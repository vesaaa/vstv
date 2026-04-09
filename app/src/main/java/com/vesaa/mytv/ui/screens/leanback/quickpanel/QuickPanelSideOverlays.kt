package com.vesaa.mytv.ui.screens.leanback.quickpanel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyListState
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ListItemDefaults
import kotlinx.coroutines.flow.distinctUntilChanged
import com.vesaa.mytv.data.entities.Epg
import com.vesaa.mytv.data.entities.EpgProgramme
import com.vesaa.mytv.data.entities.EpgProgramme.Companion.isLive
import com.vesaa.mytv.data.entities.Iptv
import com.vesaa.mytv.ui.screens.leanback.panel.PanelAutoCloseState
import com.vesaa.mytv.ui.utils.handleLeanbackKeyEvents
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max

private val sheetCorner = RoundedCornerShape(14.dp)

/** 与选台界面 `LeanbackClassicPanelEpgList` 一致：background 0.7 透明度 */
@Composable
private fun QuickPanelEpgSurfacePanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val bg = MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
    val stroke = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f)
    Box(
        modifier = modifier
            .clip(sheetCorner)
            .background(bg)
            .border(1.dp, stroke, sheetCorner),
        content = content,
    )
}

/**
 * 右侧详情：在原先玻璃基础上「减少透明度」约 25%（更不透明、更易辨认）
 * 原先白填充约 0.22 → 约 0.28
 */
private val rightGlassFill = Color.White.copy(alpha = 0.28f)
private val rightGlassStroke = Color.White.copy(alpha = 0.38f)

@Composable
private fun QuickPanelGlassPanelRight(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(sheetCorner)
            .background(rightGlassFill)
            .border(1.dp, rightGlassStroke, sheetCorner),
        content = content,
    )
}

@Composable
fun LeanbackQuickPanelEpgLeftSheet(
    modifier: Modifier = Modifier,
    iptvProvider: () -> Iptv,
    epgProvider: () -> Epg,
    replaySupportedProvider: () -> Boolean = { false },
    autoCloseState: PanelAutoCloseState,
    onSelectProgramme: (EpgProgramme) -> Unit = {},
) {
    val iptv = iptvProvider()
    val epg = epgProvider()
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var hasFocused by remember(iptv, epg) { mutableStateOf(false) }

    val listState = remember(epg) {
        TvLazyListState(max(0, epg.programmes.indexOfFirst { it.isLive() } - 2))
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { _ -> autoCloseState.active() }
    }

    val sheetFocus = remember { FocusRequester() }
    LaunchedEffect(iptv, epg) {
        sheetFocus.requestFocus()
    }

    val onBg = MaterialTheme.colorScheme.onBackground

    QuickPanelEpgSurfacePanel(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(sheetFocus)
                .focusable()
                .handleLeanbackKeyEvents(
                    onSelect = { sheetFocus.requestFocus() },
                ),
            contentAlignment = Alignment.TopStart,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    text = iptv.channelName.ifBlank { iptv.name.ifBlank { "当前频道" } },
                    style = MaterialTheme.typography.titleMedium,
                    color = onBg,
                )
                Text(
                    text = "节目单",
                    style = MaterialTheme.typography.labelMedium,
                    color = onBg.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                TvLazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (epg.programmes.isNotEmpty()) {
                        items(epg.programmes, key = { "${it.startAt}_${it.title}" }) { programme ->
                            QuickPanelEpgProgrammeRow(
                                programme = programme,
                                timeFormat = timeFormat,
                                replaySupported = replaySupportedProvider(),
                                hasFocusedFlag = hasFocused,
                                onFocusedLive = { hasFocused = true },
                                autoCloseState = autoCloseState,
                                onSelect = { onSelectProgramme(programme) },
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "当前频道暂无节目单数据",
                                style = MaterialTheme.typography.bodyMedium,
                                color = onBg.copy(alpha = 0.85f),
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPanelEpgProgrammeRow(
    programme: EpgProgramme,
    timeFormat: SimpleDateFormat,
    replaySupported: Boolean,
    hasFocusedFlag: Boolean,
    onFocusedLive: () -> Unit,
    autoCloseState: PanelAutoCloseState,
    onSelect: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(programme) {
        if (programme.isLive() && !hasFocusedFlag) {
            onFocusedLive()
            focusRequester.requestFocus()
        }
    }

    CompositionLocalProvider(
        LocalContentColor provides if (isFocused) MaterialTheme.colorScheme.background
        else MaterialTheme.colorScheme.onBackground,
    ) {
        androidx.tv.material3.ListItem(
            modifier = Modifier
                .focusRequester(focusRequester)
                .onFocusChanged {
                    isFocused = it.isFocused || it.hasFocus
                    if (isFocused) autoCloseState.active()
                }
                .handleLeanbackKeyEvents(
                    onSelect = {
                        focusRequester.requestFocus()
                        onSelect()
                    },
                ),
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.onBackground,
                selectedContainerColor = Color.Transparent,
            ),
            selected = programme.isLive(),
            onClick = onSelect,
            headlineContent = {
                Text(
                    text = programme.title.ifBlank { "（无标题）" },
                    maxLines = if (isFocused) Int.MAX_VALUE else 1,
                )
            },
            overlineContent = {
                val start = timeFormat.format(programme.startAt)
                val end = timeFormat.format(programme.endAt)
                Text(
                    text = "$start  ~ $end",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.alpha(0.8f),
                )
            },
            trailingContent = {
                if (programme.isLive()) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "正在播出",
                    )
                } else if (replaySupported && programme.endAt in 1 until System.currentTimeMillis()) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = "可回看",
                    )
                }
            },
        )
    }
}

