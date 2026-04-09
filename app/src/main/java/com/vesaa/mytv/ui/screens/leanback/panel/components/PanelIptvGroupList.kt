package com.vesaa.mytv.ui.screens.leanback.panel.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import kotlinx.coroutines.flow.distinctUntilChanged
import com.vesaa.mytv.data.entities.EpgList
import com.vesaa.mytv.data.entities.Iptv
import com.vesaa.mytv.data.entities.IptvGroup
import com.vesaa.mytv.data.entities.IptvGroupList
import com.vesaa.mytv.data.entities.IptvGroupList.Companion.iptvGroupIdx
import com.vesaa.mytv.ui.rememberLeanbackChildPadding
import com.vesaa.mytv.ui.theme.LeanbackTheme
import com.vesaa.mytv.ui.utils.handleLeanbackKeyEvents
import kotlin.math.max

@Composable
fun LeanbackPanelIptvGroupList(
    modifier: Modifier = Modifier,
    iptvGroupListProvider: () -> IptvGroupList = { IptvGroupList() },
    epgListProvider: () -> EpgList = { EpgList() },
    currentIptvProvider: () -> Iptv = { Iptv() },
    showProgrammeProgressProvider: () -> Boolean = { false },
    onIptvSelected: (Iptv) -> Unit = {},
    onIptvFavoriteToggle: (Iptv) -> Unit = {},
    onToFavorite: () -> Unit = {},
    onIptvGroupLongPressHide: (IptvGroup) -> Unit = {},
    onIptvGroupLongPressAddToFavorites: (IptvGroup) -> Unit = {},
    onUserAction: () -> Unit = {},
) {
    val iptvGroupList = iptvGroupListProvider()

    val listState =
        rememberTvLazyListState(max(0, iptvGroupList.iptvGroupIdx(currentIptvProvider())))
    val childPadding = rememberLeanbackChildPadding()

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { _ -> onUserAction() }
    }

    TvLazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = childPadding.bottom),
    ) {
        itemsIndexed(iptvGroupList) { index, iptvGroup ->
            val headerFocusRequester = remember(iptvGroup.name, index) { FocusRequester() }
            var headerFocused by remember { mutableStateOf(false) }
            var showLongPressMenu by remember { mutableStateOf(false) }
            ListItem(
                modifier = Modifier
                    .padding(start = childPadding.start)
                    .focusRequester(headerFocusRequester)
                    .onFocusChanged { headerFocused = it.isFocused || it.hasFocus }
                    .handleLeanbackKeyEvents(
                        onLongSelect = {
                            val isSpecial = iptvGroup.name == IptvGroup.FAVORITE_GROUP_NAME ||
                                iptvGroup.name == IptvGroup.EXPANDED_GROUP_NAME
                            if (headerFocused && !isSpecial) showLongPressMenu = true
                        },
                    ),
                colors = ListItemDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.onBackground,
                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f,
                    ),
                ),
                selected = false,
                onClick = {},
                headlineContent = {
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.labelMedium,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = iptvGroup.name)
                            Text(
                                text = "${iptvGroup.iptvList.size}个频道",
                                color = LocalContentColor.current.copy(alpha = 0.8f),
                            )
                        }
                    }
                },
            )

            if (showLongPressMenu) {
                AlertDialog(
                    onDismissRequest = { showLongPressMenu = false },
                    title = { Text("分组操作：${iptvGroup.name}") },
                    text = { Text("请选择要执行的操作") },
                    confirmButton = {
                        TextButton(onClick = {
                            showLongPressMenu = false
                            onIptvGroupLongPressHide(iptvGroup)
                        }) { Text("隐藏分组") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showLongPressMenu = false
                            onIptvGroupLongPressAddToFavorites(iptvGroup)
                        }) { Text("添加到精选频道") }
                    },
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LeanbackPanelIptvList(
                modifier = if (index == 0) {
                    Modifier.handleLeanbackKeyEvents(onUp = { onToFavorite() })
                } else Modifier,
                iptvListProvider = { iptvGroup.iptvList },
                epgListProvider = epgListProvider,
                currentIptvProvider = currentIptvProvider,
                showProgrammeProgressProvider = showProgrammeProgressProvider,
                onIptvSelected = onIptvSelected,
                onIptvFavoriteToggle = onIptvFavoriteToggle,
                onUserAction = onUserAction,
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun LeanbackPanelIptvGroupListPreview() {
    LeanbackTheme {
        Box(modifier = Modifier.height(150.dp)) {
            LeanbackPanelIptvGroupList(
                iptvGroupListProvider = { IptvGroupList.EXAMPLE },
                currentIptvProvider = { Iptv.EXAMPLE },
            )
        }
    }
}