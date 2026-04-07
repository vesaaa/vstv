package com.vesaa.mytv.ui.screens.leanback.video.player

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.os.Handler
import android.os.SystemClock
import android.view.SurfaceView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class LeanbackVlcVideoPlayer(
    context: Context,
    private val coroutineScope: CoroutineScope,
) : LeanbackVideoPlayer(coroutineScope) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val libVlc = LibVLC(
        context.applicationContext,
        arrayListOf(
            "--avcodec-hw=any",
            "--network-caching=1200",
            "--live-caching=1200",
        ),
    )
    private val player = MediaPlayer(libVlc)
    private var updatePositionJob: Job? = null
    private var latestZapStartElapsedMs: Long = 0L
    private var currentSurfaceView: SurfaceView? = null
    private var attachedSurface = false

    override fun initialize() {
        super.initialize()
        player.setEventListener(::onPlayerEvent)
    }

    override fun release() {
        updatePositionJob?.cancel()
        updatePositionJob = null
        player.setEventListener(null)
        detachSurfaceIfNeeded()
        player.stop()
        player.release()
        libVlc.release()
        super.release()
    }

    override fun onDeactivate() {
        updatePositionJob?.cancel()
        updatePositionJob = null
        player.stop()
        detachSurfaceIfNeeded()
        triggerBuffering(false)
    }

    override fun prepare(url: String, streamRequestHeaders: String?) {
        latestZapStartElapsedMs = SystemClock.elapsedRealtime()
        metadata = Metadata(zapLatencyMs = null, videoRenderedFps = 0f)
        triggerMetadata(metadata)
        triggerPrepared()

        val media = Media(libVlc, Uri.parse(url)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=1200")
            addOption(":live-caching=1200")
        }
        player.media = media
        media.release()
        ensureSurfaceAttached()
        player.play()
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        currentSurfaceView = surfaceView
        ensureSurfaceAttached()
    }

    private fun ensureSurfaceAttached() {
        val view = currentSurfaceView ?: return
        if (attachedSurface) return
        player.vlcVout.setVideoView(view)
        player.vlcVout.attachViews()
        attachedSurface = true
    }

    private fun detachSurfaceIfNeeded() {
        if (!attachedSurface) return
        player.vlcVout.detachViews()
        attachedSurface = false
    }

    private fun onPlayerEvent(event: MediaPlayer.Event) {
        // VLC 回调线程并不保证是主线程，这里统一切回主线程，避免改 Compose 状态时并发崩溃。
        mainHandler.post {
            when (event.type) {
                MediaPlayer.Event.Buffering -> {
                    val buffering = event.buffering < 100f
                    triggerBuffering(buffering)
                }

                MediaPlayer.Event.Playing -> {
                    val elapsed = (SystemClock.elapsedRealtime() - latestZapStartElapsedMs).coerceAtLeast(0L)
                    metadata = metadata.copy(zapLatencyMs = elapsed)
                    triggerMetadata(metadata)
                    triggerBuffering(false)
                    triggerReady()
                    startPositionTicker()
                }

                MediaPlayer.Event.EncounteredError -> {
                    triggerError(PlaybackException("VLC_ENCOUNTERED_ERROR", 20001))
                }

                MediaPlayer.Event.EndReached -> {
                    triggerError(PlaybackException("VLC_END_REACHED", 20002))
                }
            }
        }
    }

    private fun startPositionTicker() {
        updatePositionJob?.cancel()
        updatePositionJob = coroutineScope.launch {
            triggerCurrentPosition(-1)
            while (true) {
                triggerCurrentPosition(player.time)
                delay(1000)
            }
        }
    }
}
