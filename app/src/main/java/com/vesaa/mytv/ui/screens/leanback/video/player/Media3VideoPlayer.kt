package com.vesaa.mytv.ui.screens.leanback.video.player

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import com.vesaa.mytv.ui.utils.SP
import com.vesaa.mytv.utils.PlaybackTrace
import com.vesaa.mytv.utils.RtspSmilResolver
import com.vesaa.mytv.data.utils.Constants
import com.vesaa.mytv.utils.AppOkHttp
import com.vesaa.mytv.utils.IptvOutboundHeaderPolicy
import com.vesaa.mytv.utils.normalizeIptvRequestHeadersInput
import com.vesaa.mytv.utils.parseHttpHeaderLines
import java.net.URI
import androidx.media3.common.PlaybackException as Media3PlaybackException
import kotlin.math.max

@OptIn(UnstableApi::class)
class LeanbackMedia3VideoPlayer(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
) : LeanbackVideoPlayer(coroutineScope) {
    // IPTV 场景优化的缓冲策略（HTTP/HLS 等）
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            60_000,
            120_000,
            1_500,
            DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .setBackBuffer(0, false)
        .build()

    // UDP/RTP 组播流专用缓冲策略：裸 UDP 无重传，起播需多缓冲
    private val udpLoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(60_000, 120_000, 3_000, 5_000)
        .setPrioritizeTimeOverSizeThresholds(true)
        .setBackBuffer(0, false)
        .build()

    private var forcePreferExtensionDecoders = false
    private var hevcSoftFallbackTried = false
    private var lastPreparedUri: Uri? = null
    private var lastPreparedContentType: Int? = null

    private fun newRenderersFactory(): DefaultRenderersFactory {
        val mode = if (forcePreferExtensionDecoders) EXTENSION_RENDERER_MODE_PREFER else EXTENSION_RENDERER_MODE_ON
        return DefaultRenderersFactory(context)
            .setExtensionRendererMode(mode)
            .setEnableDecoderFallback(true)
    }

    private var videoPlayer = ExoPlayer.Builder(context, newRenderersFactory())
        .setLoadControl(loadControl)
        .build().apply { playWhenReady = true }

    /** 当前是否使用 UDP 专用播放器（更大的起播缓冲） */
    private var isUdpPlayer = false

    /** 组播锁：Android 默认丢弃 Wi-Fi 组播包，播放 UDP/RTP 时必须持有 */
    private var multicastLock: WifiManager.MulticastLock? = null

    private val contentTypeAttempts = mutableMapOf<Int, Boolean>()
    private var parseRetryJob: Job? = null
    private var noVideoFrameWatchdogJob: Job? = null
    private var imageSequenceJob: Job? = null
    private var boundSurfaceView: SurfaceView? = null
    private var boundTextureView: TextureView? = null
    private var latestZapStartElapsedMs: Long = 0L
    private var awaitingFirstReadyAfterPrepare: Boolean = false
    private var lastRenderedFpsElapsedMs: Long = 0L
    private var fpsWindowStartNs: Long = 0L
    private var fpsWindowFrameCount: Int = 0

    /** 同一次播放会话内重试容器类型时沿用（收藏夹 per-entry 头等） */
    private var activeStreamRequestHeaders: String? = null
    private var parseErrorRetryUsed = false
    private val attemptedVideoTrackFallbackKeys = mutableSetOf<String>()
    private var rtspTriedUdpFallback = false
    private var lastRtspForceTcp = true

    /** 最近一次 [prepare] 传入的频道名，用于 [PlaybackTrace]（不含 URL）。 */
    private var lastPlaybackLabel: String? = null
    /** 仍为 TCP/interleaved 时允许的同址重试次数（耗尽早于 UDP 回退） */
    private var rtspTcpPrepareRetriesRemaining = 0
    private var hlsAvcFallbackTried = false
    private var hlsImageSequenceFallbackTried = false
    private var hlsPreprobeJob: Job? = null
    private var smilResolveJob: Job? = null
    private var prepareSessionId: Int = 0
    private var hlsSuspiciousSession: Boolean = false

    /** 新会话在 [onTracksChanged] 中自动启用第一条可解码字幕轨；用户手动选字幕后置 false。 */
    private var pendingApplyDefaultFirstSubtitle: Boolean = false

    private val preferredTrackParams: TrackSelectionParameters by lazy {
        // 优先选更兼容的编解码，提升老盒子/运营商定制 ROM 的可播率：
        // - 视频：优先 AVC(H.264)，其次 HEVC(H.265)
        // - 音频：优先 AAC，其次 AC3/EAC3
        TrackSelectionParameters.Builder(context)
            .setPreferredVideoMimeTypes("video/avc", "video/hevc")
            .setPreferredAudioMimeTypes("audio/mp4a-latm", "audio/ac3", "audio/eac3")
            .build()
    }

    // ── MulticastLock 管理 ─────────────────────────────────────────

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wm.createMulticastLock("iptv_udp").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) { /* 部分有线盒子无 Wi-Fi 服务 */ }
    }

    private fun releaseMulticastLock() {
        try { multicastLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) { }
        multicastLock = null
    }

    // ── 播放器实例切换（HTTP ⇆ UDP） ──────────────────────────────

    private fun recreatePlayer(needUdp: Boolean) {
        videoPlayer.removeListener(playerListener)
        videoPlayer.removeAnalyticsListener(metadataListener)
        videoPlayer.removeAnalyticsListener(eventLogger)
        videoPlayer.setVideoFrameMetadataListener(noopVideoFrameMetadataListener)
        videoPlayer.release()

        videoPlayer = ExoPlayer.Builder(context, newRenderersFactory())
            .setLoadControl(if (needUdp) udpLoadControl else loadControl)
            .build().apply { playWhenReady = true }
        videoPlayer.addListener(playerListener)
        videoPlayer.addAnalyticsListener(metadataListener)
        videoPlayer.addAnalyticsListener(eventLogger)
        videoPlayer.setVideoFrameMetadataListener(videoFrameMetadataListener)
        boundSurfaceView?.let { videoPlayer.setVideoSurfaceView(it) }
        boundTextureView?.let { videoPlayer.setVideoTextureView(it) }
        isUdpPlayer = needUdp
    }

    private fun ensurePlayerForUdp(needUdp: Boolean) {
        if (needUdp == isUdpPlayer) return
        recreatePlayer(needUdp)
    }

    // ── DataSource 工厂 ───────────────────────────────────────────

    @OptIn(UnstableApi::class)
    private fun httpDataSourceFactory(uri: Uri, streamRequestHeaders: String?): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory().apply {
            val url = uri.toString()
            val trimmed = streamRequestHeaders?.trim().orEmpty()
            if (trimmed.isEmpty()) {
                val ua = IptvOutboundHeaderPolicy.blendUserAgentValue(SP.playbackHttpUserAgent(), url)
                setUserAgent(ua)
            } else {
                val norm = normalizeIptvRequestHeadersInput(trimmed)
                val blended = IptvOutboundHeaderPolicy.applyToNormalizedHeadersText(norm, url)
                val map = blended.parseHttpHeaderLines()
                val ua = map.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
                    ?.value?.trim()?.takeIf { it.isNotEmpty() }
                setUserAgent(
                    ua ?: IptvOutboundHeaderPolicy.blendUserAgentValue(SP.playbackHttpUserAgent(), url),
                )
                val rest = map.filterKeys { !it.equals("User-Agent", ignoreCase = true) }
                if (rest.isNotEmpty()) {
                    setDefaultRequestProperties(rest)
                }
            }
            setConnectTimeoutMs(SP.videoPlayerLoadTimeout.toInt())
            setReadTimeoutMs(SP.videoPlayerLoadTimeout.toInt())
            setKeepPostFor302Redirects(true)
            setAllowCrossProtocolRedirects(true)
        }

    private fun isUdpOrRtp(uri: Uri): Boolean =
        uri.scheme.equals("udp", ignoreCase = true) || uri.scheme.equals("rtp", ignoreCase = true)

    private fun rtspUserAgent(uri: Uri, streamRequestHeaders: String?): String {
        val url = uri.toString()
        val trimmed = streamRequestHeaders?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return IptvOutboundHeaderPolicy.blendUserAgentValue(SP.playbackHttpUserAgent(), url)
        }
        val norm = normalizeIptvRequestHeadersInput(trimmed)
        val blended = IptvOutboundHeaderPolicy.applyToNormalizedHeadersText(norm, url)
        val map = blended.parseHttpHeaderLines()
        val ua = map.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value?.trim()?.takeIf { it.isNotEmpty() }
        return ua ?: IptvOutboundHeaderPolicy.blendUserAgentValue(SP.playbackHttpUserAgent(), url)
    }

    // ── prepare ───────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    private fun prepare(
        uri: Uri,
        contentType: Int? = null,
        streamRequestHeaders: String? = null,
        markHlsSuspiciousSession: Boolean = false,
    ) {
        val headers = streamRequestHeaders ?: activeStreamRequestHeaders
        val isRtmp = uri.scheme.equals("rtmp", ignoreCase = true)
        val isUdpRtp = isUdpOrRtp(uri)
        val isRtsp = uri.scheme.equals("rtsp", ignoreCase = true)
        lastPreparedUri = uri
        lastPreparedContentType = contentType
        if ((contentType ?: Util.inferContentType(uri)) == C.CONTENT_TYPE_HLS) {
            hlsSuspiciousSession = markHlsSuspiciousSession
        } else {
            hlsSuspiciousSession = false
        }

        // UDP/RTP 组播：切换到专用播放器 + 获取 MulticastLock
        ensurePlayerForUdp(isUdpRtp)
        if (isUdpRtp) acquireMulticastLock() else releaseMulticastLock()

        // rtp:// → udp://（端口/地址保持不变）
        val effectiveUri = if (uri.scheme.equals("rtp", ignoreCase = true)) {
            uri.buildUpon().scheme("udp").build()
        } else {
            uri
        }

        val mediaItem = MediaItem.fromUri(effectiveUri)

        val mediaSource = if (isRtmp) {
            ProgressiveMediaSource.Factory(RtmpDataSource.Factory()).createMediaSource(mediaItem)
        } else if (isUdpRtp) {
            // 自定义 MulticastUdpDataSource（2MB 接收缓冲区）
            val udpFactory = DataSource.Factory { MulticastUdpDataSource() }
            ProgressiveMediaSource.Factory(udpFactory).createMediaSource(mediaItem)
        } else when (val type = contentType ?: Util.inferContentType(effectiveUri)) {
            C.CONTENT_TYPE_HLS -> {
                val dsf = DefaultDataSource.Factory(context, httpDataSourceFactory(uri, headers))
                HlsMediaSource.Factory(dsf)
                    // 兼容“master 标注仅音频，但分片实际含视频”的非规范 HLS 源。
                    .setAllowChunklessPreparation(false)
                    .createMediaSource(mediaItem)
            }

            C.CONTENT_TYPE_DASH -> {
                val dsf = DefaultDataSource.Factory(context, httpDataSourceFactory(uri, headers))
                DashMediaSource.Factory(dsf).createMediaSource(mediaItem)
            }

            C.CONTENT_TYPE_SS -> {
                val dsf = DefaultDataSource.Factory(context, httpDataSourceFactory(uri, headers))
                SsMediaSource.Factory(dsf).createMediaSource(mediaItem)
            }

            C.CONTENT_TYPE_RTSP -> {
                // Media3 内部完成 SDP/SETUP；App 侧通过更长 timeout、TCP-first、UA 与重试提升运营商源可播率。
                val forceTcp = if (isRtsp) lastRtspForceTcp else true
                val timeoutMs = SP.videoRtspRtpSilenceTimeoutMs.coerceIn(3_000L, 120_000L)
                RtspMediaSource.Factory()
                    .setForceUseRtpTcp(forceTcp)
                    .setTimeoutMs(timeoutMs)
                    .setUserAgent(rtspUserAgent(effectiveUri, headers))
                    .setDebugLoggingEnabled(SP.debugAppLog)
                    .createMediaSource(mediaItem)
            }

            C.CONTENT_TYPE_OTHER -> {
                val dsf = DefaultDataSource.Factory(context, httpDataSourceFactory(uri, headers))
                ProgressiveMediaSource.Factory(dsf).createMediaSource(mediaItem)
            }

            else -> {
                triggerError(
                    PlaybackException.UNSUPPORTED_TYPE.copy(
                        errorCodeName = "${PlaybackException.UNSUPPORTED_TYPE.message}_$type"
                    )
                )
                null
            }
        }

        pendingApplyDefaultFirstSubtitle = false
        if (mediaSource != null) {
            val ct = contentType ?: Util.inferContentType(effectiveUri)
            val prepareDetail = buildString {
                append("ct=$ct rtspTcp=$lastRtspForceTcp rtspRetryLeft=$rtspTcpPrepareRetriesRemaining")
                if (isRtsp) {
                    append(" rtspTimeoutMs=").append(
                        SP.videoRtspRtpSilenceTimeoutMs.coerceIn(3_000L, 120_000L),
                    )
                    if (effectiveUri.path?.endsWith(".smil", ignoreCase = true) == true) {
                        append(" rtspUriTail=.smil")
                    }
                }
            }
            PlaybackTrace.i(
                effectiveUri,
                "prepare",
                prepareDetail,
                lastPlaybackLabel,
            )
            latestZapStartElapsedMs = SystemClock.elapsedRealtime()
            awaitingFirstReadyAfterPrepare = true
            lastRenderedFpsElapsedMs = 0L
            attemptedVideoTrackFallbackKeys.clear()
            metadata = metadata.copy(
                zapLatencyMs = null,
                videoRenderedFps = 0f,
                audioOnlyModeHint = false,
                imageSequenceModeHint = false,
                imageSequenceImageUrl = "",
            )
            triggerMetadata(metadata)
            contentTypeAttempts[contentType ?: Util.inferContentType(effectiveUri)] = true
            // 每次切台先按偏好设置选轨（优先 AVC/AAC），清掉历史手动覆盖；字幕轨先禁用，
            // 待 [onTracksChanged] 后自动选中第一条可解码字幕（见 [applyDefaultFirstSubtitleIfPending]）。
            pendingApplyDefaultFirstSubtitle = true
            videoPlayer.trackSelectionParameters = preferredTrackParams.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            videoPlayer.setMediaSource(mediaSource)
            videoPlayer.prepare()
            triggerPrepared()
        }
    }

    private suspend fun fetchText(url: String, headers: String?): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url)
        val norm = normalizeIptvRequestHeadersInput(headers.orEmpty())
        val blended = IptvOutboundHeaderPolicy.applyToNormalizedHeadersText(norm, url)
        blended.parseHttpHeaderLines().forEach { (k, v) ->
            if (!k.equals("User-Agent", ignoreCase = true) && k.isNotBlank()) builder.addHeader(k, v)
        }
        val req = builder.build()
        AppOkHttp.client().newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
    }

    private data class HlsMasterProbe(
        val hasAnyVariant: Boolean,
        val hasAvc: Boolean,
        val hasHevc: Boolean,
        val avcVariantUrl: String?,
        val firstVariantUrl: String?,
        val hasMissingCodecsDecl: Boolean,
        val hasAudioOnlyCodecsDecl: Boolean,
        val suspiciousByDeclaration: Boolean,
    )

    private data class ImageSequenceFrame(
        val url: String,
        val durationMs: Long,
    )

    private data class HlsImageSequenceProbe(
        val audioUrl: String?,
        val frames: List<ImageSequenceFrame>,
    )

    private fun probeHlsMaster(masterUrl: String, content: String): HlsMasterProbe {
        val lines = content.lines()
        var hasVariant = false
        var hasAvc = false
        var hasHevc = false
        var avcUrl: String? = null
        var firstVariantUrl: String? = null
        var hasMissingCodecsDecl = false
        var hasAudioOnlyCodecsDecl = false

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (!line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) continue
            hasVariant = true
            val nextUri = lines.getOrNull(i + 1)?.trim().orEmpty()
            if (firstVariantUrl == null && nextUri.isNotBlank() && !nextUri.startsWith("#")) {
                firstVariantUrl = runCatching { URI(masterUrl).resolve(nextUri).toString() }
                    .getOrElse { nextUri }
            }
            val codecs = Regex("""CODECS="([^"]+)"""", RegexOption.IGNORE_CASE)
                .find(line)?.groupValues?.getOrNull(1).orEmpty().lowercase()
            if (codecs.isBlank()) {
                hasMissingCodecsDecl = true
            } else {
                val tokens = codecs.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val audioOnlyDeclared = tokens.isNotEmpty() &&
                    tokens.all { token ->
                        token.startsWith("mp4a") ||
                            token.startsWith("ac-3") ||
                            token.startsWith("ec-3") ||
                            token.startsWith("opus")
                    }
                if (audioOnlyDeclared) hasAudioOnlyCodecsDecl = true
            }
            val isAvc = codecs.contains("avc1")
            val isHevc = codecs.contains("hvc1") || codecs.contains("hev1")
            if (isAvc) hasAvc = true
            if (isHevc) hasHevc = true
            if (isAvc && avcUrl == null && nextUri.isNotBlank() && !nextUri.startsWith("#")) {
                avcUrl = runCatching { URI(masterUrl).resolve(nextUri).toString() }
                    .getOrElse { nextUri }
            }
        }

        return HlsMasterProbe(
            hasAnyVariant = hasVariant,
            hasAvc = hasAvc,
            hasHevc = hasHevc,
            avcVariantUrl = avcUrl,
            firstVariantUrl = firstVariantUrl,
            hasMissingCodecsDecl = hasMissingCodecsDecl,
            hasAudioOnlyCodecsDecl = hasAudioOnlyCodecsDecl,
            suspiciousByDeclaration = hasMissingCodecsDecl || hasAudioOnlyCodecsDecl,
        )
    }

    private fun extractFirstAudioUri(masterUrl: String, content: String): String? {
        val line = content.lines()
            .firstOrNull { it.contains("#EXT-X-MEDIA", ignoreCase = true) && it.contains("TYPE=AUDIO", ignoreCase = true) }
            ?.trim()
            .orEmpty()
        if (line.isBlank()) return null
        val uri = Regex("""URI="([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(line)?.groupValues?.getOrNull(1).orEmpty()
        if (uri.isBlank()) return null
        return runCatching { URI(masterUrl).resolve(uri).toString() }.getOrElse { uri }
    }

    private fun extractFirstVariantUri(masterUrl: String, content: String): String? {
        val lines = content.lines()
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (!line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) continue
            val next = lines.getOrNull(i + 1)?.trim().orEmpty()
            if (next.isBlank() || next.startsWith("#")) continue
            return runCatching { URI(masterUrl).resolve(next).toString() }.getOrElse { next }
        }
        return null
    }

    private fun probeImageSequenceVariant(variantUrl: String, content: String): List<ImageSequenceFrame> {
        val lines = content.lines()
        val out = mutableListOf<ImageSequenceFrame>()
        var pendingDurationMs = 4000L
        for (lineRaw in lines) {
            val line = lineRaw.trim()
            if (line.isBlank()) continue
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                val sec = line.removePrefix("#EXTINF:")
                    .substringBefore(",")
                    .trim()
                    .toDoubleOrNull()
                pendingDurationMs = ((sec ?: 4.0) * 1000).toLong().coerceAtLeast(500L)
                continue
            }
            if (line.startsWith("#")) continue
            if (!line.lowercase().endsWith(".jpeg") && !line.lowercase().endsWith(".jpg")) continue
            val resolved = runCatching { URI(variantUrl).resolve(line).toString() }.getOrElse { line }
            out += ImageSequenceFrame(resolved, pendingDurationMs)
        }
        return out
    }

    private suspend fun probeHlsImageSequence(masterUrl: String, masterContent: String, headers: String?): HlsImageSequenceProbe? {
        val variant = extractFirstVariantUri(masterUrl, masterContent) ?: return null
        val variantText = fetchText(variant, headers)
        val frames = probeImageSequenceVariant(variant, variantText)
        if (frames.isEmpty()) return null
        return HlsImageSequenceProbe(
            audioUrl = extractFirstAudioUri(masterUrl, masterContent),
            frames = frames,
        )
    }

    private fun stopImageSequenceMode(clearMetadata: Boolean = true) {
        imageSequenceJob?.cancel()
        imageSequenceJob = null
        if (clearMetadata && (metadata.imageSequenceModeHint || metadata.imageSequenceImageUrl.isNotBlank())) {
            metadata = metadata.copy(
                imageSequenceModeHint = false,
                imageSequenceImageUrl = "",
            )
            triggerMetadata(metadata)
        }
    }

    private fun startImageSequenceMode(frames: List<ImageSequenceFrame>) {
        if (frames.isEmpty()) return
        imageSequenceJob?.cancel()
        imageSequenceJob = coroutineScope.launch {
            var idx = 0
            while (true) {
                val frame = frames[idx % frames.size]
                metadata = metadata.copy(
                    imageSequenceModeHint = true,
                    imageSequenceImageUrl = frame.url,
                    audioOnlyModeHint = false,
                )
                triggerMetadata(metadata)
                delay(frame.durationMs.coerceAtLeast(500L))
                idx++
            }
        }
    }

    // ── Player 事件监听 ───────────────────────────────────────────

    private fun applyDefaultFirstSubtitleIfPending() {
        if (!pendingApplyDefaultFirstSubtitle) return
        val groups = videoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        if (groups.isEmpty()) return
        for (group in groups) {
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                videoPlayer.trackSelectionParameters = videoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(i)))
                    .build()
                pendingApplyDefaultFirstSubtitle = false
                triggerTrackSelectionChanged()
                return
            }
        }
        pendingApplyDefaultFirstSubtitle = false
    }

    private val playerListener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            applyDefaultFirstSubtitleIfPending()
        }

        override fun onCues(cueGroup: CueGroup) {
            android.util.Log.d("MyTVSub", "onCues(CueGroup) size=${cueGroup.cues.size}, text=${cueGroup.cues.firstOrNull()?.text}")
            triggerSubtitle(cueGroup.cues)
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onCues(cues: MutableList<androidx.media3.common.text.Cue>) {
            android.util.Log.d("MyTVSub", "onCues(List) size=${cues.size}, text=${cues.firstOrNull()?.text}")
            triggerSubtitle(cues)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            triggerResolution(videoSize.width, videoSize.height)
        }

        override fun onPlayerError(ex: Media3PlaybackException) {
            // 运营商 RTSP 常见“UDP 不通、TCP 可播”场景：
            // 先按 TCP 拉流；若失败且还未尝试 UDP，则自动回退 UDP 再试一次。
            val curUri = videoPlayer.currentMediaItem?.localConfiguration?.uri
            if (curUri?.scheme.equals("rtsp", ignoreCase = true) &&
                lastRtspForceTcp &&
                rtspTcpPrepareRetriesRemaining > 0
            ) {
                val rtspUri = curUri ?: return
                rtspTcpPrepareRetriesRemaining--
                val delayMs = SP.videoRtspPrepareRetryDelayMs.coerceIn(200L, 10_000L)
                PlaybackTrace.i(
                    rtspUri,
                    "rtsp_tcp_prepare_retry",
                    "left=$rtspTcpPrepareRetriesRemaining code=${ex.errorCode} ${ex.errorCodeName}",
                    lastPlaybackLabel,
                )
                parseRetryJob?.cancel()
                parseRetryJob = coroutineScope.launch {
                    delay(delayMs)
                    prepare(rtspUri, C.CONTENT_TYPE_RTSP, streamRequestHeaders = null)
                }
                return
            }
            if (curUri?.scheme.equals("rtsp", ignoreCase = true) && lastRtspForceTcp && !rtspTriedUdpFallback) {
                PlaybackTrace.i(
                    curUri,
                    "rtsp_fallback_udp",
                    "after_tcp_exhausted code=${ex.errorCode}",
                    lastPlaybackLabel,
                )
                rtspTriedUdpFallback = true
                lastRtspForceTcp = false
                rtspTcpPrepareRetriesRemaining = 0
                curUri?.let { prepare(it, C.CONTENT_TYPE_RTSP, streamRequestHeaders = null) }
                return
            }

            // 某些源会短时返回损坏分片/清单，先做一次短延迟原线路重连，避免立刻误切线路。
            if ((ex.errorCode == 3001 || ex.errorCode == 3002) && !parseErrorRetryUsed) {
                parseErrorRetryUsed = true
                val uri = curUri
                if (uri != null) {
                    parseRetryJob?.cancel()
                    parseRetryJob = coroutineScope.launch {
                        delay(800)
                        prepare(uri, streamRequestHeaders = null)
                    }
                    return
                }
            }
            val hlsUri = curUri
            if (hlsUri != null && Util.inferContentType(hlsUri) == C.CONTENT_TYPE_HLS && !hlsAvcFallbackTried) {
                hlsAvcFallbackTried = true
                coroutineScope.launch {
                    val probe = runCatching {
                        val txt = fetchText(hlsUri.toString(), activeStreamRequestHeaders)
                        txt to probeHlsMaster(hlsUri.toString(), txt)
                    }.getOrNull()
                    if (probe != null) {
                        val (masterText, hlsProbe) = probe
                        if (hlsProbe.avcVariantUrl != null) {
                            stopImageSequenceMode()
                            prepare(Uri.parse(hlsProbe.avcVariantUrl), C.CONTENT_TYPE_HLS, activeStreamRequestHeaders)
                            return@launch
                        }
                        if (!hlsImageSequenceFallbackTried) {
                            hlsImageSequenceFallbackTried = true
                            val imageProbe = runCatching {
                                probeHlsImageSequence(hlsUri.toString(), masterText, activeStreamRequestHeaders)
                            }.getOrNull()
                            if (imageProbe != null && imageProbe.frames.isNotEmpty()) {
                                triggerError(null)
                                triggerBuffering(false)
                                startImageSequenceMode(imageProbe.frames)
                                if (!imageProbe.audioUrl.isNullOrBlank()) {
                                    prepare(Uri.parse(imageProbe.audioUrl), C.CONTENT_TYPE_HLS, activeStreamRequestHeaders)
                                } else {
                                    videoPlayer.stop()
                                    videoPlayer.clearMediaItems()
                                    triggerReady()
                                }
                                return@launch
                            }
                        }
                        if (hlsProbe.hasAnyVariant && hlsProbe.hasHevc && !hlsProbe.hasAvc) {
                            stopImageSequenceMode()
                            triggerError(
                                PlaybackException("HLS_HEVC_ONLY_OR_UNSUPPORTED", 10004)
                            )
                            return@launch
                        }
                        if (!hlsProbe.hasAnyVariant) {
                            stopImageSequenceMode()
                            triggerError(
                                PlaybackException("HLS_MASTER_INVALID_OR_SINGLE_AUDIO", 10005)
                            )
                            return@launch
                        }
                    }
                    stopImageSequenceMode()
                    triggerError(
                        PlaybackException(ex.errorCodeName, ex.errorCode)
                    )
                }
                return
            }
            // 如果是直播加载位置错误，尝试重新播放
            if (ex.errorCode == Media3PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                videoPlayer.seekToDefaultPosition()
                videoPlayer.prepare()
            }
            // 当解析容器不支持时，尝试使用其他解析容器
            else if (ex.errorCode == Media3PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED) {
                val uri = videoPlayer.currentMediaItem?.localConfiguration?.uri
                if (uri != null) {
                    val tryOrder = listOf(
                        C.CONTENT_TYPE_HLS,
                        C.CONTENT_TYPE_DASH,
                        C.CONTENT_TYPE_SS,
                        C.CONTENT_TYPE_OTHER,
                    )
                    val next = tryOrder.firstOrNull { contentTypeAttempts[it] != true }
                    if (next != null) {
                        prepare(uri, next, streamRequestHeaders = null)
                    } else {
                        triggerError(PlaybackException.UNSUPPORTED_TYPE)
                    }
                }
            } else {
                triggerError(
                    PlaybackException(ex.errorCodeName, ex.errorCode)
                )
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_BUFFERING) {
                triggerError(null)
                triggerBuffering(true)
            } else if (playbackState == Player.STATE_READY) {
                if (awaitingFirstReadyAfterPrepare && latestZapStartElapsedMs > 0L) {
                    val elapsed = (SystemClock.elapsedRealtime() - latestZapStartElapsedMs).coerceAtLeast(0L)
                    metadata = metadata.copy(zapLatencyMs = elapsed)
                    triggerMetadata(metadata)
                    awaitingFirstReadyAfterPrepare = false
                }
                triggerReady()
                startNoVideoFrameWatchdog()
            } else {
                stopNoVideoFrameWatchdog()
            }

            if (playbackState != Player.STATE_BUFFERING) {
                triggerBuffering(false)
            }
        }
    }

    /**
     * 多视频轨场景（例如 AVC+HEVC）下，部分设备会默认选中无法解码的视频轨，表现为“有声音无画面”。
     * READY 后若持续无视频帧渲染，则自动切到同组下一个可用视频轨；每个候选仅尝试一次。
     */
    private fun startNoVideoFrameWatchdog() {
        noVideoFrameWatchdogJob?.cancel()
        noVideoFrameWatchdogJob = coroutineScope.launch {
            val isRtspSession = lastPreparedUri?.scheme.equals("rtsp", ignoreCase = true)
            val startupDelayMs = when {
                hlsSuspiciousSession -> 1200L
                isRtspSession -> Constants.VIDEO_RTSP_NO_VIDEO_WATCHDOG_STARTUP_MS
                else -> 4000L
            }
            val noFrameThresholdMs = when {
                hlsSuspiciousSession -> 2000L
                isRtspSession -> Constants.VIDEO_RTSP_NO_VIDEO_WATCHDOG_THRESHOLD_MS
                else -> 4500L
            }
            delay(startupDelayMs)
            while (true) {
                delay(800)
                val sinceLastFrameMs = SystemClock.elapsedRealtime() - lastRenderedFpsElapsedMs
                val noVideoFrameForLongTime =
                    lastRenderedFpsElapsedMs <= 0L || sinceLastFrameMs > noFrameThresholdMs
                if (!videoPlayer.isPlaying || !noVideoFrameForLongTime) continue
                if (metadata.imageSequenceModeHint) continue
                if (tryHevcSoftDecodeFallback()) {
                    continue
                }
                if (isRtspSession) {
                    PlaybackTrace.i(
                        lastPreparedUri,
                        "watchdog_no_video",
                        "thrMs=$noFrameThresholdMs",
                        lastPlaybackLabel,
                    )
                }
                if (!metadata.audioOnlyModeHint) {
                    metadata = metadata.copy(audioOnlyModeHint = true)
                    triggerMetadata(metadata)
                }
                if (tryFallbackToNextVideoTrack()) {
                    lastRenderedFpsElapsedMs = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    private fun tryHevcSoftDecodeFallback(): Boolean {
        if (hevcSoftFallbackTried || forcePreferExtensionDecoders) return false
        val mime = metadata.videoMimeType.lowercase()
        val codecs = metadata.videoCodecs.lowercase()
        val decoder = metadata.videoDecoder.lowercase()
        val isHevc = mime.contains("hevc") || codecs.contains("hev1") || codecs.contains("hvc1")
        val alreadyFfmpeg = decoder.contains("ffmpeg")
        if (!isHevc || alreadyFfmpeg) return false
        val uri = lastPreparedUri ?: return false

        hevcSoftFallbackTried = true
        forcePreferExtensionDecoders = true
        val needUdp = isUdpOrRtp(uri)
        recreatePlayer(needUdp)
        prepare(uri, lastPreparedContentType, activeStreamRequestHeaders)
        return true
    }

    private fun stopNoVideoFrameWatchdog() {
        noVideoFrameWatchdogJob?.cancel()
        noVideoFrameWatchdogJob = null
        if (metadata.audioOnlyModeHint) {
            metadata = metadata.copy(audioOnlyModeHint = false)
            triggerMetadata(metadata)
        }
        if (metadata.imageSequenceModeHint) {
            stopImageSequenceMode()
        }
    }

    private fun tryFallbackToNextVideoTrack(): Boolean {
        val videoGroups = videoPlayer.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        for (group in videoGroups) {
            val supported = buildList {
                for (i in 0 until group.length) {
                    if (group.isTrackSupported(i)) add(i)
                }
            }
            if (supported.size <= 1) continue

            val selected = supported.firstOrNull { idx -> group.isTrackSelected(idx) }
            val ranked = supported
                .filter { it != selected }
                .sortedBy { idx -> videoTrackScore(group.mediaTrackGroup.getFormat(idx)) }

            for (idx in ranked) {
                val key = "${group.mediaTrackGroup.id ?: "unknown"}#$idx"
                if (!attemptedVideoTrackFallbackKeys.add(key)) continue
                videoPlayer.trackSelectionParameters = videoPlayer.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(idx)))
                    .build()
                return true
            }
        }
        return false
    }

    private fun videoTrackScore(format: Format): Int {
        // 分数越低越优先：尽量选 AVC + 低分辨率/低码率，提升兼容性。
        val mime = format.sampleMimeType.orEmpty().lowercase()
        val codecs = format.codecs.orEmpty().lowercase()
        val codecScore = when {
            mime.contains("avc") || codecs.startsWith("avc1") -> 0
            mime.contains("hevc") || codecs.startsWith("hvc1") || codecs.startsWith("hev1") -> 10
            mime.contains("dolby") || codecs.startsWith("dvhe") || codecs.startsWith("dvh1") -> 100
            else -> 50
        }
        val w = if (format.width > 0) format.width else 0
        val h = if (format.height > 0) format.height else 0
        val resScore = (w * h) / 10_000 // 1080p≈207, 4K≈829
        val brScore = (if (format.bitrate > 0) format.bitrate else 0) / 1_000_000
        return codecScore + resScore + brScore
    }

    override fun getTrackOptions(type: TrackType): List<TrackOption> {
        val targetType = when (type) {
            TrackType.Audio -> C.TRACK_TYPE_AUDIO
            TrackType.Video -> C.TRACK_TYPE_VIDEO
            TrackType.Subtitle -> C.TRACK_TYPE_TEXT
        }
        return videoPlayer.currentTracks.groups
            .filter { it.type == targetType }
            .flatMap { group ->
                buildList {
                    for (i in 0 until group.length) {
                        add(
                            TrackOption(
                                id = "${group.mediaTrackGroup.id ?: "group"}#$i",
                                label = trackLabel(type, group, i),
                                selected = group.isTrackSelected(i),
                            ),
                        )
                    }
                }
            }
    }

    override fun selectTrack(type: TrackType, trackId: String) {
        if (type == TrackType.Subtitle) {
            pendingApplyDefaultFirstSubtitle = false
        }
        val targetType = when (type) {
            TrackType.Audio -> C.TRACK_TYPE_AUDIO
            TrackType.Video -> C.TRACK_TYPE_VIDEO
            TrackType.Subtitle -> C.TRACK_TYPE_TEXT
        }
        val groups = videoPlayer.currentTracks.groups.filter { it.type == targetType }
        for (group in groups) {
            for (i in 0 until group.length) {
                val id = "${group.mediaTrackGroup.id ?: "group"}#$i"
                if (id != trackId) continue
                if (type == TrackType.Subtitle && group.isTrackSelected(i)) {
                    triggerSubtitle(emptyList())
                    videoPlayer.trackSelectionParameters = videoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .build()
                } else {
                    videoPlayer.trackSelectionParameters = videoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(targetType, false)
                        .clearOverridesOfType(targetType)
                        .addOverride(TrackSelectionOverride(group.mediaTrackGroup, listOf(i)))
                        .build()
                }
                return
            }
        }
    }

    private fun trackLabel(type: TrackType, group: Tracks.Group, index: Int): String {
        val f = group.mediaTrackGroup.getFormat(index)
        return when (type) {
            TrackType.Audio -> {
                val lang = f.language?.takeIf { it.isNotBlank() && it != "und" } ?: "音轨${index + 1}"
                val codec = f.sampleMimeType?.removePrefix("audio/").orEmpty().ifBlank { "unknown" }
                val ch = if (f.channelCount > 0) "·${f.channelCount}ch" else ""
                "$lang · $codec$ch"
            }

            TrackType.Video -> {
                val res = if (f.width > 0 && f.height > 0) "${f.width}x${f.height}" else "视频轨${index + 1}"
                val codec = f.sampleMimeType?.removePrefix("video/").orEmpty().ifBlank { "unknown" }
                "$res · $codec"
            }
            TrackType.Subtitle -> {
                val lang = f.language?.takeIf { it.isNotBlank() && it != "und" } ?: "字幕${index + 1}"
                val codec = f.sampleMimeType.orEmpty().ifBlank { "text" }
                "$lang · $codec"
            }
        }
    }

    // ── 元数据监听 ────────────────────────────────────────────────

    private val metadataListener = @UnstableApi object : AnalyticsListener {
        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(
                videoMimeType = format.sampleMimeType ?: "",
                videoCodecs = format.codecs ?: "",
                videoWidth = format.width,
                videoHeight = format.height,
                videoColor = format.colorInfo?.toLogString() ?: "",
                // TODO 帧率、比特率目前是从tag中获取，有的返回空，后续需要实时计算
                videoFrameRate = format.frameRate,
                videoBitrate = format.bitrate,
            )
            triggerMetadata(metadata)
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            metadata = metadata.copy(videoDecoder = decoderName)
            triggerMetadata(metadata)
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            metadata = metadata.copy(
                audioMimeType = format.sampleMimeType ?: "",
                audioCodecs = format.codecs ?: "",
                audioChannels = format.channelCount,
                audioSampleRate = format.sampleRate,
            )
            triggerMetadata(metadata)
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            metadata = metadata.copy(audioDecoder = decoderName)
            triggerMetadata(metadata)
        }
    }

    // ── FPS 统计 ──────────────────────────────────────────────────

    private val videoFrameMetadataListener = VideoFrameMetadataListener { presentationTimeUs, releaseTimeNs, format, mediaFormat ->
        val nowMs = SystemClock.elapsedRealtime()
        val lastMs = lastRenderedFpsElapsedMs
        lastRenderedFpsElapsedMs = nowMs
        if (metadata.audioOnlyModeHint) {
            metadata = metadata.copy(audioOnlyModeHint = false)
            triggerMetadata(metadata)
        }

        if (fpsWindowStartNs <= 0L) {
            fpsWindowStartNs = releaseTimeNs
            fpsWindowFrameCount = 0
        }
        fpsWindowFrameCount += 1
        val dtNs = releaseTimeNs - fpsWindowStartNs
        if (dtNs >= 500_000_000L && fpsWindowFrameCount > 0) {
            val instantFps = fpsWindowFrameCount * 1_000_000_000f / dtNs.toFloat()
            val clamped = instantFps.coerceIn(1f, 240f)
            val smoothed = if (metadata.videoRenderedFps > 0.1f) {
                metadata.videoRenderedFps * 0.7f + clamped * 0.3f
            } else {
                clamped
            }
            metadata = metadata.copy(videoRenderedFps = smoothed)
            triggerMetadata(metadata)
            fpsWindowStartNs = releaseTimeNs
            fpsWindowFrameCount = 0
        }

        if (lastMs > 0L && nowMs - lastMs > 2500L && metadata.videoRenderedFps > 0f) {
            metadata = metadata.copy(videoRenderedFps = 0f)
            triggerMetadata(metadata)
        }
    }

    private val noopVideoFrameMetadataListener = VideoFrameMetadataListener { _, _, _, _ -> }

    private val eventLogger = EventLogger()

    // ── 生命周期 ──────────────────────────────────────────────────

    override fun initialize() {
        super.initialize()
        videoPlayer.addListener(playerListener)
        videoPlayer.addAnalyticsListener(metadataListener)
        videoPlayer.addAnalyticsListener(eventLogger)
        videoPlayer.setVideoFrameMetadataListener(videoFrameMetadataListener)
    }

    override fun release() {
        hlsPreprobeJob?.cancel()
        smilResolveJob?.cancel()
        parseRetryJob?.cancel()
        stopNoVideoFrameWatchdog()
        stopImageSequenceMode()
        releaseMulticastLock()
        videoPlayer.removeListener(playerListener)
        videoPlayer.removeAnalyticsListener(metadataListener)
        videoPlayer.removeAnalyticsListener(eventLogger)
        videoPlayer.setVideoFrameMetadataListener(noopVideoFrameMetadataListener)
        videoPlayer.release()
        super.release()
    }

    @UnstableApi
    override fun prepare(url: String, streamRequestHeaders: String?, playbackLabel: String?) {
        lastPlaybackLabel = playbackLabel?.trim()?.take(80)?.takeIf { it.isNotEmpty() }
        prepareSessionId += 1
        val sessionId = prepareSessionId
        hlsPreprobeJob?.cancel()
        smilResolveJob?.cancel()
        parseRetryJob?.cancel()
        parseRetryJob = null
        stopImageSequenceMode()
        forcePreferExtensionDecoders = false
        hevcSoftFallbackTried = false
        lastPreparedUri = null
        lastPreparedContentType = null
        parseErrorRetryUsed = false
        rtspTriedUdpFallback = false
        lastRtspForceTcp = SP.videoRtspForceTcp
        hlsAvcFallbackTried = false
        hlsImageSequenceFallbackTried = false
        hlsSuspiciousSession = false
        activeStreamRequestHeaders = streamRequestHeaders
        contentTypeAttempts.clear()
        val parsed = Uri.parse(url)
        when {
            parsed.scheme.equals("rtsp", ignoreCase = true) && SP.videoRtspForceTcp ->
                rtspTcpPrepareRetriesRemaining =
                    SP.videoRtspTcpPrepareRetryCount.coerceIn(0, 10)

            else -> rtspTcpPrepareRetriesRemaining = 0
        }
        val isRtspSmilPlaylist =
            parsed.scheme.equals("rtsp", ignoreCase = true) &&
                (parsed.path?.endsWith(".smil", ignoreCase = true) == true)
        if (isRtspSmilPlaylist) {
            smilResolveJob = coroutineScope.launch {
                val ua = rtspUserAgent(parsed, streamRequestHeaders)
                PlaybackTrace.i(
                    parsed,
                    "smil_resolve_start",
                    "ua_len=${ua.length}",
                    lastPlaybackLabel,
                )
                val result = RtspSmilResolver.resolve(parsed, ua, lastPlaybackLabel)
                if (sessionId != prepareSessionId) return@launch
                when (result) {
                    is RtspSmilResolver.SmilResolveResult.Ok -> {
                        PlaybackTrace.i(
                            result.streamUri,
                            "smil_resolve_ok",
                            "stream=${result.streamUri}",
                            lastPlaybackLabel,
                        )
                        prepare(result.streamUri, C.CONTENT_TYPE_RTSP, streamRequestHeaders)
                    }
                    is RtspSmilResolver.SmilResolveResult.Fail -> {
                        PlaybackTrace.i(
                            parsed,
                            "smil_resolve_fail",
                            result.reason.take(120),
                            lastPlaybackLabel,
                        )
                        triggerError(PlaybackException("SMIL_RESOLVE_FAILED", 10007))
                    }
                }
            }
            return
        }
        val inferredType = Util.inferContentType(parsed)
        val shouldPreprobeHls = inferredType == C.CONTENT_TYPE_HLS &&
            (parsed.scheme.equals("http", ignoreCase = true) || parsed.scheme.equals("https", ignoreCase = true))
        if (!shouldPreprobeHls) {
            prepare(parsed, null, streamRequestHeaders)
            return
        }
        hlsPreprobeJob = coroutineScope.launch {
            val decision = withTimeoutOrNull(1200L) {
                runCatching {
                    val masterText = fetchText(parsed.toString(), streamRequestHeaders)
                    val probe = probeHlsMaster(parsed.toString(), masterText)
                    if (probe.hasAnyVariant && probe.suspiciousByDeclaration) {
                        val target = probe.avcVariantUrl ?: probe.firstVariantUrl
                        target?.let { Uri.parse(it) }?.let { it to true }
                    } else {
                        null
                    }
                }.getOrNull()
            }
            if (sessionId != prepareSessionId) return@launch
            val target = decision?.first ?: parsed
            val markSuspicious = decision?.second == true
            prepare(target, C.CONTENT_TYPE_HLS, streamRequestHeaders, markSuspicious)
        }
    }

    override fun play() {
        videoPlayer.play()
    }

    override fun pause() {
        videoPlayer.pause()
    }

    override fun setMuted(muted: Boolean) {
        videoPlayer.volume = if (muted) 0f else 1f
    }

    override fun onDeactivate() {
        hlsPreprobeJob?.cancel()
        smilResolveJob?.cancel()
        parseRetryJob?.cancel()
        parseRetryJob = null
        stopNoVideoFrameWatchdog()
        stopImageSequenceMode()
        forcePreferExtensionDecoders = false
        hevcSoftFallbackTried = false
        lastPlaybackLabel = null
        lastPreparedUri = null
        lastPreparedContentType = null
        hlsSuspiciousSession = false
        pendingApplyDefaultFirstSubtitle = false
        releaseMulticastLock()
        videoPlayer.stop()
        videoPlayer.clearMediaItems()
        triggerBuffering(false)
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        // 分屏切换（SurfaceView <-> TextureView）时，先清空当前输出面，
        // 避免部分电视机型复用旧 surface 导致首屏绿屏。
        videoPlayer.clearVideoSurface()
        if (boundTextureView != null) {
            videoPlayer.clearVideoTextureView(boundTextureView)
            boundTextureView = null
        }
        if (boundSurfaceView === surfaceView) return
        boundSurfaceView = surfaceView
        videoPlayer.setVideoSurfaceView(surfaceView)
    }

    override fun setVideoTextureView(textureView: TextureView) {
        // 分屏切换（SurfaceView <-> TextureView）时，先清空当前输出面，
        // 避免部分电视机型复用旧 surface 导致首屏绿屏。
        videoPlayer.clearVideoSurface()
        if (boundSurfaceView != null) {
            videoPlayer.clearVideoSurfaceView(boundSurfaceView)
            boundSurfaceView = null
        }
        if (boundTextureView === textureView) return
        boundTextureView = textureView
        videoPlayer.setVideoTextureView(textureView)
    }

    override fun seekTo(positionMs: Long) {
        videoPlayer.seekTo(positionMs)
    }

    override fun seekToDefaultPosition() {
        videoPlayer.seekToDefaultPosition()
    }

    override fun seekBack(offsetMs: Long) {
        val duration = videoPlayer.duration
        if (duration > 0 && duration != C.TIME_UNSET) {
            videoPlayer.seekTo(max(0L, duration - offsetMs))
        } else {
            val current = videoPlayer.currentPosition
            videoPlayer.seekTo(max(0L, current - offsetMs))
        }
    }
}