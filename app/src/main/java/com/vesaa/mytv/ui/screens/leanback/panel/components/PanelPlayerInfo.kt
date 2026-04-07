package com.vesaa.mytv.ui.screens.leanback.panel.components

import android.net.TrafficStats
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.vesaa.mytv.ui.screens.leanback.video.player.LeanbackVideoPlayer
import com.vesaa.mytv.ui.theme.LeanbackTheme
import java.text.DecimalFormat

@Composable
fun LeanbackPanelPlayerInfo(
    modifier: Modifier = Modifier,
    metadataProvider: () -> LeanbackVideoPlayer.Metadata = { LeanbackVideoPlayer.Metadata() },
) {
    CompositionLocalProvider(
        LocalTextStyle provides MaterialTheme.typography.bodyLarge,
        LocalContentColor provides MaterialTheme.colorScheme.onBackground
    ) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PanelPlayerInfoLatency(latencyMsProvider = { metadataProvider().zapLatencyMs })
            PanelPlayerInfoFps(
                videoFrameRateProvider = { metadataProvider().videoFrameRate },
                videoRenderedFpsProvider = { metadataProvider().videoRenderedFps },
            )
            PanelPlayerInfoNetSpeed()
        }
    }
}

@Composable
private fun PanelPlayerInfoLatency(
    modifier: Modifier = Modifier,
    latencyMsProvider: () -> Long? = { null },
) {
    val latencyMs = latencyMsProvider()
    Text(
        text = if (latencyMs != null && latencyMs >= 0) "延迟：${latencyMs} ms" else "延迟：N/A（切台后显示）",
        modifier = modifier,
    )
}

@Composable
private fun PanelPlayerInfoFps(
    modifier: Modifier = Modifier,
    videoFrameRateProvider: () -> Float = { 0f },
    videoRenderedFpsProvider: () -> Float = { 0f },
) {
    val view = LocalView.current
    val declaredFps = videoFrameRateProvider()
    val renderedFps = videoRenderedFpsProvider()
    val effectiveVideoFps = when {
        declaredFps > 0.05f -> declaredFps
        renderedFps > 0.05f -> renderedFps
        else -> 0f
    }
    val videoFpsText = if (effectiveVideoFps > 0.05f) effectiveVideoFps.toInt().toString() else "N/A"
    val display = view.display
    val activeRefreshRate = display?.refreshRate ?: 0f
    val maxSupportedRefreshRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        display?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 0f
    } else {
        0f
    }
    val activeFpsText = if (activeRefreshRate > 1f) activeRefreshRate.toInt().toString() else "N/A"
    val maxFpsText = if (maxSupportedRefreshRate > 1f) maxSupportedRefreshRate.toInt().toString() else ""
    val deviceFpsText = if (maxFpsText.isNotEmpty() && maxSupportedRefreshRate - activeRefreshRate > 1f) {
        "$activeFpsText($maxFpsText)"
    } else {
        activeFpsText
    }

    Text(
        text = "帧率：$videoFpsText/$deviceFpsText FPS",
        modifier = modifier,
    )
}

@Composable
private fun PanelPlayerInfoNetSpeed(
    modifier: Modifier = Modifier,
    netSpeed: Long = rememberNetSpeed(),
) {
    Text(
        text = if (netSpeed < 1024 * 999) "网速：${netSpeed / 1024}KB/s"
        else "网速：${DecimalFormat("#.#").format(netSpeed / 1024 / 1024f)}MB/s",
        modifier = modifier,
    )
}

@Composable
private fun rememberNetSpeed(): Long {
    var netSpeed by remember { mutableLongStateOf(0) }

    LaunchedEffect(Unit) {
        var lastTotalRxBytes = TrafficStats.getTotalRxBytes()
        var lastTimeStamp = System.currentTimeMillis()

        while (true) {
            delay(1000)
            val nowTotalRxBytes = TrafficStats.getTotalRxBytes()
            val nowTimeStamp = System.currentTimeMillis()
            val speed = (nowTotalRxBytes - lastTotalRxBytes) / (nowTimeStamp - lastTimeStamp) * 1000
            lastTimeStamp = nowTimeStamp
            lastTotalRxBytes = nowTotalRxBytes

            netSpeed = speed
        }
    }

    return netSpeed
}

@Preview
@Composable
private fun LeanbackPanelPlayerInfoPreview() {
    LeanbackTheme {
        LeanbackPanelPlayerInfo(
            metadataProvider = {
                LeanbackVideoPlayer.Metadata(
                    videoWidth = 1920,
                    videoHeight = 1080,
                    videoFrameRate = 59.94f,
                )
            },
        )
    }
}

@Preview
@Composable
private fun LeanbackPanelPlayerInfoNetSpeedPreview() {
    LeanbackTheme {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PanelPlayerInfoNetSpeed()
            PanelPlayerInfoNetSpeed(netSpeed = 54321)
            PanelPlayerInfoNetSpeed(netSpeed = 1222 * 1222)
        }
    }
}