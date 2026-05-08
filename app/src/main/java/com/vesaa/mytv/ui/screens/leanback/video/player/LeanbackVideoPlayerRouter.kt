package com.vesaa.mytv.ui.screens.leanback.video.player

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import android.view.TextureView
import kotlinx.coroutines.CoroutineScope

/**
 * 播放器路由层：根据 URL 协议自动选择后端。
 *
 * - RTSP / TCP / UDP / RTP → [LeanbackIjkVideoPlayer]（debugly/ijkplayer，FFmpeg 原生协议栈）
 * - 其他（HLS / DASH / HTTP / RTMP 等）→ [LeanbackMedia3VideoPlayer]（Media3 ExoPlayer）
 *
 * 路由对上层完全透明；[LeanbackVideoPlayerState] 无需感知后端切换。
 */
class LeanbackVideoPlayerRouter(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
) : LeanbackVideoPlayer(coroutineScope) {

    private val media3Player = LeanbackMedia3VideoPlayer(context, coroutineScope)
    private val ijkPlayer = LeanbackIjkVideoPlayer(context, coroutineScope)

    init {
        media3Player.onHlsFallbackToIjk = { url, headers ->
            stopImageSequenceMode()
            switchTo(ijkPlayer, url, headers)
        }
    }

    /** 当前活跃的后端（最后一次 prepare 时选中的后端） */
    private var activePlayer: LeanbackVideoPlayer? = null

    /** 当前准备好的 URL，用于判断是否需要切换后端 */
    private var currentUrl: String? = null

    // ── 协议判断 ─────────────────────────────────────────────────

    private fun selectBackend(url: String): LeanbackVideoPlayer {
        val scheme = runCatching { Uri.parse(url).scheme }.getOrNull()?.lowercase().orEmpty()
        return when (scheme) {
            "rtsp", "tcp", "udp", "rtp" -> ijkPlayer
            else -> media3Player
        }
    }

    // ── 事件转发（仅转发活跃后端的事件） ──────────────────────────

    private fun isActive(player: LeanbackVideoPlayer): Boolean = player === activePlayer

    private fun setupForwarding() {
        listOf(media3Player, ijkPlayer).forEach { player ->
            player.onResolution { w, h ->
                if (isActive(player)) triggerResolution(w, h)
            }
            player.onError { ex ->
                if (isActive(player)) triggerError(ex)
            }
            player.onReady {
                if (isActive(player)) triggerReady()
            }
            player.onBuffering { buffering ->
                if (isActive(player)) triggerBuffering(buffering)
            }
            player.onPrepared {
                if (isActive(player)) triggerPrepared()
            }
            player.onMetadata { meta ->
                if (isActive(player)) triggerMetadata(meta)
            }
            player.onCutoff {
                if (isActive(player)) triggerCutoff()
            }
        }
    }

    // ── 后端切换 ─────────────────────────────────────────────────

    private fun switchTo(player: LeanbackVideoPlayer, url: String, headers: String?) {
        val old = activePlayer
        if (old != null && old !== player) {
            old.onDeactivate()
        }
        activePlayer = player
        currentUrl = url
        player.prepare(url, headers)
    }

    // ── prepare ──────────────────────────────────────────────────

    override fun prepare(url: String, streamRequestHeaders: String?) {
        val backend = selectBackend(url)
        if (activePlayer === backend) {
            // 同后端换台：直接 delegate
            currentUrl = url
            backend.prepare(url, streamRequestHeaders)
        } else {
            switchTo(backend, url, streamRequestHeaders)
        }
    }

    // ── 播放控制 ─────────────────────────────────────────────────

    override fun play() {
        activePlayer?.play()
    }

    override fun pause() {
        activePlayer?.pause()
    }

    override fun setMuted(muted: Boolean) {
        // 静音状态对两个后端同时生效，避免切换时短暂有声
        media3Player.setMuted(muted)
        ijkPlayer.setMuted(muted)
    }

    override fun onDeactivate() {
        // 同时停掉两个后端，避免旧会话事件串扰
        media3Player.onDeactivate()
        ijkPlayer.onDeactivate()
        activePlayer = null
        currentUrl = null
        triggerBuffering(false)
    }

    // ── Surface 绑定 ─────────────────────────────────────────────

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        media3Player.setVideoSurfaceView(surfaceView)
        ijkPlayer.setVideoSurfaceView(surfaceView)
    }

    override fun setVideoTextureView(textureView: TextureView) {
        media3Player.setVideoTextureView(textureView)
        ijkPlayer.setVideoTextureView(textureView)
    }

    // ── Seek ─────────────────────────────────────────────────────

    override fun seekTo(positionMs: Long) {
        activePlayer?.seekTo(positionMs)
    }

    override fun seekToDefaultPosition() {
        activePlayer?.seekToDefaultPosition()
    }

    override fun seekBack(offsetMs: Long) {
        activePlayer?.seekBack(offsetMs)
    }

    // ── 轨信息 ───────────────────────────────────────────────────

    override fun getTrackOptions(type: TrackType): List<TrackOption> =
        activePlayer?.getTrackOptions(type) ?: emptyList()

    override fun selectTrack(type: TrackType, trackId: String) {
        activePlayer?.selectTrack(type, trackId)
    }

    // ── 生命周期 ─────────────────────────────────────────────────

    override fun initialize() {
        super.initialize()
        media3Player.initialize()
        ijkPlayer.initialize()
        setupForwarding()
    }

    override fun release() {
        media3Player.release()
        ijkPlayer.release()
        activePlayer = null
        currentUrl = null
        super.release()
    }
}