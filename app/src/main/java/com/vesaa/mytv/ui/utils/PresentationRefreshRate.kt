package com.vesaa.mytv.ui.utils

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive

private const val FIRST_WINDOW_MS = 2_000L
private const val NEXT_WINDOW_MS = 5_000L

/**
 * 通过 [withFrameMillis] 估算当前窗口的合成帧率（Hz），与 [LeanbackMonitorScreen] 统计方式一致。
 * 用窗口内多次采样取 **算术平均** 再更新，避免数字频繁跳变：**首次约 [FIRST_WINDOW_MS]ms 出数**，之后每 **[NEXT_WINDOW_MS]ms** 刷新。
 */
@Composable
fun rememberMeasuredPresentationRefreshHz(): Float {
    var hz by remember { mutableFloatStateOf(0f) }
    var fpsCount by remember { mutableIntStateOf(0) }
    var lastFrameMs by remember { mutableLongStateOf(0L) }
    var windowStartRt by remember { mutableLongStateOf(0L) }
    var sampleSum by remember { mutableFloatStateOf(0f) }
    var sampleCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        windowStartRt = SystemClock.elapsedRealtime()
        var firstWindowDone = false
        while (isActive) {
            withFrameMillis { ms ->
                fpsCount++
                if (fpsCount == 5) {
                    if (lastFrameMs > 0L) {
                        val elapsed = ms - lastFrameMs
                        if (elapsed > 0L) {
                            val v = 5_000f / elapsed.toFloat()
                            if (v in 20f..480f) {
                                sampleSum += v
                                sampleCount++
                            }
                        }
                    }
                    lastFrameMs = ms
                    fpsCount = 0
                }
                val nowRt = SystemClock.elapsedRealtime()
                val threshold = if (firstWindowDone) NEXT_WINDOW_MS else FIRST_WINDOW_MS
                if (sampleCount > 0 && nowRt - windowStartRt >= threshold) {
                    val avg = sampleSum / sampleCount.toFloat()
                    hz = avg.roundToInt().toFloat().coerceIn(24f, 360f)
                    sampleSum = 0f
                    sampleCount = 0
                    windowStartRt = nowRt
                    firstWindowDone = true
                }
            }
        }
    }
    return hz
}
