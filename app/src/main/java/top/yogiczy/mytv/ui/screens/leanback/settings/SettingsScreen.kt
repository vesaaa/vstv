package top.yogiczy.mytv.ui.screens.leanback.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.yogiczy.mytv.ui.rememberLeanbackChildPadding
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsMenuItem
import top.yogiczy.mytv.ui.screens.leanback.settings.components.LeanbackSettingsCategoryContent
import top.yogiczy.mytv.ui.screens.leanback.settings.components.LeanbackSettingsCategoryList
import top.yogiczy.mytv.ui.theme.LeanbackTheme

@Composable
fun LeanbackSettingsScreen(
    modifier: Modifier = Modifier,
    onRequestClose: () -> Unit = {},
) {
    val childPadding = rememberLeanbackChildPadding()

    var focusedMenuItem by remember { mutableStateOf<LeanbackSettingsMenuItem>(LeanbackSettingsMenuItem.ReturnLive) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(
                top = childPadding.top + 20.dp,
                bottom = childPadding.bottom,
                start = childPadding.start,
                end = childPadding.end,
            )
            .pointerInput(Unit) { detectTapGestures(onTap = { }) },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            LeanbackSettingsCategoryList(
                modifier = Modifier.width(280.dp),
                focusedMenuItemProvider = { focusedMenuItem },
                onMenuItemFocused = { focusedMenuItem = it },
                onReturnLive = onRequestClose,
            )

            LeanbackSettingsCategoryContent(
                focusedMenuItemProvider = { focusedMenuItem },
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun LeanbackSettingsScreenPreview() {
    LeanbackTheme {
        LeanbackSettingsScreen()
    }
}