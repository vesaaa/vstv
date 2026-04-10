package com.vesaa.mytv.ui.screens.leanback.settings.components

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.vesaa.mytv.data.repositories.iptv.IptvRepository
import com.vesaa.mytv.ui.screens.leanback.components.LeanbackQrcodeDialog
import com.vesaa.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import com.vesaa.mytv.ui.screens.leanback.toast.LeanbackToastState
import com.vesaa.mytv.ui.theme.LeanbackTheme
import com.vesaa.mytv.ui.utils.HttpServer
import com.vesaa.mytv.ui.utils.SP
import com.vesaa.mytv.ui.utils.WebPushConfigNotifier
import com.vesaa.mytv.ui.utils.handleLeanbackKeyEvents
import com.vesaa.mytv.utils.humanizeMs
import com.vesaa.mytv.utils.userAgentValueFromHeadersText
import kotlin.math.max

@Composable
fun LeanbackSettingsCategoryIptv(
    modifier: Modifier = Modifier,
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
) {
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        settingsViewModel.reloadWebPushedStreamingConfigFromDisk()
        WebPushConfigNotifier.updates.collect {
            settingsViewModel.reloadWebPushedStreamingConfigFromDisk()
        }
    }

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
                    buildString {
                        append(
                            if (settingsViewModel.iptvSourceUrl.trim() == SP.IPTV_LOCAL_SOURCE_URL) {
                                "本地上传（网页推送的 M3U/TXT）"
                            } else {
                                settingsViewModel.iptvSourceUrl
                            },
                        )
                        append("\n")
                        val subRaw = settingsViewModel.iptvSourceRequestHeaders.ifBlank {
                            SP.getIptvSourceHeadersForUrl(settingsViewModel.iptvSourceUrl)
                        }
                        val subUa = userAgentValueFromHeadersText(subRaw)
                        append("拉取订阅 User-Agent：")
                        append(if (subUa.isBlank()) "（未配置）" else subUa)
                        append("\n")
                        val chRaw = settingsViewModel.iptvChannelRequestHeaders.ifBlank { subRaw }
                        val chUa = userAgentValueFromHeadersText(chRaw)
                        append("播放频道 User-Agent：")
                        append(if (chUa.isBlank()) "（未配置）" else chUa)
                    }
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
                currentIptvRequestHeadersProvider = { settingsViewModel.iptvSourceRequestHeaders },
                currentIptvChannelRequestHeadersProvider = { settingsViewModel.iptvChannelRequestHeaders },
                onSelected = {
                    showDialog = false
                    if (it.trim().startsWith(SP.IPTV_LOCAL_SOURCE_URL) && !SP.hasIptvLocalUploadFile()) {
                        LeanbackToastState.I.showToast("本地上传文件不存在，请先在网页管理端重新上传")
                        return@LeanbackSettingsIptvSourceHistoryDialog
                    }
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
    currentIptvRequestHeadersProvider: () -> String = { "" },
    currentIptvChannelRequestHeadersProvider: () -> String = { "" },
    onSelected: (String) -> Unit = {},
    onDeleted: (String) -> Unit = {},
) {
    val iptvSourceHistory = listOf("") + iptvSourceHistoryProvider()
    val currentIptvSource = currentIptvSourceProvider()
    val currentIptvRequestHeaders = currentIptvRequestHeadersProvider()
    val currentIptvChannelRequestHeaders = currentIptvChannelRequestHeadersProvider()
    val globalPlaybackUaDisplay = {
        val chRaw = currentIptvChannelRequestHeaders.ifBlank { currentIptvRequestHeaders }
        userAgentValueFromHeadersText(chRaw).let { v -> if (v.isBlank()) "（未配置）" else v }
    }

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

                        val headersText = when {
                            source.isBlank() -> ""
                            source == currentIptvSource -> {
                                currentIptvRequestHeaders.ifBlank { SP.getIptvSourceHeadersForUrl(source) }
                            }
                            else -> SP.getIptvSourceHeadersForUrl(source)
                        }
                        val subUaDisplay = userAgentValueFromHeadersText(headersText)
                            .let { v -> if (v.isBlank()) "（未配置）" else v }

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
                                    pointerTapEnabled = false,
                                    onSelect = {
                                        if (isFocused) onSelected(source)
                                        else focusRequester.requestFocus()
                                    },
                                    onLongSelect = {
                                        if (isFocused && source.isNotBlank()) onDeleted(source)
                                        else focusRequester.requestFocus()
                                    },
                                )
                                .pointerInput(source) {
                                    detectTapGestures(
                                        onTap = {
                                            focusRequester.requestFocus()
                                            onSelected(source)
                                        },
                                        onLongPress = {
                                            if (source.isNotBlank()) onDeleted(source)
                                        },
                                    )
                                },
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
                            supportingContent = {
                                androidx.tv.material3.Text(
                                    text = if (source.isBlank()) {
                                        "拉取订阅 User-Agent：（无订阅，不适用）\n播放频道 User-Agent：${
                                            globalPlaybackUaDisplay()
                                        }"
                                    } else {
                                        "拉取订阅 User-Agent：$subUaDisplay\n播放频道 User-Agent：${globalPlaybackUaDisplay()}"
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = if (isFocused) Int.MAX_VALUE else 4,
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
                                    pointerTapEnabled = false,
                                    onSelect = {
                                        if (isFocused) showDialog = true
                                        else focusRequester.requestFocus()
                                    },
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            focusRequester.requestFocus()
                                            showDialog = true
                                        },
                                    )
                                },
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