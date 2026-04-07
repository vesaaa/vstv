package com.vesaa.mytv.ui.screens.leanback.video.player

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.view.SurfaceView
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
import androidx.media3.exoplayer.DecoderReuseEvaluation
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

@OptIn(UnstableApi::class)
class LeanbackMedia3VideoPlayer(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
) : LeanbackVideoPlayer(coroutineScope) {
    private val videoPlayer = ExoPlayer.Builder(
        context,
        DefaultRenderersFactory(context).setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON)
    ).build().apply {
        playWhenReady = true
    }

    private val contentTypeAttempts = mutableMapOf<Int, Boolean>()
    private var updatePositionJob: Job? = null
    private var latestZapStartElapsedMs: Long = 0L
    private var awaitingFirstReadyAfterPrepare: Boolean = false
    private var lastRenderedFpsElapsedMs: Long = 0L
    private var fpsWindowStartNs: Long = 0L
    private var fpsWindowFrameCount: Int = 0

    /** 同一次播放会话内重试容器类型时沿用（收藏夹 per-entry 头等） */
    private var activeStreamRequestHeaders: String? = null

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

        val mediaItem = MediaItem.fromUri(uri)

        val mediaSource = when (val type = contentType ?: Util.inferContentType(uri)) {
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
            contentTypeAttempts[contentType ?: Util.inferContentType(uri)] = true
            videoPlayer.setMediaSource(mediaSource)
            videoPlayer.prepare()
            triggerPrepared()
        }
        updatePositionJob?.cancel()
        updatePositionJob = null
    }

    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            triggerResolution(videoSize.width, videoSize.height)
        }

        override fun onPlayerError(ex: Media3PlaybackException) {
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

                updatePositionJob?.cancel()
                updatePositionJob = coroutineScope.launch {
                    triggerCurrentPosition(-1)
                    while (true) {
                        triggerCurrentPosition(videoPlayer.currentPosition)
                        delay(1000)
                    }
                }
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
        videoPlayer.removeListener(playerListener)
        videoPlayer.removeAnalyticsListener(metadataListener)
        videoPlayer.removeAnalyticsListener(eventLogger)
        videoPlayer.setVideoFrameMetadataListener(noopVideoFrameMetadataListener)
        videoPlayer.release()
        super.release()
    }

    @UnstableApi
    override fun prepare(url: String, streamRequestHeaders: String?) {
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

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        videoPlayer.setVideoSurfaceView(surfaceView)
    }
}