package com.vesaa.mytv.ui.screens.leanback.video.player

import android.content.Context
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import kotlinx.coroutines.CoroutineScope
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer

class LeanbackIjkVideoPlayer(
    private val context: Context,
    coroutineScope: CoroutineScope,
) : LeanbackVideoPlayer(coroutineScope) {
    private var player: IjkMediaPlayer? = null
    private var boundSurfaceView: SurfaceView? = null
    private var boundTextureView: TextureView? = null
    private var textureSurface: Surface? = null

    override fun initialize() {
        super.initialize()
        if (player != null) return
        player = IjkMediaPlayer().apply {
            // RTSP 默认优先 TCP，弱网/跨网段环境更稳。
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "tcp")
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 20_000_000L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-auto-rotate", 1L)
            setOnPreparedListener {
                triggerReady()
                triggerBuffering(false)
            }
            setOnInfoListener { _, what, _ ->
                when (what) {
                    IMediaPlayer.MEDIA_INFO_BUFFERING_START -> triggerBuffering(true)
                    IMediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                        triggerBuffering(false)
                        triggerReady()
                    }
                    IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> triggerReady()
                }
                false
            }
            setOnVideoSizeChangedListener { _, width, height, _, _ ->
                triggerResolution(width, height)
            }
            setOnErrorListener { _, what, extra ->
                triggerError(PlaybackException("IJK_ERROR_${what}_$extra", 4001))
                true
            }
        }
    }

    override fun release() {
        boundTextureView = null
        boundSurfaceView = null
        textureSurface?.release()
        textureSurface = null
        player?.runCatching { reset() }
        player?.runCatching { release() }
        player = null
        super.release()
    }

    override fun onDeactivate() {
        player?.runCatching { stop() }
        player?.runCatching { reset() }
        triggerBuffering(false)
    }

    override fun prepare(url: String, streamRequestHeaders: String?) {
        initialize()
        val p = player ?: return
        if (!url.startsWith("rtsp://", ignoreCase = true)) {
            triggerError(PlaybackException.UNSUPPORTED_TYPE)
            return
        }
        runCatching {
            p.reset()
            applyBoundSurface()
            p.setDataSource(url.trim())
            p.prepareAsync()
            triggerPrepared()
            triggerBuffering(true)
        }.onFailure {
            triggerError(PlaybackException("IJK_PREPARE_FAILED", 4002))
        }
    }

    override fun play() {
        player?.runCatching { start() }
    }

    override fun pause() {
        player?.runCatching { pause() }
    }

    override fun setMuted(muted: Boolean) {
        val volume = if (muted) 0f else 1f
        player?.runCatching { setVolume(volume, volume) }
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        boundTextureView = null
        textureSurface?.release()
        textureSurface = null
        boundSurfaceView = surfaceView
        applyBoundSurface()
    }

    override fun setVideoTextureView(textureView: TextureView) {
        boundSurfaceView = null
        boundTextureView = textureView
        textureSurface?.release()
        textureSurface = textureView.surfaceTexture?.let { Surface(it) }
        applyBoundSurface()
    }

    override fun seekTo(positionMs: Long) {
        player?.runCatching { seekTo(positionMs) }
    }

    override fun seekToDefaultPosition() = Unit

    override fun seekBack(offsetMs: Long) {
        val p = player ?: return
        val current = runCatching { p.currentPosition }.getOrDefault(0L)
        p.runCatching { seekTo((current - offsetMs).coerceAtLeast(0L)) }
    }

    private fun applyBoundSurface() {
        val p = player ?: return
        boundSurfaceView?.holder?.surface?.takeIf { it.isValid }?.let {
            p.runCatching { setSurface(it) }
            return
        }
        textureSurface?.takeIf { it.isValid }?.let {
            p.runCatching { setSurface(it) }
        }
    }
}
