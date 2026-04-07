package com.vesaa.mytv.ui.screens.leanback.panel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.delay
import com.vesaa.mytv.data.utils.Constants
import com.vesaa.mytv.ui.rememberLeanbackChildPadding
import com.vesaa.mytv.ui.screens.leanback.panel.components.LeanbackPanelDateTime
import com.vesaa.mytv.ui.theme.LeanbackTheme
import com.vesaa.mytv.ui.utils.SP

@Composable
fun LeanbackPanelDateTimeScreen(
    modifier: Modifier = Modifier,
    showModeProvider: () -> SP.UiTimeShowMode = { SP.UiTimeShowMode.HIDDEN },
) {
    val childPadding = rememberLeanbackChildPadding()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val timestamp = System.currentTimeMillis()

            visible = when (showModeProvider()) {
                SP.UiTimeShowMode.HIDDEN -> false
                SP.UiTimeShowMode.ALWAYS -> true

                SP.UiTimeShowMode.EVERY_HOUR -> {
                    timestamp % 3600000 <= (Constants.UI_TIME_SHOW_RANGE + 1000) || timestamp % 3600000 >= 3600000 - Constants.UI_TIME_SHOW_RANGE
                }

                SP.UiTimeShowMode.HALF_HOUR -> {
                    timestamp % 1800000 <= (Constants.UI_TIME_SHOW_RANGE + 1000) || timestamp % 1800000 >= 1800000 - Constants.UI_TIME_SHOW_RANGE
                }
            }

            delay(1000)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (visible) {
            // 与详情面板顶部 [LeanbackPanelDateTime] 同款：日期 + 时间双行，无黑底块
            LeanbackPanelDateTime(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = childPadding.top, end = childPadding.end),
                horizontalAlignment = Alignment.End,
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun LeanbackPanelDateTimeScreenPreview() {
    LeanbackTheme {
        LeanbackPanelDateTimeScreen(showModeProvider = { SP.UiTimeShowMode.ALWAYS })
    }
}