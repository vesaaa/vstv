package com.vesaa.mytv.ui.screens.leanback.video.player

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import android.view.TextureView
import kotlinx.coroutines.CoroutineScope

/**
 * 按协议路由播放器：RTSP 走 IJK，其他协议继续使用 Media3。
 */
class LeanbackProtocolRoutedVideoPlayer(
    context: Context,
    coroutineScope: CoroutineScope,
) : LeanbackVideoPlayer(coroutineScope) {
    private val media3 = LeanbackMedia3VideoPlayer(context, coroutineScope)
    private val ijk = LeanbackIjkVideoPlayer(context, coroutineScope)
    private var active: LeanbackVideoPlayer = media3

    private var boundSurfaceView: SurfaceView? = null
    private var boundTextureView: TextureView? = null
    private var playRequested = true
    private var currentUrl: String = ""
    private var currentHeaders: String? = null
    private var hasTriedHevcIjkFallback = false

    init {
        media3.onResolution { w, h -> if (active === media3) triggerResolution(w, h) }
        media3.onPrepared { if (active === media3) triggerPrepared() }
        media3.onReady { if (active === media3) triggerReady() }
        media3.onBuffering { if (active === media3) triggerBuffering(it) }
        media3.onMetadata {
            if (active !== media3) return@onMetadata
            triggerMetadata(it)
            maybeSwitchHevcAudioOnlyToIjk(it)
        }
        media3.onCutoff { if (active === media3) triggerCutoff() }
        media3.onError { ex -> if (active === media3) triggerError(ex) }

        ijk.onResolution { w, h -> if (active === ijk) triggerResolution(w, h) }
        ijk.onPrepared { if (active === ijk) triggerPrepared() }
        ijk.onReady { if (active === ijk) triggerReady() }
        ijk.onBuffering { if (active === ijk) triggerBuffering(it) }
        ijk.onMetadata { if (active === ijk) triggerMetadata(it) }
        ijk.onCutoff { if (active === ijk) triggerCutoff() }
        ijk.onError { ex -> if (active === ijk) triggerError(ex) }
    }

    override fun initialize() {
        super.initialize()
        media3.initialize()
        ijk.initialize()
    }

    override fun release() {
        media3.release()
        ijk.release()
        super.release()
    }

    override fun onDeactivate() {
        media3.onDeactivate()
        ijk.onDeactivate()
        hasTriedHevcIjkFallback = false
        currentUrl = ""
        currentHeaders = null
    }

    override fun prepare(url: String, streamRequestHeaders: String?) {
        val targetUrl = url.trim()
        currentUrl = targetUrl
        currentHeaders = streamRequestHeaders
        hasTriedHevcIjkFallback = false
        active = if (targetUrl.isRtspUrl()) ijk else media3
        if (active === ijk) media3.onDeactivate() else ijk.onDeactivate()
        applyBoundSurfaceToActive()
        active.prepare(targetUrl, streamRequestHeaders)
        if (playRequested) active.play() else active.pause()
    }

    override fun play() {
        playRequested = true
        active.play()
    }

    override fun pause() {
        playRequested = false
        active.pause()
    }

    override fun setMuted(muted: Boolean) {
        media3.setMuted(muted)
        ijk.setMuted(muted)
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        boundTextureView = null
        boundSurfaceView = surfaceView
        media3.setVideoSurfaceView(surfaceView)
        ijk.setVideoSurfaceView(surfaceView)
    }

    override fun setVideoTextureView(textureView: TextureView) {
        boundSurfaceView = null
        boundTextureView = textureView
        media3.setVideoTextureView(textureView)
        ijk.setVideoTextureView(textureView)
    }

    override fun seekTo(positionMs: Long) {
        active.seekTo(positionMs)
    }

    override fun seekToDefaultPosition() {
        active.seekToDefaultPosition()
    }

    override fun seekBack(offsetMs: Long) {
        active.seekBack(offsetMs)
    }

    override fun getTrackOptions(type: TrackType): List<TrackOption> {
        return active.getTrackOptions(type)
    }

    override fun selectTrack(type: TrackType, trackId: String) {
        active.selectTrack(type, trackId)
    }

    private fun applyBoundSurfaceToActive() {
        boundSurfaceView?.let { active.setVideoSurfaceView(it) }
        boundTextureView?.let { active.setVideoTextureView(it) }
    }

    private fun maybeSwitchHevcAudioOnlyToIjk(metadata: Metadata) {
        if (hasTriedHevcIjkFallback || active !== media3) return
        if (!metadata.audioOnlyModeHint) return
        if (!metadata.looksLikeHevc()) return
        if (currentUrl.isBlank()) return

        hasTriedHevcIjkFallback = true
        media3.onDeactivate()
        active = ijk
        applyBoundSurfaceToActive()
        active.prepare(currentUrl, currentHeaders)
        if (playRequested) active.play() else active.pause()
    }

    private fun String.isRtspUrl(): Boolean = runCatching {
        Uri.parse(this).scheme.equals("rtsp", ignoreCase = true)
    }.getOrDefault(false)

    private fun Metadata.looksLikeHevc(): Boolean {
        val mime = videoMimeType.lowercase()
        val codecs = videoCodecs.lowercase()
        return mime.contains("hevc") || codecs.contains("hev1") || codecs.contains("hvc1")
    }
}
