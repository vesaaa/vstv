package top.yogiczy.mytv.ui.screens.leanback.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import kotlinx.coroutines.delay
import top.yogiczy.mytv.ui.screens.leanback.components.LeanbackQrcode
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import top.yogiczy.mytv.ui.utils.HttpServer

@Composable
fun LeanbackSettingsCategoryMore(
    modifier: Modifier = Modifier,
) {
    var serverUrl by remember { mutableStateOf(HttpServer.serverUrl()) }

    TvLazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
    ) {
        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "设置页面地址",
                supportingContent = "若手机扫二维码打不开，短按下一项切换本机 IPv4；第一项为自动选择",
                trailingContent = serverUrl,
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "切换扫码用的本机 IP",
                supportingContent = "在自动与检测到的局域网 IPv4 之间循环",
                trailingContent = "短按切换",
                onSelected = {
                    serverUrl = HttpServer.cycleHttpServerAdvertiseIp()
                },
            )
        }

        item {
            var show by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                delay(100)
                show = true
            }

            if (show) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LeanbackQrcode(
                        modifier = Modifier
                            .width(200.dp)
                            .height(200.dp),
                        text = serverUrl,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun LeanbackSettingsMorePreview() {
    LeanbackTheme {
        LeanbackSettingsCategoryMore()
    }
}
