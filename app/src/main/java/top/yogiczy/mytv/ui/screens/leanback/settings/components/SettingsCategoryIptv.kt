package top.yogiczy.mytv.ui.screens.leanback.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyListState
import androidx.tv.foundation.lazy.list.items
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import top.yogiczy.mytv.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.ui.screens.leanback.components.LeanbackQrcodeDialog
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import top.yogiczy.mytv.ui.utils.HttpServer
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.ui.utils.WebPushConfigNotifier
import top.yogiczy.mytv.ui.utils.handleLeanbackKeyEvents
import top.yogiczy.mytv.utils.humanizeMs
import kotlin.math.max

@Composable
fun LeanbackSettingsCategoryIptv(
    modifier: Modifier = Modifier,
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
) {
    val coroutineScope = rememberCoroutineScope()

    TvLazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
    ) {
        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "数字选台",
                supportingContent = "通过数字选择频道",
                trailingContent = {
                    Switch(
                        checked = settingsViewModel.iptvChannelNoSelectEnable,
                        onCheckedChange = null
                    )
                },
                onSelected = {
                    settingsViewModel.iptvChannelNoSelectEnable =
                        !settingsViewModel.iptvChannelNoSelectEnable
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "换台反转",
                supportingContent = if (settingsViewModel.iptvChannelChangeFlip) "方向键上：下一个频道；方向键下：上一个频道"
                else "方向键上：上一个频道；方向键下：下一个频道",
                trailingContent = {
                    Switch(
                        checked = settingsViewModel.iptvChannelChangeFlip,
                        onCheckedChange = null
                    )
                },
                onSelected = {
                    settingsViewModel.iptvChannelChangeFlip =
                        !settingsViewModel.iptvChannelChangeFlip
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "直播源精简",
                supportingContent = if (settingsViewModel.iptvSourceSimplify) "显示精简直播源(仅央视、地方卫视)" else "显示完整直播源",
                trailingContent = {
                    Switch(checked = settingsViewModel.iptvSourceSimplify, onCheckedChange = null)
                },
                onSelected = {
                    settingsViewModel.iptvSourceSimplify = !settingsViewModel.iptvSourceSimplify
                    WebPushConfigNotifier.notifyConfigMayHaveChanged()
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "直播源缓存时间",
                supportingContent = "短按增加1小时，长按设为0小时",
                trailingContent = settingsViewModel.iptvSourceCacheTime.humanizeMs(),
                onSelected = {
                    settingsViewModel.iptvSourceCacheTime =
                        (settingsViewModel.iptvSourceCacheTime + 1 * 1000 * 60 * 60) % (1000 * 60 * 60 * 24)
                    WebPushConfigNotifier.notifyConfigMayHaveChanged()
                },
                onLongSelected = {
                    settingsViewModel.iptvSourceCacheTime = 0
                    WebPushConfigNotifier.notifyConfigMayHaveChanged()
                },
            )
        }

        item {
            var showDialog by remember { mutableStateOf(false) }

            LeanbackSettingsCategoryListItem(
                headlineContent = "直播源与默认",
                supportingContent = if (settingsViewModel.iptvSourceUrl.isNotBlank()) {
                    settingsViewModel.iptvSourceUrl
                } else {
                    "未设置默认：请扫码或网页推送；多源时在列表中选「当前默认」"
                },
                trailingContent = if (settingsViewModel.iptvSourceUrl.isNotBlank()) "当前默认" else "未设置",
                onSelected = { showDialog = true },
                remoteConfig = true,
            )

            LeanbackSettingsIptvSourceHistoryDialog(showDialogProvider = { showDialog },
                onDismissRequest = { showDialog = false },
                iptvSourceHistoryProvider = {
                    settingsViewModel.iptvSourceUrlHistoryList.filter { it.isNotBlank() }.sorted()
                        .toImmutableList()
                },
                currentIptvSourceProvider = { settingsViewModel.iptvSourceUrl },
                onSelected = {
                    showDialog = false
                    if (settingsViewModel.iptvSourceUrl != it) {
                        settingsViewModel.iptvSourceUrl = it
                        settingsViewModel.iptvSourceRequestHeaders =
                            if (it.isBlank()) "" else SP.getIptvSourceHeadersForUrl(it)
                        coroutineScope.launch { IptvRepository().clearCache() }
                        WebPushConfigNotifier.notifyConfigMayHaveChanged()
                    }
                },
                onDeleted = {
                    settingsViewModel.iptvSourceUrlHistoryList -= it
                })
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "清除缓存",
                supportingContent = "短按清除直播源缓存文件、可播放域名列表",
                onSelected = {
                    settingsViewModel.iptvPlayableHostList = emptySet()
                    coroutineScope.launch { IptvRepository().clearCache() }
                    WebPushConfigNotifier.notifyConfigMayHaveChanged()
                    LeanbackToastState.I.showToast("清除缓存成功")
                },
            )
        }
    }
}

