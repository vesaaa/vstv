package com.vesaa.mytv.ui.screens.leanback.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.foundation.Border
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import com.vesaa.mytv.ui.utils.handleLeanbackKeyEvents
import kotlinx.coroutines.delay

/** 约 3 行 × 2 列可见，再多则纵向滚动 */
private val SwitchGridVisibleRows = 3
private val SwitchTileHeight = 76.dp
private val SwitchGridGap = 10.dp

internal fun distinctIptvSourceUrlsForSwitch(
    currentUrl: String,
    historyUrls: Set<String>,
): List<String> {
    val cur = currentUrl.trim()
    val fromHistory = historyUrls.asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return (fromHistory + cur)
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
        .toList()
}

@Composable
internal fun LeanbackIptvSourceSwitchOnlyDialog(
    urls: List<String>,
    currentUrl: String,
    onDismissRequest: () -> Unit,
    onSourceSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberTvLazyGridState()
    val focusRequesters = remember(urls) { List(urls.size) { FocusRequester() } }

    LaunchedEffect(urls) {
        delay(48)
        if (focusRequesters.isNotEmpty()) {
            runCatching { focusRequesters[0].requestFocus() }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            val dialogWidth = maxWidth * 0.92f
            val gridMaxHeight =
                SwitchTileHeight * SwitchGridVisibleRows +
                    SwitchGridGap * (SwitchGridVisibleRows - 1) +
                    8.dp

            Surface(
                modifier = modifier
                    .align(Alignment.Center)
                    .width(dialogWidth)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = "切换默认直播源",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "仅用于在已保存的地址之间切换。添加新地址、扫码或修改请求头请前往：设置 → 直播源。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    TvLazyVerticalGrid(
                        state = gridState,
                        columns = TvGridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(SwitchGridGap),
                        verticalArrangement = Arrangement.spacedBy(SwitchGridGap),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(gridMaxHeight),
                    ) {
                        itemsIndexed(urls) { index, url ->
                            val focusRequester = focusRequesters[index]
                            var isFocused by remember(index) { mutableStateOf(false) }
                            val isCurrent = url.trim() == currentUrl.trim()
                            Card(
                                onClick = { onSourceSelected(url) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(SwitchTileHeight)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged {
                                        isFocused = it.isFocused || it.hasFocus
                                    }
                                    .handleLeanbackKeyEvents(
                                        pointerTapEnabled = false,
                                        onSelect = {
                                            if (isFocused) onSourceSelected(url)
                                            else focusRequester.requestFocus()
                                        },
                                    ),
                                colors = CardDefaults.colors(
                                    containerColor = Color.Transparent,
                                ),
                                border = CardDefaults.border(
                                    focusedBorder = Border(
                                        border = BorderStroke(
                                            1.5.dp,
                                            MaterialTheme.colorScheme.primary,
                                        ),
                                    ),
                                ),
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = when {
                                        isFocused -> MaterialTheme.colorScheme.primaryContainer
                                        isCurrent -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    },
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        if (isCurrent) {
                                            Text(
                                                text = "当前默认",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                        }
                                        Text(
                                            text = url,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
