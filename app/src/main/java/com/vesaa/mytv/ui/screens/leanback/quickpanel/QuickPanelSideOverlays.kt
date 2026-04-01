package com.vesaa.mytv.ui.screens.leanback.quickpanel

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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

private val sheetBackground = Color.Black.copy(alpha = 0.55f)

@Composable
fun LeanbackQuickPanelEpgLeftSheet(
    modifier: Modifier = Modifier,
    iptvProvider: () -> Iptv,
    epgProvider: () -> Epg,
    autoCloseState: PanelAutoCloseState,
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

    Box(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.42f)
            .background(sheetBackground)
            .focusRequester(sheetFocus)
            .focusable()
            .handleLeanbackKeyEvents(
                onSelect = { sheetFocus.requestFocus() },
            ),
        contentAlignment = Alignment.TopStart,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = iptv.channelName.ifBlank { iptv.name.ifBlank { "当前频道" } },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = "节目单",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f),
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
                            hasFocusedFlag = hasFocused,
                            onFocusedLive = { hasFocused = true },
                            autoCloseState = autoCloseState,
                        )
                    }
                } else {
                    item {
                        Text(
                            text = "当前频道暂无节目单数据",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(8.dp),
                        )
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
    hasFocusedFlag: Boolean,
    onFocusedLive: () -> Unit,
    autoCloseState: PanelAutoCloseState,
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
        else Color.White,
    ) {
        androidx.tv.material3.ListItem(
            modifier = Modifier
                .focusRequester(focusRequester)
                .onFocusChanged {
                    isFocused = it.isFocused || it.hasFocus
                    if (isFocused) autoCloseState.active()
                }
                .handleLeanbackKeyEvents(
                    onSelect = { focusRequester.requestFocus() },
                ),
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.onBackground,
                selectedContainerColor = Color.Transparent,
            ),
            selected = programme.isLive(),
            onClick = { },
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
                }
            },
        )
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

    LaunchedEffect(title, body) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.44f)
            .background(sheetBackground)
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
                color = Color.White,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.92f),
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
