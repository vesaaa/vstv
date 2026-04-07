package com.vesaa.mytv.ui.screens.leanback.video.player

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.view.SurfaceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer

class LeanbackIjkVideoPlayer(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
) : LeanbackVideoPlayer(coroutineScope) {
    private val player = IjkMediaPlayer()
    private var updatePositionJob: Job? = null
    private var latestZapStartElapsedMs: Long = 0L

    override fun initialize() {
        super.initialize()
        IjkMediaPlayer.loadLibrariesOnce(null)
        IjkMediaPlayer.native_profileBegin("libijkplayer.so")
        bindListeners()
    }

    override fun release() {
        updatePositionJob?.cancel()
        updatePositionJob = null
        player.setOnPreparedListener(null)
        player.setOnVideoSizeChangedListener(null)
        player.setOnInfoListener(null)
        player.setOnErrorListener(null)
        player.reset()
        player.release()
        IjkMediaPlayer.native_profileEnd()
        super.release()
    }

    override fun prepare(url: String, streamRequestHeaders: String?) {
        val uri = Uri.parse(url)
        val headers = parseHeaders(streamRequestHeaders)
        player.reset()
        bindListeners()
        latestZapStartElapsedMs = SystemClock.elapsedRealtime()
        metadata = Metadata(zapLatencyMs = null, videoRenderedFps = 0f)
        triggerMetadata(metadata)
        triggerPrepared()
        if (headers.isEmpty()) {
            player.setDataSource(context, uri)
        } else {
            player.setDataSource(context, uri, headers)
        }
        player.prepareAsync()
    }

    override fun play() {
        player.start()
    }

    override fun pause() {
        player.pause()
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        player.setDisplay(surfaceView.holder)
    }

    private fun bindListeners() {
        player.setOnPreparedListener {
            val elapsed = (SystemClock.elapsedRealtime() - latestZapStartElapsedMs).coerceAtLeast(0L)
            metadata = metadata.copy(zapLatencyMs = elapsed)
            triggerMetadata(metadata)
            triggerReady()
            player.start()
            startPositionTicker()
        }
        player.setOnVideoSizeChangedListener { _, width, height, _, _ ->
            triggerResolution(width, height)
            metadata = metadata.copy(videoWidth = width, videoHeight = height)
            triggerMetadata(metadata)
        }
        player.setOnInfoListener { _, what, _ ->
            when (what) {
                IMediaPlayer.MEDIA_INFO_BUFFERING_START -> triggerBuffering(true)
                IMediaPlayer.MEDIA_INFO_BUFFERING_END -> triggerBuffering(false)
            }
            false
        }
        player.setOnErrorListener { _, what, extra ->
            triggerError(
                PlaybackException(
                    errorCodeName = "IJK_ERROR_$what",
                    errorCode = if (extra != 0) extra else what,
                )
            )
            true
        }
    }

    private fun startPositionTicker() {
        updatePositionJob?.cancel()
        updatePositionJob = coroutineScope.launch {
            triggerCurrentPosition(-1)
            while (true) {
                triggerCurrentPosition(player.currentPosition)
                delay(1000)
            }
        }
    }

    private fun parseHeaders(streamRequestHeaders: String?): Map<String, String> {
        val raw = streamRequestHeaders?.trim().orEmpty()
        if (raw.isEmpty()) return emptyMap()
        return raw
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains(':') }
            .map {
                val idx = it.indexOf(':')
                it.substring(0, idx).trim() to it.substring(idx + 1).trim()
            }
            .filter { (k, v) -> k.isNotEmpty() && v.isNotEmpty() }
            .toMap()
    }
}
