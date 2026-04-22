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
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.vesaa.mytv.ui.utils.SP
import com.vesaa.mytv.utils.IptvOutboundHeaderPolicy
import com.vesaa.mytv.utils.normalizeIptvRequestHeadersInput
import com.vesaa.mytv.utils.parseHttpHeaderLines
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

    private val renderersFactory =
        DefaultRenderersFactory(context).setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)

    private var videoPlayer = ExoPlayer.Builder(context, renderersFactory)
        .setLoadControl(loadControl)
        .build().apply { playWhenReady = true }

    /** 当前是否使用 UDP 专用播放器（更大的起播缓冲） */
    private var isUdpPlayer = false

    /** 组播锁：Android 默认丢弃 Wi-Fi 组播包，播放 UDP/RTP 时必须持有 */
    private var multicastLock: WifiManager.MulticastLock? = null

    private val contentTypeAttempts = mutableMapOf<Int, Boolean>()
    private var parseRetryJob: Job? = null
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

    private fun ensurePlayerForUdp(needUdp: Boolean) {
        if (needUdp == isUdpPlayer) return
        videoPlayer.removeListener(playerListener)
        videoPlayer.removeAnalyticsListener(metadataListener)
        videoPlayer.removeAnalyticsListener(eventLogger)
        videoPlayer.setVideoFrameMetadataListener(noopVideoFrameMetadataListener)
        videoPlayer.release()

        videoPlayer = ExoPlayer.Builder(context, renderersFactory)
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

    // ── prepare ───────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    private fun prepare(uri: Uri, contentType: Int? = null, streamRequestHeaders: String? = null) {
        val headers = streamRequestHeaders ?: activeStreamRequestHeaders
        val isRtmp = uri.scheme.equals("rtmp", ignoreCase = true)
        val isUdpRtp = isUdpOrRtp(uri)

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
                HlsMediaSource.Factory(dsf).createMediaSource(mediaItem)
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
                RtspMediaSource.Factory().createMediaSource(mediaItem)
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

        if (mediaSource != null) {
            latestZapStartElapsedMs = SystemClock.elapsedRealtime()
            awaitingFirstReadyAfterPrepare = true
            lastRenderedFpsElapsedMs = 0L
            metadata = metadata.copy(zapLatencyMs = null, videoRenderedFps = 0f)
            triggerMetadata(metadata)
            contentTypeAttempts[contentType ?: Util.inferContentType(effectiveUri)] = true
            videoPlayer.setMediaSource(mediaSource)
            videoPlayer.prepare()
            triggerPrepared()
        }
    }

    // ── Player 事件监听 ───────────────────────────────────────────

    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            triggerResolution(videoSize.width, videoSize.height)
        }

        override fun onPlayerError(ex: Media3PlaybackException) {
            // 某些源会短时返回损坏分片/清单，先做一次短延迟原线路重连，避免立刻误切线路。
            if ((ex.errorCode == 3001 || ex.errorCode == 3002) && !parseErrorRetryUsed) {
                parseErrorRetryUsed = true
                val uri = videoPlayer.currentMediaItem?.localConfiguration?.uri
                if (uri != null) {
                    parseRetryJob?.cancel()
                    parseRetryJob = coroutineScope.launch {
                        delay(800)
                        prepare(uri, streamRequestHeaders = null)
                    }
                    return
                }
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
            }

            if (playbackState != Player.STATE_BUFFERING) {
                triggerBuffering(false)
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
        parseRetryJob?.cancel()
        releaseMulticastLock()
        videoPlayer.removeListener(playerListener)
        videoPlayer.removeAnalyticsListener(metadataListener)
        videoPlayer.removeAnalyticsListener(eventLogger)
        videoPlayer.setVideoFrameMetadataListener(noopVideoFrameMetadataListener)
        videoPlayer.release()
        super.release()
    }

    @UnstableApi
    override fun prepare(url: String, streamRequestHeaders: String?) {
        parseRetryJob?.cancel()
        parseRetryJob = null
        parseErrorRetryUsed = false
        activeStreamRequestHeaders = streamRequestHeaders
        contentTypeAttempts.clear()
        prepare(Uri.parse(url), null, streamRequestHeaders)
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
        parseRetryJob?.cancel()
        parseRetryJob = null
        releaseMulticastLock()
        videoPlayer.stop()
        videoPlayer.clearMediaItems()
        triggerBuffering(false)
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        if (boundTextureView != null) {
            videoPlayer.clearVideoTextureView(boundTextureView)
            boundTextureView = null
        }
        if (boundSurfaceView === surfaceView) return
        boundSurfaceView = surfaceView
        videoPlayer.setVideoSurfaceView(surfaceView)
    }

    override fun setVideoTextureView(textureView: TextureView) {
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