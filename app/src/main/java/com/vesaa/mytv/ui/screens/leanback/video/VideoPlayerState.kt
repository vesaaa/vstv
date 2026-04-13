package com.vesaa.mytv.ui.screens.leanback.video

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vesaa.mytv.ui.screens.leanback.video.player.LeanbackMedia3VideoPlayer
import com.vesaa.mytv.ui.screens.leanback.video.player.LeanbackVideoPlayer

/**
 * 播放器状态
 */
@Stable
class LeanbackVideoPlayerState(
    private val instance: LeanbackVideoPlayer,
    private val defaultAspectRatioProvider: () -> Float? = { null },
) {
    /** 当前是否静音（用于分屏仅激活子屏发声） */
    var isMuted by mutableStateOf(false)

    private fun friendlyErrorText(ex: LeanbackVideoPlayer.PlaybackException): String {
        val code = ex.errorCode
        val raw = ex.errorCodeName.trim()
        val upper = raw.uppercase()
        return when {
            code == 3001 -> "源端分片/容器数据异常，请稍后重试或切换线路($code)"
            code == 3002 -> "源端清单异常或分片错误，请稍后重试或切换线路($code)"
            code in 3000..3999 && (upper.contains("AUTH") || upper.contains("401") || upper.contains("403")) ->
                "源鉴权可能过期或被拒绝，请检查订阅/请求头($code)"
            code in 3000..3999 -> "源端抖动或解析异常，请稍后重试或切换线路($code)"
            else -> "$raw($code)"
        }
    }

    /** 视频宽高比 */
    var aspectRatio by mutableFloatStateOf(16f / 9f)

    /** 错误 */
    var error by mutableStateOf<String?>(null)

    /** 元数据 */
    var metadata by mutableStateOf(LeanbackVideoPlayer.Metadata())

    /** 当前正在拉流的地址（含回看/多线路切换后的实际 URL），供 UI 展示 */
    var currentMediaUrl by mutableStateOf("")

    fun prepare(url: String, streamRequestHeaders: String? = null) {
        error = null
        currentMediaUrl = url.trim()
        // 新播放请求前先停掉旧会话，降低旧错误回调串扰到新频道的概率。
        instance.onDeactivate()
        instance.prepare(url, streamRequestHeaders)
    }

    /** 分屏退出或子屏释放时调用，停止当前会话并清空展示态。 */
    fun stop() {
        instance.onDeactivate()
        error = null
        currentMediaUrl = ""
        metadata = LeanbackVideoPlayer.Metadata()
    }

    fun play() {
        instance.play()
    }

    fun pause() {
        instance.pause()
    }

    fun applyMuted(muted: Boolean) {
        isMuted = muted
        instance.setMuted(muted)
    }

    fun setVideoSurfaceView(surfaceView: SurfaceView) {
        instance.setVideoSurfaceView(surfaceView)
    }

    private val onReadyListeners = mutableListOf<() -> Unit>()
    private val onErrorListeners = mutableListOf<() -> Unit>()
    private val onCutoffListeners = mutableListOf<() -> Unit>()

    fun onReady(listener: () -> Unit) {
        onReadyListeners.add(listener)
    }

    fun onError(listener: () -> Unit) {
        onErrorListeners.add(listener)
    }

    fun onCutoff(listener: () -> Unit) {
        onCutoffListeners.add(listener)
    }

    fun initialize() {
        instance.initialize()
        instance.onResolution { width, height ->
            val defaultAspectRatio = defaultAspectRatioProvider()

            if (defaultAspectRatio == null) {
                if (width > 0 && height > 0) aspectRatio = width.toFloat() / height
            } else {
                aspectRatio = defaultAspectRatio
            }
        }
        instance.onError { ex ->
            error = if (ex != null) friendlyErrorText(ex)
            else null

            if (error != null) onErrorListeners.forEach { it.invoke() }

        }
        instance.onReady { onReadyListeners.forEach { it.invoke() } }
        instance.onBuffering { if (it) error = null }
        instance.onPrepared { }
        instance.onMetadata { metadata = it }
        instance.onCutoff { onCutoffListeners.forEach { it.invoke() } }
    }

    fun release() {
        onReadyListeners.clear()
        onErrorListeners.clear()
        instance.release()
    }
}

@Composable
fun rememberLeanbackVideoPlayerState(
    defaultAspectRatioProvider: () -> Float? = { null },
): LeanbackVideoPlayerState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val state = remember {
        LeanbackVideoPlayerState(
            LeanbackMedia3VideoPlayer(context, coroutineScope),
            defaultAspectRatioProvider = defaultAspectRatioProvider,
        )
    }

    DisposableEffect(Unit) {
        state.initialize()

        onDispose {
            state.release()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.play()
            } else if (event == Lifecycle.Event.ON_STOP) {
                state.pause()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return state
}
