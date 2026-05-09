package com.vesaa.mytv.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.isActive

/**
 * 通过 [withFrameMillis] 估算当前窗口的实际合成帧率（Hz），与 [LeanbackMonitorScreen] 原理一致。
 * 部分电视/盒子仍把 [android.view.Display.getRefreshRate] / supportedModes 报成 60，但界面实际以 120Hz 呈现；
 * 将该值与 Display API 合并可让「显示器 FPS」更贴近体感与调试叠加层。
 */
@Composable
fun rememberMeasuredPresentationRefreshHz(): Float {
    var hz by remember { mutableFloatStateOf(0f) }
    var fpsCount by remember { mutableIntStateOf(0) }
    var lastUpdate by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { ms ->
                fpsCount++
                if (fpsCount == 5) {
                    if (lastUpdate > 0L) {
                        val elapsed = ms - lastUpdate
                        if (elapsed > 0L) {
                            val v = 5_000f / elapsed.toFloat()
                            if (v in 20f..480f) hz = v
                        }
                    }
                    lastUpdate = ms
                    fpsCount = 0
                }
            }
        }
    }
    return hz
}
