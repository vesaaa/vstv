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
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.itemsIndexed
import androidx.tv.foundation.lazy.grid.rememberTvLazyGridState
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import com.vesaa.mytv.ui.utils.handleLeanbackKeyEvents
import kotlinx.coroutines.delay

/** 固定 3 行 × 2 列 = 一屏 6 个，多出的向下滚动 */
private const val SwitchGridVisibleRows = 3

/** 面板相对屏幕宽度 */
private const val DialogWidthFraction = 2f / 3f

/** 面板内左右留白 */
private val PanelInnerHorizontalPadding = 22.dp

/** 行距；列缝与之相同（左格 end + 右格 start 各一半拼出总宽） */
private val RowGap = 14.dp

/**
 * 每个格子的固定高度（扁长条）。在原先约 58dp 基础上增加约 1/3，使一屏 6 格区域与长条更高。
 */
private val TileHeight = 58.dp * (4f / 3f)

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
    val columnGapHalf = RowGap / 2

    LaunchedEffect(urls) {
        delay(48)
        if (focusRequesters.isNotEmpty()) {
            runCatching { focusRequesters[0].requestFocus() }
        }
    }

    val gridMaxHeight =
        TileHeight * SwitchGridVisibleRows +
            RowGap * (SwitchGridVisibleRows - 1) +
            8.dp

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            val dialogWidth = maxWidth * DialogWidthFraction

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
                        .padding(
                            horizontal = PanelInnerHorizontalPadding,
                            vertical = 22.dp,
                        ),
                ) {
                    Text(
                        text = "切换默认直播源",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    TvLazyVerticalGrid(
                        state = gridState,
                        columns = TvGridCells.Fixed(2),
                        // 列间距由每个 Card 的 start/end padding 拼出，避免与网格实现叠在一起看不见缝
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalArrangement = Arrangement.spacedBy(RowGap),
                        contentPadding = PaddingValues(
                            start = 2.dp,
                            top = 0.dp,
                            end = 2.dp,
                            bottom = 6.dp,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(gridMaxHeight),
                    ) {
                        itemsIndexed(urls) { index, url ->
                            val focusRequester = focusRequesters[index]
                            var isFocused by remember(index) { mutableStateOf(false) }
                            val isCurrent = url.trim() == currentUrl.trim()
                            val col = index % 2
                            Card(
                                onClick = { onSourceSelected(url) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(TileHeight)
                                    .padding(
                                        start = if (col == 1) columnGapHalf else 0.dp,
                                        end = if (col == 0) columnGapHalf else 0.dp,
                                    )
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
                                        .padding(3.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = when {
                                        isFocused -> MaterialTheme.colorScheme.primaryContainer
                                        isCurrent -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    },
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
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
                                            maxLines = 2,
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
