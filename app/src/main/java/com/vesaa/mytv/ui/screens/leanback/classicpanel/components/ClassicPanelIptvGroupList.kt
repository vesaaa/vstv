package com.vesaa.mytv.ui.screens.leanback.classicpanel.components

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.ListItemDefaults
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import com.vesaa.mytv.data.entities.IptvGroup
import com.vesaa.mytv.data.entities.IptvGroupList
import com.vesaa.mytv.ui.theme.LeanbackTheme
import com.vesaa.mytv.ui.utils.handleLeanbackKeyEvents
import kotlin.math.max

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LeanbackClassicPanelIptvGroupList(
    modifier: Modifier = Modifier,
    iptvGroupListProvider: () -> IptvGroupList = { IptvGroupList() },
    initialIptvGroupProvider: () -> IptvGroup = { IptvGroup() },
    exitFocusRequesterProvider: () -> FocusRequester = { FocusRequester.Default },
    onIptvGroupFocused: (IptvGroup) -> Unit = {},
    /** 长按隐藏该分组（「精选频道」无效） */
    onIptvGroupLongPressHide: (IptvGroup) -> Unit = {},
    onIptvGroupLongPressAddToFavorites: (IptvGroup) -> Unit = {},
    onUserAction: () -> Unit = {},
) {
    val iptvGroupList = iptvGroupListProvider()
    val initialIptvGroup = initialIptvGroupProvider()

    val focusRequester = remember { FocusRequester() }
    var focusedIptvGroup by remember { mutableStateOf(initialIptvGroup) }

    val listState = rememberTvLazyListState(max(0, iptvGroupList.indexOf(initialIptvGroup) - 2))

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { _ -> onUserAction() }
    }

    TvLazyColumn(
        state = listState,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .width(140.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background.copy(0.9f))
            .focusRequester(focusRequester)
            .focusProperties {
                exit = {
                    focusRequester.saveFocusedChild()
                    exitFocusRequesterProvider()
                }
                enter = {
                    if (focusRequester.restoreFocusedChild()) FocusRequester.Cancel
                    else FocusRequester.Default
                }
            },
    ) {
        items(iptvGroupList) { iptvGroup ->
            val isSelected by remember { derivedStateOf { iptvGroup == focusedIptvGroup } }

            LeanbackClassicPanelIptvGroupItem(
                iptvGroupProvider = { iptvGroup },
                isSelectedProvider = { isSelected },
                initialFocusedProvider = { iptvGroup == initialIptvGroup },
                onFocused = {
                    focusedIptvGroup = it
                    onIptvGroupFocused(it)
                },
                onLongPressHide = onIptvGroupLongPressHide,
                onLongPressAddToFavorites = onIptvGroupLongPressAddToFavorites,
            )
        }
    }
}

@Composable
private fun LeanbackClassicPanelIptvGroupItem(
    modifier: Modifier = Modifier,
    iptvGroupProvider: () -> IptvGroup = { IptvGroup() },
    isSelectedProvider: () -> Boolean = { false },
    initialFocusedProvider: () -> Boolean = { false },
    onFocused: (IptvGroup) -> Unit = {},
    onLongPressHide: (IptvGroup) -> Unit = {},
    onLongPressAddToFavorites: (IptvGroup) -> Unit = {},
) {
    val iptvGroup = iptvGroupProvider()

    val focusRequester = remember { FocusRequester() }
    var hasFocused by rememberSaveable { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
    var showLongPressMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!hasFocused && initialFocusedProvider()) {
            focusRequester.requestFocus()
        }
        hasFocused = true
    }

    CompositionLocalProvider(
        LocalContentColor provides if (isFocused) MaterialTheme.colorScheme.background
        else MaterialTheme.colorScheme.onBackground
    ) {
        androidx.tv.material3.ListItem(
            modifier = modifier
                .focusRequester(focusRequester)
                .onFocusChanged {
                    isFocused = it.isFocused || it.hasFocus

                    if (isFocused) {
                        onFocused(iptvGroup)
                    }
                }
                .handleLeanbackKeyEvents(
                    pointerTapEnabled = false,
                    onSelect = {
                        focusRequester.requestFocus()
                    },
                    onLongSelect = {
                        val isSpecial = iptvGroup.name == IptvGroup.FAVORITE_GROUP_NAME ||
                            iptvGroup.name == IptvGroup.EXPANDED_GROUP_NAME
                        if (isFocused && !isSpecial) showLongPressMenu = true
                    },
                )
                .pointerInput(iptvGroup) {
                    detectTapGestures(
                        onTap = {
                            focusRequester.requestFocus()
                            onFocused(iptvGroup)
                        },
                        onLongPress = {
                            val isSpecial = iptvGroup.name == IptvGroup.FAVORITE_GROUP_NAME ||
                                iptvGroup.name == IptvGroup.EXPANDED_GROUP_NAME
                            if (!isSpecial) showLongPressMenu = true
                        },
                    )
                },
            colors = ListItemDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.onBackground,
                selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = 0.5f
                ),
            ),
            selected = isSelectedProvider(),
            // 触摸由 pointerInput 处理；保留语义占位
            onClick = {
                focusRequester.requestFocus()
                onFocused(iptvGroup)
            },
            headlineContent = {
                Text(
                    text = iptvGroup.name,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }

    if (showLongPressMenu) {
        GroupActionDialog(
            onDismissRequest = { showLongPressMenu = false },
            onHide = {
                showLongPressMenu = false
                onLongPressHide(iptvGroup)
            },
            onAddToFavorite = {
                showLongPressMenu = false
                onLongPressAddToFavorites(iptvGroup)
            },
        )
    }
}

@Composable
private fun GroupActionDialog(
    onDismissRequest: () -> Unit,
    onHide: () -> Unit,
    onAddToFavorite: () -> Unit,
) {
    val focusHide = remember { FocusRequester() }
    var selectedIdx by remember { mutableStateOf(0) }
    var canActivate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusHide.requestFocus()
        // 避免“长按抬起”被弹窗默认项立即消费导致直接执行隐藏。
        delay(260)
        canActivate = true
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            TvLazyColumn(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .width(240.dp)
                    .handleLeanbackKeyEvents(
                        onKeyTap = mapOf(
                            KeyEvent.KEYCODE_BACK to { onDismissRequest() },
                        ),
                    )
                    .handleLeanbackKeyEvents(
                        onUp = { selectedIdx = 0 },
                        onDown = { selectedIdx = 1 },
                        onSelect = {
                            if (!canActivate) return@handleLeanbackKeyEvents
                            if (selectedIdx == 0) onHide() else onAddToFavorite()
                        },
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    androidx.tv.material3.ListItem(
                        modifier = Modifier.focusRequester(focusHide),
                        selected = selectedIdx == 0,
                        onClick = {
                            if (canActivate) onHide()
                        },
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
                    androidx.tv.material3.ListItem(
                        selected = selectedIdx == 1,
                        onClick = {
                            if (canActivate) onAddToFavorite()
                        },
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

@Preview
@Composable
private fun LeanbackClassicPanelIptvGroupListPreview() {
    LeanbackTheme {
        LeanbackClassicPanelIptvGroupList(
            modifier = Modifier.padding(20.dp),
            iptvGroupListProvider = { IptvGroupList.EXAMPLE },
        )
    }
}