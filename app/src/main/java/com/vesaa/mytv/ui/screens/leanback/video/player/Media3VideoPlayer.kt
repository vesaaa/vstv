package com.vesaa.mytv.ui.screens.leanback.video.player

import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vesaa.mytv.ui.utils.SP
import com.vesaa.mytv.utils.IptvOutboundHeaderPolicy
import com.vesaa.mytv.utils.normalizeIptvRequestHeadersInput
import com.vesaa.mytv.utils.parseHttpHeaderLines
import androidx.media3.common.PlaybackException as Media3PlaybackException

@OptIn(UnstableApi::class)
class LeanbackMedia3VideoPlayer(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
) : LeanbackVideoPlayer(coroutineScope) {
    // IPTV 场景优化的缓冲策略：
    // - min 60s / max 120s：允许更积极地持续下载，扩大后续缓冲，抵御网络抖动
    // - bufferForPlayback 1.5s：更快出画，换台体验更好（默认 2.5s）
    // - bufferForPlaybackAfterRebuffer 保持 5s：rebuffer 后稳一些再播
    // - prioritizeTimeOverSizeThresholds=true：高码率流不会因字节上限提前停止下载
    // - backBuffer=0：直播无需保留历史回看缓冲，节省内存
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

    private val videoPlayer = ExoPlayer.Builder(
        context,
        DefaultRenderersFactory(context).setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
    )
        .setLoadControl(loadControl)
        .build().apply {
            playWhenReady = true
        }

    private val contentTypeAttempts = mutableMapOf<Int, Boolean>()
    private var parseRetryJob: Job? = null
    private var prepareFetchJob: Job? = null
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

    /**
     * 当前会话使用的直播起播配置。公共 prepare 根据源类型与缓存决定；容器类型/解析重试
     * 会沿用该值（同一 URL 的窗口尺寸不会变）。
     */
    private var activeLiveConfig: MediaItem.LiveConfiguration = DEFAULT_LIVE_CONFIG

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

    @OptIn(UnstableApi::class)
    private fun prepare(uri: Uri, contentType: Int? = null, streamRequestHeaders: String? = null) {
        val headers = streamRequestHeaders ?: activeStreamRequestHeaders
        val dataSourceFactory =
            DefaultDataSource.Factory(context, httpDataSourceFactory(uri, headers))
        val isRtmp = uri.scheme.equals("rtmp", ignoreCase = true)
        // rtp:// 与 udp:// 在 IPTV 场景下传输层相同，ExoPlayer UdpDataSource 可直接处理；
        // 仅重写 scheme，端口/地址保持不变。
        val effectiveUri = if (uri.scheme.equals("rtp", ignoreCase = true)) {
            uri.buildUpon().scheme("udp").build()
        } else {
            uri
        }

        // activeLiveConfig 由公共 prepare 根据 URL 缓存计算；容器类型/解析重试
        // 沿用该值（同一 URL 的窗口尺寸不会变）。详见 computeLiveConfig。
        val mediaItem = MediaItem.Builder()
            .setUri(effectiveUri)
            .setLiveConfiguration(activeLiveConfig)
            .build()

        val mediaSource = if (isRtmp) {
            ProgressiveMediaSource.Factory(RtmpDataSource.Factory()).createMediaSource(mediaItem)
        } else when (val type = contentType ?: Util.inferContentType(effectiveUri)) {
            C.CONTENT_TYPE_HLS -> {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }

            C.CONTENT_TYPE_DASH -> {
                DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }

            C.CONTENT_TYPE_SS -> {
                SsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }

            C.CONTENT_TYPE_RTSP -> {
                RtspMediaSource.Factory().createMediaSource(mediaItem)
            }

            C.CONTENT_TYPE_OTHER -> {
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
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

    override fun initialize() {
        super.initialize()
        videoPlayer.addListener(playerListener)
        videoPlayer.addAnalyticsListener(metadataListener)
        videoPlayer.addAnalyticsListener(eventLogger)
        videoPlayer.setVideoFrameMetadataListener(videoFrameMetadataListener)
    }

    override fun release() {
        parseRetryJob?.cancel()
        prepareFetchJob?.cancel()
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
        prepareFetchJob?.cancel()
        prepareFetchJob = null

        val uri = Uri.parse(url)
        val inferredType = Util.inferContentType(uri)

        // HLS：优先用之前缓存的窗口尺寸算出最优 LiveConfiguration；未命中则用默认（24s，
        // 对常见 ~30s 窗口已经够用），同时在后台抓取 m3u8 更新缓存以优化下一次切台。
        activeLiveConfig = if (inferredType == C.CONTENT_TYPE_HLS) {
            HlsWindowCache.get(context, url)?.let(::computeLiveConfig) ?: DEFAULT_LIVE_CONFIG
        } else {
            DEFAULT_LIVE_CONFIG
        }

        prepare(uri, null, streamRequestHeaders)

        if (inferredType == C.CONTENT_TYPE_HLS) {
            prepareFetchJob = coroutineScope.launch {
                val windowMs = withContext(Dispatchers.IO) {
                    HlsWindowProbe.fetchWindowMs(url, streamRequestHeaders)
                }
                if (windowMs != null) {
                    HlsWindowCache.put(context, url, windowMs)
                }
            }
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
        parseRetryJob?.cancel()
        parseRetryJob = null
        prepareFetchJob?.cancel()
        prepareFetchJob = null
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

    companion object {
        /**
         * 兜底直播起播配置：目标延迟 24s、允许 12s~28s 漂移、±3% 变速。
         * 适配常见 ~30s 滑动窗口的 IPTV 源；非直播源会被 MediaSource 自动忽略；
         * 窗口远大于 30s 的源会在缓存建立后由 [computeLiveConfig] 计算出更优值替换。
         */
        private val DEFAULT_LIVE_CONFIG: MediaItem.LiveConfiguration =
            MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(24_000L)
                .setMinOffsetMs(12_000L)
                .setMaxOffsetMs(28_000L)
                .setMinPlaybackSpeed(0.97f)
                .setMaxPlaybackSpeed(1.03f)
                .build()

        /**
         * 根据探测到的 HLS 滑动窗口 [windowMs] 计算最优 LiveConfiguration：
         * - 目标延迟：窗口末端再回撤 3s（留出抓包/seek 容错），并限制在 12s~60s 区间
         * - 最小延迟：目标的一半，并不低于 6s（避免贴 live edge）
         * - 最大延迟：窗口末端再回撤 1.5s，但不低于目标 +0.5s（保证 min ≤ target ≤ max）
         */
        private fun computeLiveConfig(windowMs: Long): MediaItem.LiveConfiguration {
            val target = (windowMs - 3_000L).coerceIn(12_000L, 60_000L)
            val min = (target / 2).coerceAtLeast(6_000L).coerceAtMost(target)
            val max = (windowMs - 1_500L).coerceAtLeast(target + 500L)
            return MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(target)
                .setMinOffsetMs(min)
                .setMaxOffsetMs(max)
                .setMinPlaybackSpeed(0.97f)
                .setMaxPlaybackSpeed(1.03f)
                .build()
        }
    }
}