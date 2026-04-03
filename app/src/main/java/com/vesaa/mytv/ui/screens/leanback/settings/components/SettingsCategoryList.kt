package com.vesaa.mytv.ui.screens.leanback.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.vesaa.mytv.ui.screens.leanback.settings.LeanbackSettingsCategories
import com.vesaa.mytv.ui.screens.leanback.settings.LeanbackSettingsMenuItem
import com.vesaa.mytv.ui.theme.LeanbackTheme
import kotlin.math.floor

private val SettingsMenuTileSize = 120.dp
private val SettingsMenuTileSpacing = 20.dp

@Composable
fun LeanbackSettingsCategoryList(
    modifier: Modifier = Modifier,
    onCategoryOpen: (LeanbackSettingsCategories) -> Unit = {},
    onReturnLive: () -> Unit = {},
) {
    val menuItems = remember { LeanbackSettingsMenuItem.all() }
    val focusRequesters = remember(menuItems.size) {
        List(menuItems.size) { FocusRequester() }
    }
    LaunchedEffect(menuItems.size) {
        delay(48)
        focusRequesters.firstOrNull()?.requestFocus()
    }

    // 不用 TvLazyVerticalGrid：Lazy 列表会把方向键用于滚动整块区域，导致无法像在网格里那样切换焦点。
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
    ) {
        val columns = remember(maxWidth) {
            val w = maxWidth.value
            val tile = SettingsMenuTileSize.value
            val gap = SettingsMenuTileSpacing.value
            if (!w.isFinite() || w <= 0f) {
                6
            } else {
                floor((w + gap) / (tile + gap)).toInt().coerceIn(4, 12)
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(SettingsMenuTileSpacing),
        ) {
            var flatIndex = 0
            menuItems.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SettingsMenuTileSpacing),
                ) {
                    rowItems.forEach { item ->
                        val index = flatIndex++
                        val focusRequester = focusRequesters[index]

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
                            modifier = Modifier.size(SettingsMenuTileSize),
                            tileFocusRequester = focusRequester,
                            icon = icon,
                            title = title,
                            onActivate = {
                                when (item) {
                                    LeanbackSettingsMenuItem.ReturnLive -> onReturnLive()
                                    is LeanbackSettingsMenuItem.Category -> onCategoryOpen(item.value)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuTile(
    modifier: Modifier = Modifier,
    tileFocusRequester: FocusRequester,
    icon: ImageVector,
    title: String,
    onActivate: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val tileOnWhite = Color(0xFF1C1B1F)
    Surface(
        modifier = modifier
            .focusRequester(tileFocusRequester)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onActivate,
            ),
        shape = MaterialTheme.shapes.large,
        color = when {
            isFocused -> Color.White
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = when {
            isFocused -> tileOnWhite
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(
            width = if (isFocused) 3.dp else 1.dp,
            color = when {
                isFocused -> tileOnWhite
                else -> MaterialTheme.colorScheme.outlineVariant
            },
        ),
        tonalElevation = if (isFocused) 6.dp else 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
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
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp)
                .padding(16.dp),
        )
    }
}