@Composable
fun LeanbackQuickPanelReplayRightSheet(
    modifier: Modifier = Modifier,
    capabilityLabelProvider: () -> String = { "回看不可用" },
    capabilityDetailProvider: () -> String = { "当前频道未提供回看模板或DVR入口" },
    maxHoursProvider: () -> Int = { 24 },
    replaySupportedProvider: () -> Boolean = { false },
    onReplayByBackMinutes: (Int) -> Unit = {},
    autoCloseState: PanelAutoCloseState,
) {
    val focusRequester = remember { FocusRequester() }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val options = listOf(15, 30, 60, 120, 1440).filter { it <= maxHoursProvider() * 60 }

    LaunchedEffect(capabilityLabelProvider(), capabilityDetailProvider()) {
        focusRequester.requestFocus()
    }

    QuickPanelGlassPanelRight(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onFocusChanged { if (it.isFocused || it.hasFocus) autoCloseState.active() },
            contentAlignment = Alignment.TopStart,
        ) {
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "回看",
                    style = MaterialTheme.typography.titleMedium,
                    color = onSurface,
                )
                Text(
                    text = "能力：${capabilityLabelProvider()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurface.copy(alpha = 0.92f),
                )
                Text(
                    text = capabilityDetailProvider(),
                    style = MaterialTheme.typography.bodySmall,
                    color = onSurface.copy(alpha = 0.8f),
                )
                if (replaySupportedProvider()) {
                    Text(
                        text = "最大回看：${maxHoursProvider()}小时",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurface.copy(alpha = 0.85f),
                    )
                    TvLazyColumn(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        items(options, key = { it }) { minutes ->
                            androidx.tv.material3.ListItem(
                                selected = false,
                                onClick = { onReplayByBackMinutes(minutes) },
                                headlineContent = { Text("回看 $minutes 分钟") },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LeanbackQuickPanelMetadataRightSheet(
    modifier: Modifier = Modifier,
    title: String,
    body: String,
    autoCloseState: PanelAutoCloseState,
) {
    val scroll = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val onSurface = MaterialTheme.colorScheme.onSurface

    LaunchedEffect(title, body) {
        focusRequester.requestFocus()
    }

    QuickPanelGlassPanelRight(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .handleLeanbackKeyEvents(
                    onSelect = { autoCloseState.active() },
                )
                .onFocusChanged { if (it.isFocused || it.hasFocus) autoCloseState.active() },
            contentAlignment = Alignment.TopStart,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .verticalScroll(scroll),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = onSurface,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = onSurface.copy(alpha = 0.92f),
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
