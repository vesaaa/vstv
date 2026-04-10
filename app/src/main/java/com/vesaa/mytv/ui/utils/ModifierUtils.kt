package com.vesaa.mytv.ui.utils

import android.os.Build
import android.view.KeyEvent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.collections.buildMap
import kotlin.math.absoluteValue

fun Modifier.handleLeanbackKeyEvents(
    onKeyTap: Map<Int, () -> Unit> = emptyMap(),
    onKeyLongTap: Map<Int, () -> Unit> = emptyMap(),
): Modifier {
    val keyDownMap = mutableMapOf<Int, Boolean>()

    return onPreviewKeyEvent {
        when (it.nativeKeyEvent.action) {
            KeyEvent.ACTION_DOWN -> {
                if (it.nativeKeyEvent.repeatCount == 0) {
                    keyDownMap[it.nativeKeyEvent.keyCode] = true
                } else if (it.nativeKeyEvent.repeatCount == 1) {
                    keyDownMap.remove(it.nativeKeyEvent.keyCode)
                    onKeyLongTap[it.nativeKeyEvent.keyCode]?.invoke()
                }
            }

            KeyEvent.ACTION_UP -> {
                if (keyDownMap[it.nativeKeyEvent.keyCode] == true) {
                    keyDownMap.remove(it.nativeKeyEvent.keyCode)
                    onKeyTap[it.nativeKeyEvent.keyCode]?.invoke()
                }
            }
        }

        false
    }
}

fun Modifier.handleLeanbackDragGestures(
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
): Modifier {
    val speedThreshold = 100.dp
    val distanceThreshold = 10.dp

    val verticalTracker = VelocityTracker()
    var verticalDragOffset = 0f
    val horizontalTracker = VelocityTracker()
    var horizontalDragOffset = 0f


    return this then pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragEnd = {
                if (verticalDragOffset.absoluteValue > distanceThreshold.toPx()) {
                    if (verticalTracker.calculateVelocity().y > speedThreshold.toPx()) {
                        onSwipeDown()
                    } else if (verticalTracker.calculateVelocity().y < -speedThreshold.toPx()) {
                        onSwipeUp()
                    }
                }
            },
        ) { change, dragAmount ->
            verticalDragOffset += dragAmount
            verticalTracker.addPosition(change.uptimeMillis, change.position)
        }
    }.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = {
                if (horizontalDragOffset.absoluteValue > distanceThreshold.toPx()) {
                    if (horizontalTracker.calculateVelocity().x > speedThreshold.toPx()) {
                        onSwipeRight()
                    } else if (horizontalTracker.calculateVelocity().x < -speedThreshold.toPx()) {
                        onSwipeLeft()
                    }
                }
            },
        ) { change, dragAmount ->
            horizontalDragOffset += dragAmount
            horizontalTracker.addPosition(change.uptimeMillis, change.position)
        }
    }
}

fun Modifier.handleLeanbackKeyEvents(
    key: Any = Unit,
    onLeft: () -> Unit = {},
    onLongLeft: () -> Unit = {},
    onRight: () -> Unit = {},
    onLongRight: () -> Unit = {},
    onUp: () -> Unit = {},
    onLongUp: () -> Unit = {},
    onDown: () -> Unit = {},
    onLongDown: () -> Unit = {},
    onSelect: () -> Unit = {},
    onLongSelect: () -> Unit = {},
    onSettings: () -> Unit = {},
    onNumber: (Int) -> Unit = {},
    /**
     * 为 false 时不附加 [detectTapGestures]，用于已自带 [androidx.tv.material3.ListItem] `onClick`
     * 等可点击实现的组件：否则触摸事件可能既未被子级可靠消费，又与外层全屏点按关闭等手势冲突，
     * 在部分机型上会导致面板已销毁后仍触发回调（例如对已失效的 FocusRequester 操作）而闪退。
     */
    pointerTapEnabled: Boolean = true,
): Modifier {
    val keyOnly = handleLeanbackKeyEvents(
        onKeyTap = buildMap {
            put(KeyEvent.KEYCODE_DPAD_LEFT, onLeft)
            put(KeyEvent.KEYCODE_DPAD_RIGHT, onRight)
            put(KeyEvent.KEYCODE_DPAD_UP, onUp)
            put(KeyEvent.KEYCODE_CHANNEL_UP, onUp)
            put(KeyEvent.KEYCODE_DPAD_DOWN, onDown)
            put(KeyEvent.KEYCODE_CHANNEL_DOWN, onDown)

            put(KeyEvent.KEYCODE_DPAD_CENTER, onSelect)
            put(KeyEvent.KEYCODE_ENTER, onSelect)
            put(KeyEvent.KEYCODE_NUMPAD_ENTER, onSelect)

            put(KeyEvent.KEYCODE_MENU, onSettings)
            put(KeyEvent.KEYCODE_SETTINGS, onSettings)
            put(KeyEvent.KEYCODE_HELP, onSettings)
            put(KeyEvent.KEYCODE_H, onSettings)

            put(KeyEvent.KEYCODE_L, onLongSelect)

            put(KeyEvent.KEYCODE_0) { onNumber(0) }
            put(KeyEvent.KEYCODE_1) { onNumber(1) }
            put(KeyEvent.KEYCODE_2) { onNumber(2) }
            put(KeyEvent.KEYCODE_3) { onNumber(3) }
            put(KeyEvent.KEYCODE_4) { onNumber(4) }
            put(KeyEvent.KEYCODE_5) { onNumber(5) }
            put(KeyEvent.KEYCODE_6) { onNumber(6) }
            put(KeyEvent.KEYCODE_7) { onNumber(7) }
            put(KeyEvent.KEYCODE_8) { onNumber(8) }
            put(KeyEvent.KEYCODE_9) { onNumber(9) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT, onLeft)
                put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT, onRight)
                put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP, onUp)
                put(KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN, onDown)
            }
        },
        onKeyLongTap = mapOf(
            KeyEvent.KEYCODE_DPAD_LEFT to onLongLeft,
            KeyEvent.KEYCODE_DPAD_RIGHT to onLongRight,
            KeyEvent.KEYCODE_DPAD_UP to onLongUp,
            KeyEvent.KEYCODE_CHANNEL_UP to onLongUp,
            KeyEvent.KEYCODE_DPAD_DOWN to onLongDown,
            KeyEvent.KEYCODE_CHANNEL_DOWN to onLongDown,

            KeyEvent.KEYCODE_ENTER to onLongSelect,
            KeyEvent.KEYCODE_NUMPAD_ENTER to onLongSelect,
            KeyEvent.KEYCODE_DPAD_CENTER to onLongSelect,
        ),
    )
    return this then keyOnly then if (pointerTapEnabled) {
        Modifier.pointerInput(key) {
            detectTapGestures(
                onTap = { onSelect() },
                onLongPress = { onLongSelect() },
                onDoubleTap = { onSettings() },
            )
        }
    } else {
        Modifier
    }
}

fun Modifier.handleLeanbackUserAction(onHandle: () -> Unit) =
    onPreviewKeyEvent { onHandle(); false }
        .pointerInput(Unit) { detectDragGestures { _, _ -> onHandle() } }
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { onHandle() },
                onDoubleTap = { onHandle() },
                onLongPress = { onHandle() },
                onPress = { onHandle() },
            )
        }