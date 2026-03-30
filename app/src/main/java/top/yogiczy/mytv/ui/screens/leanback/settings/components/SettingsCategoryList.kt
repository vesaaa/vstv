package top.yogiczy.mytv.ui.screens.leanback.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsMenuItem
import top.yogiczy.mytv.ui.theme.LeanbackTheme
import top.yogiczy.mytv.ui.utils.handleLeanbackKeyEvents

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LeanbackSettingsCategoryList(
    modifier: Modifier = Modifier,
    focusedMenuItemProvider: () -> LeanbackSettingsMenuItem = { LeanbackSettingsMenuItem.ReturnLive },
    onMenuItemFocused: (LeanbackSettingsMenuItem) -> Unit = {},
    onReturnLive: () -> Unit = {},
) {
    val menuItems = remember { LeanbackSettingsMenuItem.all() }
    var hasInitialFocus by rememberSaveable { mutableStateOf(false) }

    TvLazyVerticalGrid(
        columns = TvGridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
        modifier = modifier.focusRestorer(),
    ) {
        itemsIndexed(menuItems) { index, item ->
            val isSelected by remember {
                derivedStateOf { focusedMenuItemProvider() == item }
            }
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                if (index == 0 && !hasInitialFocus) {
                    focusRequester.requestFocus()
                    hasInitialFocus = true
                }
            }

            val icon: ImageVector
            val title: String
            when (item) {
                LeanbackSettingsMenuItem.ReturnLive -> {
                    icon = Icons.Default.LiveTv
                    title = "返回直播"
                }
                is LeanbackSettingsMenuItem.Category -> {
                    icon = item.value.icon
                    title = item.value.title
                }
            }

            SettingsMenuTile(
                modifier = Modifier.fillMaxWidth(),
                tileFocusRequester = focusRequester,
                icon = icon,
                title = title,
                selected = isSelected,
                onFocused = { onMenuItemFocused(item) },
                isReturnLive = item is LeanbackSettingsMenuItem.ReturnLive,
                onReturnLive = onReturnLive,
            )
        }
    }
}

@Composable
private fun SettingsMenuTile(
    modifier: Modifier = Modifier,
    tileFocusRequester: FocusRequester,
    icon: ImageVector,
    title: String,
    selected: Boolean,
    onFocused: () -> Unit,
    isReturnLive: Boolean,
    onReturnLive: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .focusRequester(tileFocusRequester)
            .focusable()
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (isFocused) onFocused()
            }
            .handleLeanbackKeyEvents(
                onSelect = {
                    if (!isFocused) {
                        tileFocusRequester.requestFocus()
                    } else if (isReturnLive) {
                        onReturnLive()
                    } else {
                        focusManager.moveFocus(FocusDirection.Right)
                    }
                },
                onRight = {
                    if (isFocused && !isReturnLive) {
                        focusManager.moveFocus(FocusDirection.Right)
                    }
                },
            ),
        shape = MaterialTheme.shapes.medium,
        color = when {
            isFocused -> MaterialTheme.colorScheme.primaryContainer
            selected -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = when {
                isFocused -> MaterialTheme.colorScheme.primary
                selected -> MaterialTheme.colorScheme.outline
                else -> MaterialTheme.colorScheme.outlineVariant
            },
        ),
        tonalElevation = if (isFocused) 4.dp else 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryListPreview() {
    LeanbackTheme {
        LeanbackSettingsCategoryList(
            modifier = Modifier.padding(16.dp),
        )
    }
}
