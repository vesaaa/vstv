package top.yogiczy.mytv.ui.screens.leanback.settings.components

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
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.utils.humanizeMs

@Composable
fun LeanbackSettingsCategoryNetwork(
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
                headlineContent = "HTTP 请求重试次数",
                supportingContent = "影响直播源、节目单等数据拉取",
                trailingContent = Constants.HTTP_RETRY_COUNT.toString(),
                locK = true,
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "HTTP 请求重试间隔",
                supportingContent = "影响直播源、节目单等数据拉取",
                trailingContent = Constants.HTTP_RETRY_INTERVAL.humanizeMs(),
                locK = true,
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
            LeanbackSettingsCategoryListItem(
                headlineContent = "显示播放器信息",
                supportingContent = "显示编码、解码器、采样率等",
                trailingContent = {
                    Switch(
                        checked = settingsViewModel.debugShowVideoPlayerMetadata,
                        onCheckedChange = null,
                    )
                },
                onSelected = {
                    settingsViewModel.debugShowVideoPlayerMetadata =
                        !settingsViewModel.debugShowVideoPlayerMetadata
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "应用调试日志",
                supportingContent = "开启后记录 HTTP 请求地址、请求头与响应状态及体长（不含正文）；默认关闭",
                trailingContent = {
                    Switch(checked = settingsViewModel.debugAppLog, onCheckedChange = null)
                },
                onSelected = {
                    settingsViewModel.debugAppLog = !settingsViewModel.debugAppLog
                },
            )
        }
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryNetworkPreview() {
    SP.init(LocalContext.current)
    LeanbackTheme {
        LeanbackSettingsCategoryNetwork(modifier = Modifier.padding(20.dp))
    }
}
