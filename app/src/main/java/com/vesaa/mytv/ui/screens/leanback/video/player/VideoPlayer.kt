package com.vesaa.mytv.ui.screens.leanback.video.player

import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.PlaybackException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.vesaa.mytv.ui.utils.SP
import com.vesaa.mytv.utils.Loggable

abstract class LeanbackVideoPlayer(
    private val coroutineScope: CoroutineScope,
) : Loggable() {
    private var loadTimeoutJob: Job? = null
    private var cutoffTimeoutJob: Job? = null

    protected var metadata = Metadata()

    open fun initialize() {
        clearAllListeners()
    }

    open fun release() {
        clearAllListeners()
    }

    /** 当路由层切换到其它播放器内核时调用，用于停止当前会话并清理瞬时状态。 */
    open fun onDeactivate() = Unit

    /**
     * @param streamRequestHeaders 非 null 且非空白时，按多行请求头解析 UA 与其它头用于拉流；null 或空白则用 [SP.playbackHttpUserAgent] 等全局行为。
     */
    abstract fun prepare(url: String, streamRequestHeaders: String? = null)

    abstract fun play()

    abstract fun pause()

    /** 静音控制（分屏时用于仅保留激活子屏发声） */
    abstract fun setMuted(muted: Boolean)

    abstract fun setVideoSurfaceView(surfaceView: SurfaceView)

    /** 分屏模式可改用 TextureView，避免部分电视上 SurfaceView 覆盖 UI 角标。 */
    abstract fun setVideoTextureView(textureView: TextureView)

    private val onResolutionListeners = mutableListOf<(width: Int, height: Int) -> Unit>()
    private val onErrorListeners = mutableListOf<(error: PlaybackException?) -> Unit>()
    private val onReadyListeners = mutableListOf<() -> Unit>()
    private val onBufferingListeners = mutableListOf<(buffering: Boolean) -> Unit>()
    private val onPreparedListeners = mutableListOf<() -> Unit>()
    private val onMetadataListeners = mutableListOf<(metadata: Metadata) -> Unit>()
    private val onCutoffListeners = mutableListOf<() -> Unit>()

    private fun clearAllListeners() {
        onResolutionListeners.clear()
        onErrorListeners.clear()
        onReadyListeners.clear()
        onBufferingListeners.clear()
        onPreparedListeners.clear()
        onMetadataListeners.clear()
        onCutoffListeners.clear()
    }

    protected fun triggerResolution(width: Int, height: Int) {
        onResolutionListeners.forEach { it(width, height) }
    }

    protected fun triggerError(error: PlaybackException?) {
        onErrorListeners.forEach { it(error) }
        if(error != PlaybackException.LOAD_TIMEOUT) {
            loadTimeoutJob?.cancel()
            loadTimeoutJob = null
        }
    }

    protected fun triggerReady() {
        onReadyListeners.forEach { it() }
        loadTimeoutJob?.cancel()
    }

    /**
     * 事件驱动的「持续缓冲超时」检测：进入 STATE_BUFFERING 时启动倒计时，
     * 离开时取消；到期视作频道持续拉不到数据，触发 cutoff 由上层决定切换/提示。
     *
     * 取代了此前按 1Hz 轮询 `currentPosition` 的推断式实现 —— 该实现既无法区分
     * "真正卡顿"与"正在缓冲"，且 cutoff 只会重新 prepare 同线路，实际意义有限。
     */
    protected fun triggerBuffering(buffering: Boolean) {
        onBufferingListeners.forEach { it(buffering) }
        if (buffering) {
            cutoffTimeoutJob?.cancel()
            cutoffTimeoutJob = coroutineScope.launch {
                delay(SP.videoPlayerLoadTimeout)
                triggerCutoff()
            }
        } else {
            cutoffTimeoutJob?.cancel()
            cutoffTimeoutJob = null
        }
    }

    protected fun triggerPrepared() {
        onPreparedListeners.forEach { it() }
        loadTimeoutJob?.cancel()
        loadTimeoutJob = coroutineScope.launch {
            delay(SP.videoPlayerLoadTimeout)
            triggerError(PlaybackException.LOAD_TIMEOUT)
        }
        cutoffTimeoutJob?.cancel()
        cutoffTimeoutJob = null
    }

    protected fun triggerMetadata(metadata: Metadata) {
        onMetadataListeners.forEach { it(metadata) }
    }

    protected fun triggerCutoff() {
        onCutoffListeners.forEach { it() }
    }

    fun onResolution(listener: (width: Int, height: Int) -> Unit) {
        onResolutionListeners.add(listener)
    }

    fun onError(listener: (error: PlaybackException?) -> Unit) {
        onErrorListeners.add(listener)
    }

    fun onReady(listener: () -> Unit) {
        onReadyListeners.add(listener)
    }

    fun onBuffering(listener: (buffering: Boolean) -> Unit) {
        onBufferingListeners.add(listener)
    }

    fun onPrepared(listener: () -> Unit) {
        onPreparedListeners.add(listener)
    }

    fun onMetadata(listener: (metadata: Metadata) -> Unit) {
        onMetadataListeners.add(listener)
    }

    fun onCutoff(listener: () -> Unit) {
        onCutoffListeners.add(listener)
    }

    data class PlaybackException(
        val errorCodeName: String,
        val errorCode: Int,
    ) : Exception(errorCodeName) {
        companion object {
            val UNSUPPORTED_TYPE =
                PlaybackException("UNSUPPORTED_TYPE", 10002)
            val LOAD_TIMEOUT =
                PlaybackException("LOAD_TIMEOUT", 10003)
        }
    }

    /** 元数据 */
    data class Metadata(
        /** 视频编码 */
        val videoMimeType: String = "",
        /** 视频 codecs（例如 avc1.640028 / hev1 / dvhe） */
        val videoCodecs: String = "",
        /** 视频宽度 */
        val videoWidth: Int = 0,
        /** 视频高度 */
        val videoHeight: Int = 0,
        /** 视频颜色 */
        val videoColor: String = "",
        /** 视频帧率 */
        val videoFrameRate: Float = 0f,
        /** 实时估算视频帧率（基于渲染帧统计） */
        val videoRenderedFps: Float = 0f,
        /** 视频比特率 */
        val videoBitrate: Int = 0,
        /** 最近一次切台到首帧就绪耗时（毫秒）；未知为 null */
        val zapLatencyMs: Long? = null,
        /** 视频解码器 */
        val videoDecoder: String = "",

        /** 音频编码 */
        val audioMimeType: String = "",
        /** 音频 codecs（例如 mp4a.40.2 / ec-3 / ec+3） */
        val audioCodecs: String = "",
        /** 音频通道 */
        val audioChannels: Int = 0,
        /** 音频采样率 */
        val audioSampleRate: Int = 0,
        /** 音频解码器 */
        val audioDecoder: String = "",
    )
}