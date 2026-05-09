package com.vesaa.mytv.ui.screens.leanback.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import com.vesaa.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import com.vesaa.mytv.ui.theme.LeanbackTheme
import com.vesaa.mytv.ui.utils.SP
import com.vesaa.mytv.utils.humanizeMs
import kotlin.math.max

@Composable
fun LeanbackSettingsCategoryVideoPlayer(
    modifier: Modifier = Modifier,
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
) {
    TvLazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
    ) {
        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "全局画面比例",
                trailingContent = when (settingsViewModel.videoPlayerAspectRatio) {
                    SP.VideoPlayerAspectRatio.ORIGINAL -> "原始"
                    SP.VideoPlayerAspectRatio.SIXTEEN_NINE -> "16:9"
                    SP.VideoPlayerAspectRatio.FOUR_THREE -> "4:3"
                    SP.VideoPlayerAspectRatio.AUTO -> "自动拉伸"
                },
                onSelected = {
                    settingsViewModel.videoPlayerAspectRatio =
                        SP.VideoPlayerAspectRatio.entries.let {
                            it[(it.indexOf(settingsViewModel.videoPlayerAspectRatio) + 1) % it.size]
                        }
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "显示 FPS",
                supportingContent = "在屏幕左上角显示 fps 与柱状图",
                trailingContent = {
                    Switch(checked = settingsViewModel.debugShowFps, onCheckedChange = null)
                },
                onSelected = {
                    settingsViewModel.debugShowFps = !settingsViewModel.debugShowFps
                },
            )
        }

        item {
            val min = 1000 * 5L
            val max = 1000 * 30L
            val step = 1000 * 5L

            LeanbackSettingsCategoryListItem(
                headlineContent = "播放器加载超时",
                supportingContent = "影响超时换源、断线重连",
                trailingContent = settingsViewModel.videoPlayerLoadTimeout.humanizeMs(),
                onSelected = {
                    settingsViewModel.videoPlayerLoadTimeout =
                        max(min, (settingsViewModel.videoPlayerLoadTimeout + step) % (max + step))
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "RTSP 优先 TCP（Interleaved）",
                supportingContent = "默认开启，穿越 NAT/防火墙更稳后再按需回退 RTP/UDP",
                trailingContent = {
                    Switch(checked = settingsViewModel.videoRtspForceTcp, onCheckedChange = null)
                },
                onSelected = {
                    settingsViewModel.videoRtspForceTcp = !settingsViewModel.videoRtspForceTcp
                },
            )
        }

        item {
            val step = 5000L
            val minTo = 10_000L
            val maxTo = 60_000L
            LeanbackSettingsCategoryListItem(
                headlineContent = "RTSP 超时容忍",
                supportingContent = "对应 Media3 收流静默判定，过小易误断；SDP/握手慢时可加大",
                trailingContent = settingsViewModel.videoRtspRtpSilenceTimeoutMs.humanizeMs(),
                onSelected = {
                    val v = settingsViewModel.videoRtspRtpSilenceTimeoutMs + step
                    settingsViewModel.videoRtspRtpSilenceTimeoutMs =
                        if (v > maxTo) minTo else v.coerceIn(minTo, maxTo)
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "RTSP TCP 起播重试",
                supportingContent = "仍为 TCP/interleaved 时同址重试次数，之后才尝试 RTP/UDP（${settingsViewModel.videoRtspPrepareRetryDelayMs.humanizeMs()} 间隔）",
                trailingContent = "${settingsViewModel.videoRtspTcpPrepareRetryCount} 次",
                onSelected = {
                    settingsViewModel.videoRtspTcpPrepareRetryCount =
                        (settingsViewModel.videoRtspTcpPrepareRetryCount + 1) % 6
                },
            )
        }

        item {
            val stepDelay = 200L
            val minDelay = 400L
            val maxDelay = 4000L
            LeanbackSettingsCategoryListItem(
                headlineContent = "RTSP 重试间隔",
                supportingContent = "TCP 起播失败后等待再拉同一地址",
                trailingContent = settingsViewModel.videoRtspPrepareRetryDelayMs.humanizeMs(),
                onSelected = {
                    val v = settingsViewModel.videoRtspPrepareRetryDelayMs + stepDelay
                    settingsViewModel.videoRtspPrepareRetryDelayMs =
                        if (v > maxDelay) minDelay else v.coerceIn(minDelay, maxDelay)
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "播放链路诊断日志",
                supportingContent = "Logcat 筛选标签：VsTVPlayback（不写完整频道 URL）",
                trailingContent = {
                    Switch(checked = settingsViewModel.playbackTraceLogcatEnabled, onCheckedChange = null)
                },
                onSelected = {
                    settingsViewModel.playbackTraceLogcatEnabled =
                        !settingsViewModel.playbackTraceLogcatEnabled
                },
            )
        }
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryVideoPlayerPreview() {
    SP.init(LocalContext.current)
    LeanbackTheme {
        LeanbackSettingsCategoryVideoPlayer(
            modifier = Modifier.padding(20.dp),
        )
    }
}
