package com.vesaa.mytv.ui.screens.leanback.main.components

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.vesaa.mytv.ui.screens.leanback.quickpanel.QuickPanelSplitMode
import com.vesaa.mytv.ui.screens.leanback.video.LeanbackVideoPlayerState
import com.vesaa.mytv.ui.screens.leanback.video.LeanbackVideoScreen
import com.vesaa.mytv.ui.utils.handleLeanbackDragGestures
import com.vesaa.mytv.ui.utils.handleLeanbackKeyEvents

private val SplitPaneShape = RoundedCornerShape(6.dp)

@Composable
internal fun LeanbackSplitPlaybackScreen(
    mode: QuickPanelSplitMode,
    paneStates: List<LeanbackVideoPlayerState>,
    focusedPane: Int,
    activePane: Int,
    onFocusedPaneChange: (Int) -> Unit,
    onActivePaneChange: (Int) -> Unit,
    onOpenQuickPanelFromSafeArea: () -> Unit,
    onOpenChannelPanelForFocused: () -> Unit,
    onChannelUp: () -> Unit,
    onChannelDown: () -> Unit,
    onLineLeft: () -> Unit,
    onLineRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paneCount = when (mode) {
        QuickPanelSplitMode.LeftRight -> 2
        QuickPanelSplitMode.FourGrid -> 4
        QuickPanelSplitMode.Off -> 1
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent.keyCode
                when {
                    native == KeyEvent.KEYCODE_CHANNEL_UP -> true
                    native == KeyEvent.KEYCODE_CHANNEL_DOWN -> true
                    native == KeyEvent.KEYCODE_DPAD_LEFT ||
                        native == KeyEvent.KEYCODE_DPAD_RIGHT ||
                        native == KeyEvent.KEYCODE_DPAD_UP ||
                        native == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        val nextFocused = when {
                            paneCount == 2 && native == KeyEvent.KEYCODE_DPAD_LEFT -> 0
                            paneCount == 2 && native == KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                            paneCount == 4 && native == KeyEvent.KEYCODE_DPAD_LEFT -> {
                                when (focusedPane) {
                                    1 -> 0
                                    3 -> 2
                                    else -> focusedPane
                                }
                            }
                            paneCount == 4 && native == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                when (focusedPane) {
                                    0 -> 1
                                    2 -> 3
                                    else -> focusedPane
                                }
                            }
                            paneCount == 4 && native == KeyEvent.KEYCODE_DPAD_UP -> {
                                when (focusedPane) {
                                    2 -> 0
                                    3 -> 1
                                    else -> focusedPane
                                }
                            }
                            paneCount == 4 && native == KeyEvent.KEYCODE_DPAD_DOWN -> {
                                when (focusedPane) {
                                    0 -> 2
                                    1 -> 3
                                    else -> focusedPane
                                }
                            }
                            else -> focusedPane
                        }
                        if (nextFocused != focusedPane) {
                            onFocusedPaneChange(nextFocused)
                        }
                        // 分屏内方向键仅用于子屏选择：即使边界无变化也消费，避免触发其它动作。
                        true
                    }
                    else -> {
                        false
                    }
                }
            }
            .handleLeanbackKeyEvents(
                pointerTapEnabled = false,
                onSelect = { onOpenChannelPanelForFocused() },
                onLongSelect = {},
                onSettings = onOpenQuickPanelFromSafeArea,
                onLongDown = onOpenQuickPanelFromSafeArea,
            )
            .handleLeanbackDragGestures(
                onSwipeUp = {},
                onSwipeDown = {},
                onSwipeLeft = onLineRight,
                onSwipeRight = onLineLeft,
            ),
    ) {
        if (paneCount == 2) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                LeanbackSplitPane(
                    state = paneStates[0],
                    index = 0,
                    paneCount = paneCount,
                    focused = focusedPane == 0,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    onFocus = { onFocusedPaneChange(0) },
                    onSelectFocusedPane = { onOpenChannelPanelForFocused() },
                )
                LeanbackSplitPane(
                    state = paneStates[1],
                    index = 1,
                    paneCount = paneCount,
                    focused = focusedPane == 1,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    onFocus = { onFocusedPaneChange(1) },
                    onSelectFocusedPane = { onOpenChannelPanelForFocused() },
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    LeanbackSplitPane(
                        state = paneStates[0],
                        index = 0,
                        paneCount = paneCount,
                        focused = focusedPane == 0,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        onFocus = { onFocusedPaneChange(0) },
                        onSelectFocusedPane = { onOpenChannelPanelForFocused() },
                    )
                    LeanbackSplitPane(
                        state = paneStates[1],
                        index = 1,
                        paneCount = paneCount,
                        focused = focusedPane == 1,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        onFocus = { onFocusedPaneChange(1) },
                        onSelectFocusedPane = { onOpenChannelPanelForFocused() },
                    )
                }
                Row(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    LeanbackSplitPane(
                        state = paneStates[2],
                        index = 2,
                        paneCount = paneCount,
                        focused = focusedPane == 2,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        onFocus = { onFocusedPaneChange(2) },
                        onSelectFocusedPane = { onOpenChannelPanelForFocused() },
                    )
                    LeanbackSplitPane(
                        state = paneStates[3],
                        index = 3,
                        paneCount = paneCount,
                        focused = focusedPane == 3,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        onFocus = { onFocusedPaneChange(3) },
                        onSelectFocusedPane = { onOpenChannelPanelForFocused() },
                    )
                }
            }
        }
        // 分屏铺满全屏后，保留上下窄条作为“非格子安全区”：长按打开快捷面板。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .align(Alignment.TopCenter)
                .pointerInput(mode) {
                    detectTapGestures(
                        onLongPress = { onOpenQuickPanelFromSafeArea() },
                        onDoubleTap = { onOpenQuickPanelFromSafeArea() },
                    )
                },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .align(Alignment.BottomCenter)
                .pointerInput(mode) {
                    detectTapGestures(
                        onLongPress = { onOpenQuickPanelFromSafeArea() },
                        onDoubleTap = { onOpenQuickPanelFromSafeArea() },
                    )
                },
        )
    }
}

@Composable
private fun LeanbackSplitPane(
    state: LeanbackVideoPlayerState,
    index: Int,
    paneCount: Int,
    focused: Boolean,
    onFocus: () -> Unit,
    onSelectFocusedPane: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.15f), SplitPaneShape)
            .pointerInput(index) {
                detectTapGestures(
                    onTap = {
                        if (focused) onSelectFocusedPane()
                        else onFocus()
                    },
                    onLongPress = {
                        onFocus()
                    },
                )
            },
    ) {
        LeanbackVideoScreen(
            state = state,
            showMetadataProvider = { false },
            useTextureView = true,
            modifier = Modifier.fillMaxSize(),
        )
        val statusLabel = when {
            focused -> "选中"
            else -> ""
        }
        if (statusLabel.isNotEmpty()) {
            val statusAlign = when {
                paneCount == 2 && index == 0 -> Alignment.TopEnd
                paneCount == 2 && index == 1 -> Alignment.TopStart
                paneCount == 4 && index == 0 -> Alignment.BottomEnd
                paneCount == 4 && index == 1 -> Alignment.BottomStart
                paneCount == 4 && index == 2 -> Alignment.TopEnd
                paneCount == 4 && index == 3 -> Alignment.TopStart
                else -> Alignment.TopStart
            }
            val statusColor = Color(0xFFFFD54F)
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .align(statusAlign)
                    .background(statusColor.copy(alpha = 0.90f), RoundedCornerShape(6.dp)),
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Black.copy(alpha = 0.86f),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