@Composable
private fun LeanbackSettingsIptvSourceHistoryDialog(
    modifier: Modifier = Modifier,
    showDialogProvider: () -> Boolean = { false },
    onDismissRequest: () -> Unit = {},
    iptvSourceHistoryProvider: () -> ImmutableList<String> = { persistentListOf() },
    currentIptvSourceProvider: () -> String = { "" },
    onSelected: (String) -> Unit = {},
    onDeleted: (String) -> Unit = {},
) {
    val iptvSourceHistory = listOf("") + iptvSourceHistoryProvider()
    val currentIptvSource = currentIptvSourceProvider()

    if (showDialogProvider()) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = modifier,
            onDismissRequest = onDismissRequest,
            confirmButton = { Text(text = "短按设为当前默认；长按从列表删除") },
            title = { Text("选择默认直播源") },
            text = {
                var hasFocused by remember { mutableStateOf(false) }

                TvLazyColumn(
                    state = TvLazyListState(
                        max(0, iptvSourceHistory.indexOf(currentIptvSource) - 2),
                    ),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(iptvSourceHistory) { source ->
                        val focusRequester = remember { FocusRequester() }
                        var isFocused by remember { mutableStateOf(false) }

                        LaunchedEffect(Unit) {
                            if (source == currentIptvSource && !hasFocused) {
                                hasFocused = true
                                focusRequester.requestFocus()
                            }
                        }

                        androidx.tv.material3.ListItem(
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                                .handleLeanbackKeyEvents(
                                    onSelect = {
                                        if (isFocused) onSelected(source)
                                        else focusRequester.requestFocus()
                                    },
                                    onLongSelect = {
                                        if (isFocused && source.isNotBlank()) onDeleted(source)
                                        else focusRequester.requestFocus()
                                    },
                                ),
                            selected = currentIptvSource == source,
                            onClick = { },
                            headlineContent = {
                                androidx.tv.material3.Text(
                                    text = if (source.isBlank()) {
                                        "无（清除当前默认订阅）"
                                    } else {
                                        source
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = if (isFocused) Int.MAX_VALUE else 2,
                                )
                            },
                            trailingContent = {
                                if (currentIptvSource == source) {
                                    androidx.tv.material3.Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "checked",
                                    )
                                }
                            },
                        )
                    }

                    item {
                        val focusRequester = remember { FocusRequester() }
                        var isFocused by remember { mutableStateOf(false) }
                        var showDialog by remember { mutableStateOf(false) }

                        androidx.tv.material3.ListItem(
                            modifier = Modifier
                                .focusRequester(focusRequester)
                                .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                                .handleLeanbackKeyEvents(
                                    onSelect = {
                                        if (isFocused) showDialog = true
                                        else focusRequester.requestFocus()
                                    },
                                ),
                            selected = false,
                            onClick = {},
                            headlineContent = {
                                androidx.tv.material3.Text("添加其他直播源")
                            },
                        )

                        LeanbackQrcodeDialog(
                            text = HttpServer.serverUrl(),
                            description = "扫码前往设置页面",
                            showDialogProvider = { showDialog },
                            onDismissRequest = { showDialog = false },
                        )
                    }
                }
            },
        )
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryIptvPreview() {
    SP.init(LocalContext.current)
    LeanbackTheme {
        LeanbackSettingsCategoryIptv(
            modifier = Modifier.padding(20.dp),
            settingsViewModel = LeanbackSettingsViewModel().apply {
                iptvSourceCacheTime = 3_600_000
                iptvSourceUrl = "https://iptv-org.github.io/iptv/iptv.m3u"
                iptvSourceUrlHistoryList = setOf(
                    "https://iptv-org.github.io/iptv/iptv.m3u",
                    "https://iptv-org.github.io/iptv/iptv2.m3u",
                    "https://iptv-org.github.io/iptv/iptv3.m3u",
                )
            },
        )
    }
}