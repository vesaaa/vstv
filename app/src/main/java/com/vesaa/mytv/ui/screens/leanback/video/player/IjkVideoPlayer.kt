package com.vesaa.mytv.ui.screens.leanback.video.player

import android.content.Context
import android.net.Uri
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tv.danmaku.ijk.media.player.IMediaPlayer
import tv.danmaku.ijk.media.player.IjkMediaPlayer
import tv.danmaku.ijk.media.player.misc.ITrackInfo
import java.io.IOException

/**
 * 基于 debugly/ijkplayer (k0.8.9-beta) 的播放器后端。
 *
 * 与 Media3 后端的差异：
 * - 通过 FFmpeg 软解支持更广泛的容器/编码格式，适合兼容性要求高的 IPTV 场景。
 * - 元数据/码流信息不如 Media3 丰富（无独立编解码器/渲染帧回调）。
 * - 不支持 HLS master 清单探测、图片分片降级等 Media3 特有逻辑。
 */
class LeanbackIjkVideoPlayer(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
) : LeanbackVideoPlayer(coroutineScope) {

    private var mediaPlayer: IjkMediaPlayer? = null
    private var surfaceView: SurfaceView? = null
    private var textureView: TextureView? = null
    private var preparedUrl: String? = null
    private var isMuted = false
    private var seekOnPrepared: Long? = null
    private var prepared = false

    // ── 播放器实例 ────────────────────────────────────────────────

    private fun ensurePlayer(): IjkMediaPlayer {
        val existing = mediaPlayer
        if (existing != null) return existing

        val mp = IjkMediaPlayer().apply {
            // 通用选项：参考 debugly/ijkplayer 默认配置
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec", 1L)        // 硬解优先
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "mediacodec-hevc", 1L)   // HEVC 硬解
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "opensles", 0L)          // 禁用 OpenSL（兼容性）
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "timeout", 10_000_000L)   // 10s 超时
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "analyzeduration", 2_000_000L) // 2s 分析
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "probesize", 1_048_576L)  // 1MB 探测
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect", 1L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_streamed", 1L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "reconnect_delay_max", 5L)
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "packet-buffering", 0L)  // 禁用包缓冲（直播）
            setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "framedrop", 1L)         // 允许丢帧

            setOnPreparedListener(preparedListener)
            setOnCompletionListener(completionListener)
            setOnErrorListener(errorListener)
            setOnInfoListener(infoListener)
            setOnVideoSizeChangedListener(videoSizeListener)
            setOnBufferingUpdateListener(bufferingListener)
        }

        // 绑定当前 Surface
        surfaceView?.let { mp.setDisplay(it.holder) }
        textureView?.let { mp.setSurface(Surface(it.surfaceTexture)) }

        mediaPlayer = mp
        return mp
    }

    // ── 监听器 ────────────────────────────────────────────────────

    private val preparedListener = IMediaPlayer.OnPreparedListener {
        prepared = true
        triggerPrepared()
        triggerReady()
        triggerBuffering(false)

        seekOnPrepared?.let { pos ->
            mediaPlayer?.seekTo(pos)
            seekOnPrepared = null
        }
        updateTrackMetadata()
    }

    private val completionListener = IMediaPlayer.OnCompletionListener {
        // 直播流通常不会 completion；点播结束后通知上层。
        triggerError(null)
    }

    private val errorListener = IMediaPlayer.OnErrorListener { _, what, extra ->
        val code = (what * 1000) + extra
        triggerError(
            PlaybackException(
                errorCodeName = "IJK_ERROR",
                errorCode = code,
            )
        )
        triggerBuffering(false)
        true // 已处理
    }

    private val infoListener = IMediaPlayer.OnInfoListener { _, what, _ ->
        when (what) {
            IMediaPlayer.MEDIA_INFO_BUFFERING_START -> {
                triggerBuffering(true)
            }
            IMediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                triggerBuffering(false)
            }
            IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                triggerReady()
                triggerBuffering(false)
                if (prepared) updateTrackMetadata()
            }
        }
        true
    }

    private val videoSizeListener = IMediaPlayer.OnVideoSizeChangedListener { width, height, _, _ ->
        if (width > 0 && height > 0) {
            triggerResolution(width, height)
        }
    }

    private val bufferingListener = IMediaPlayer.OnBufferingUpdateListener { _, percent ->
        // ijkplayer 的缓冲百分比更新；不做额外处理，关键状态由 infoListener 驱动。
    }

    // ── 元数据采集 ────────────────────────────────────────────────

    private fun updateTrackMetadata() {
        val mp = mediaPlayer ?: return
        coroutineScope.launch(Dispatchers.Main) {
            try {
                val trackInfo = mp.trackInfo ?: return@launch
                for (info in trackInfo) {
                    val lang = info.language.orEmpty()
                    val infoString = info.infoString.orEmpty()
                    when (info.trackType) {
                        ITrackInfo.MEDIA_TRACK_TYPE_VIDEO -> {
                            // 从 infoString 解析可能的编码信息
                            // ijkplayer 的 ITrackInfo 没有独立的 mime/codec 字段，靠 FFmpeg 的 metadata
                            metadata = metadata.copy(
                                videoWidth = mediaPlayer?.videoWidth ?: 0,
                                videoHeight = mediaPlayer?.videoHeight ?: 0,
                            )
                            triggerMetadata(metadata)
                        }
                        ITrackInfo.MEDIA_TRACK_TYPE_AUDIO -> {
                            metadata = metadata.copy(
                                audioMimeType = infoString.takeIf { it.isNotBlank() } ?: metadata.audioMimeType,
                            )
                            triggerMetadata(metadata)
                        }
                    }
                }
            } catch (_: Exception) {
                // 轨信息获取失败时静默忽略
            }
        }
    }

    // ── HTTP Headers ──────────────────────────────────────────────

    private fun buildHeadersString(headersRaw: String?): String? {
        if (headersRaw.isNullOrBlank()) return null
        return headersRaw
            .trim()
            .lines()
            .joinToString("\r\n") { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@joinToString ""
                if (':' !in trimmed) return@joinToString ""
                val colonIdx = trimmed.indexOf(':')
                val key = trimmed.substring(0, colonIdx).trim()
                val value = trimmed.substring(colonIdx + 1).trim()
                "$key: $value"
            }
            .takeIf { it.isNotBlank() }
    }

    // ── prepare / 播放控制 ────────────────────────────────────────

    override fun prepare(url: String, streamRequestHeaders: String?) {
        prepared = false
        seekOnPrepared = null
        metadata = Metadata()
        triggerMetadata(metadata)

        val mp = ensurePlayer()
        try {
            // 重置播放器状态
            mp.reset()

            val uri = Uri.parse(url)
            val headers = buildHeadersString(streamRequestHeaders)

            if (headers != null) {
                mp.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "headers", headers)
            }

            // 设置 RTSP 传输方式：优先 TCP（兼容性更好）
            if (uri.scheme.equals("rtsp", ignoreCase = true)) {
                mp.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "rtsp_transport", "tcp")
            }

            // 针对 UDP/RTP 组播增大缓冲区
            if (uri.scheme.equals("udp", ignoreCase = true) || uri.scheme.equals("rtp", ignoreCase = true)) {
                mp.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "buffer_size", 2_097_152L) // 2MB
            }

            mp.dataSource = url
            preparedUrl = url
            mp.prepareAsync()
            triggerPrepared()
        } catch (e: IOException) {
            triggerError(
                PlaybackException(
                    errorCodeName = "IJK_IO_ERROR",
                    errorCode = 4001,
                ).apply { initCause(e) }
            )
        } catch (e: Exception) {
            triggerError(
                PlaybackException(
                    errorCodeName = "IJK_UNEXPECTED",
                    errorCode = 5000,
                ).apply { initCause(e) }
            )
        }
    }

    override fun play() {
        mediaPlayer?.start()
    }

    override fun pause() {
        mediaPlayer?.pause()
    }

    override fun setMuted(muted: Boolean) {
        isMuted = muted
        mediaPlayer?.setVolume(
            if (muted) 0f else 1f,
            if (muted) 0f else 1f,
        )
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        if (textureView != null) {
            mediaPlayer?.setSurface(null)
            textureView = null
        }
        if (this.surfaceView === surfaceView) return
        this.surfaceView = surfaceView
        mediaPlayer?.setDisplay(surfaceView.holder)
    }

    override fun setVideoTextureView(textureView: TextureView) {
        if (surfaceView != null) {
            mediaPlayer?.setDisplay(null)
            surfaceView = null
        }
        if (this.textureView === textureView) return
        this.textureView = textureView
        mediaPlayer?.setSurface(Surface(textureView.surfaceTexture))
    }

    override fun seekTo(positionMs: Long) {
        if (prepared) {
            mediaPlayer?.seekTo(positionMs)
        } else {
            seekOnPrepared = positionMs
        }
    }

    override fun seekToDefaultPosition() {
        // 直播场景：跳到尽可能靠后的位置（模拟 live edge）
        val mp = mediaPlayer ?: return
        if (prepared) {
            val dur = mp.duration
            if (dur > 0) {
                mp.seekTo(dur - 5000L)
            }
        }
    }

    override fun seekBack(offsetMs: Long) {
        val mp = mediaPlayer ?: return
        if (!prepared) return
        val dur = mp.duration
        val pos = if (dur > 0) {
            maxOf(0L, dur - offsetMs)
        } else {
            maxOf(0L, mp.currentPosition - offsetMs)
        }
        mp.seekTo(pos)
    }

    // ── 轨信息 ────────────────────────────────────────────────────

    override fun getTrackOptions(type: TrackType): List<TrackOption> {
        val mp = mediaPlayer ?: return emptyList()
        val tracks = mp.trackInfo ?: return emptyList()
        val targetType = when (type) {
            TrackType.Audio -> ITrackInfo.MEDIA_TRACK_TYPE_AUDIO
            TrackType.Video -> ITrackInfo.MEDIA_TRACK_TYPE_VIDEO
        }

        return tracks
            .filter { it.trackType == targetType }
            .mapIndexed { idx, info ->
                val lang = info.language?.takeIf { it.isNotBlank() && it != "und" } ?: "${type.name}${idx + 1}"
                val label = buildString {
                    append(lang)
                    val extra = info.infoString?.takeIf { it.isNotBlank() }
                    if (extra != null) append(" · $extra")
                }
                TrackOption(
                    id = "$targetType#$idx",
                    label = label,
                    selected = mp.getSelectedTrack(info.trackType) == idx,
                )
            }
    }

    override fun selectTrack(type: TrackType, trackId: String) {
        val mp = mediaPlayer ?: return
        val tracks = mp.trackInfo ?: return
        val targetType = when (type) {
            TrackType.Audio -> ITrackInfo.MEDIA_TRACK_TYPE_AUDIO
            TrackType.Video -> ITrackInfo.MEDIA_TRACK_TYPE_VIDEO
        }

        val targetIdx = trackId.removePrefix("$targetType#").toIntOrNull() ?: return
        val matchedTracks = tracks.filter { it.trackType == targetType }
        if (targetIdx !in matchedTracks.indices) return

        // 取消其他轨选择，选择目标轨
        matchedTracks.forEachIndexed { idx, _ ->
            if (idx == targetIdx) {
                mp.selectTrack(targetType)
            } else {
                mp.deselectTrack(targetType)
            }
        }
    }

    // ── 生命周期 ──────────────────────────────────────────────────

    override fun initialize() {
        super.initialize()
        // 确保播放器实例可创建。首次创建会触发 native 库加载，放在初始化阶段可更早发现加载失败。
        ensurePlayer()
    }

    override fun onDeactivate() {
        mediaPlayer?.stop()
        prepared = false
        seekOnPrepared = null
        preparedUrl = null
        triggerBuffering(false)
    }

    override fun release() {
        mediaPlayer?.apply {
            stop()
            reset()
            release()
        }
        mediaPlayer = null
        surfaceView = null
        textureView = null
        prepared = false
        super.release()
    }
}