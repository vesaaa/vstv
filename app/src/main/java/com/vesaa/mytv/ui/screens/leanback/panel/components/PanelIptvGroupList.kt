package com.vesaa.mytv.ui.screens.leanback.panel.components

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.window.Dialog
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
                GroupActionDialog(
                    onDismissRequest = { showLongPressMenu = false },
                    onHide = {
                        showLongPressMenu = false
                        onIptvGroupLongPressHide(iptvGroup)
                    },
                    onAddToFavorite = {
                        showLongPressMenu = false
                        onIptvGroupLongPressAddToFavorites(iptvGroup)
                    },
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LeanbackPanelIptvList(
                modifier = if (index == 0) {
                    Modifier.handleLeanbackKeyEvents(
                        pointerTapEnabled = false,
                        onUp = { onToFavorite() },
                    )
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

@Composable
private fun GroupActionDialog(
    onDismissRequest: () -> Unit,
    onHide: () -> Unit,
    onAddToFavorite: () -> Unit,
) {
    val focusHide = remember { FocusRequester() }
    var selectedIdx by remember { mutableStateOf(0) } // 0: 隐藏分组, 1: 添加到精选

    LaunchedEffect(Unit) {
        focusHide.requestFocus()
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                TvLazyColumn(
                    modifier = Modifier
                        .width(240.dp)
                        .handleLeanbackKeyEvents(
                            onKeyTap = mapOf(
                                KeyEvent.KEYCODE_BACK to { onDismissRequest() },
                            ),
                        )
                        .handleLeanbackKeyEvents(
                            onUp = { selectedIdx = 0 },
                            onDown = { selectedIdx = 1 },
                            onSelect = { if (selectedIdx == 0) onHide() else onAddToFavorite() },
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        ListItem(
                            modifier = Modifier.focusRequester(focusHide),
                            selected = selectedIdx == 0,
                            onClick = { onHide() },
                            headlineContent = {
                                Text(
                                    "隐藏分组",
                                    color = if (selectedIdx == 0) MaterialTheme.colorScheme.background
                                    else MaterialTheme.colorScheme.onBackground,
                                )
                            },
                        )
                    }
                    item {
                        ListItem(
                            selected = selectedIdx == 1,
                            onClick = { onAddToFavorite() },
                            headlineContent = {
                                Text(
                                    "添加到精选频道",
                                    color = if (selectedIdx == 1) MaterialTheme.colorScheme.background
                                    else MaterialTheme.colorScheme.onBackground,
                                )
                            },
                        )
                    }
                }
            }
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