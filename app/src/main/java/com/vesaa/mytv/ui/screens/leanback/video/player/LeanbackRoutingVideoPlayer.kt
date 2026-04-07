package com.vesaa.mytv.ui.screens.leanback.video.player

import android.net.Uri
import android.view.SurfaceView
import kotlinx.coroutines.CoroutineScope

class LeanbackRoutingVideoPlayer(
    private val media3Player: LeanbackMedia3VideoPlayer,
    private val vlcPlayer: LeanbackVlcVideoPlayer,
    coroutineScope: CoroutineScope,
) : LeanbackVideoPlayer(coroutineScope) {
    private var currentPlayer: LeanbackVideoPlayer = media3Player
    private var currentSurfaceView: SurfaceView? = null

    override fun initialize() {
        super.initialize()
        media3Player.initialize()
        vlcPlayer.initialize()
        bindChild(media3Player)
        bindChild(vlcPlayer)
    }

    override fun release() {
        media3Player.release()
        vlcPlayer.release()
        super.release()
    }

    override fun prepare(url: String, streamRequestHeaders: String?) {
        val target = if (isRtsp(url)) vlcPlayer else media3Player
        if (currentPlayer != target) {
            currentPlayer.onDeactivate()
        } else {
            currentPlayer.pause()
        }
        currentPlayer = target
        currentSurfaceView?.let { currentPlayer.setVideoSurfaceView(it) }
        currentPlayer.prepare(url, streamRequestHeaders)
    }

    override fun play() {
        currentPlayer.play()
    }

    override fun pause() {
        currentPlayer.pause()
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        currentSurfaceView = surfaceView
        currentPlayer.setVideoSurfaceView(surfaceView)
    }

    private fun bindChild(player: LeanbackVideoPlayer) {
        player.onResolution { width, height -> if (player == currentPlayer) triggerResolution(width, height) }
        player.onError { ex -> if (player == currentPlayer) triggerError(ex) }
        player.onReady { if (player == currentPlayer) triggerReady() }
        player.onBuffering { buffering -> if (player == currentPlayer) triggerBuffering(buffering) }
        player.onPrepared { if (player == currentPlayer) triggerPrepared() }
        player.onMetadata { metadata -> if (player == currentPlayer) triggerMetadata(metadata) }
        player.onCutoff { if (player == currentPlayer) triggerCutoff() }
    }

    private fun isRtsp(url: String): Boolean {
        return Uri.parse(url).scheme.equals("rtsp", ignoreCase = true)
    }
}
