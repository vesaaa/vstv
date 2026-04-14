package com.vesaa.mytv.ui.screens.leanback.panel.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import com.vesaa.mytv.data.entities.EpgProgramme
import com.vesaa.mytv.data.entities.EpgProgramme.Companion.isLive
import com.vesaa.mytv.data.entities.EpgProgramme.Companion.progress
import com.vesaa.mytv.data.entities.Iptv
import com.vesaa.mytv.BuildConfig
import com.vesaa.mytv.ui.components.IptvLogoImage
import com.vesaa.mytv.ui.theme.LeanbackTheme
import com.vesaa.mytv.ui.utils.handleLeanbackKeyEvents
import com.vesaa.mytv.utils.IptvCatchup

@Composable
fun LeanbackPanelIptvItem(
    modifier: Modifier = Modifier,
    iptvProvider: () -> Iptv = { Iptv() },
    currentProgrammeProvider: () -> EpgProgramme? = { null },
    showProgrammeProgressProvider: () -> Boolean = { false },
    onIptvSelected: () -> Unit = {},
    onIptvFavoriteToggle: () -> Unit = {},
    onShowEpg: () -> Unit = {},
    initialFocusedProvider: () -> Boolean = { false },
    onHasFocused: () -> Unit = {},
    onFocused: () -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val iptv = iptvProvider()
    val currentProgramme = currentProgrammeProvider()
    val showProgrammeProgress = showProgrammeProgressProvider()
    val replaySupported = IptvCatchup.supportCatchup(iptv)

    LaunchedEffect(Unit) {
        if (initialFocusedProvider()) {
            onHasFocused()
            focusRequester.requestFocus()
        }
    }

    androidx.tv.material3.Card(
        // 触摸/鼠标点击由 Card 消费，避免与外层全屏点按关闭及下方 duplicate 的 detectTapGestures 竞态导致闪退（见 ModifierUtils.handleLeanbackKeyEvents 注释）。
        onClick = { onIptvSelected() },
        modifier = modifier
            .width(130.dp)
            .height(54.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (isFocused) onFocused()
            }
            .handleLeanbackKeyEvents(
                pointerTapEnabled = false,
                onSelect = {
                    if (isFocused) onIptvSelected()
                    else focusRequester.requestFocus()
                },
                onLongSelect = {
                    if (isFocused) onIptvFavoriteToggle()
                    else focusRequester.requestFocus()
                },
                onSettings = {
                    if (isFocused) onShowEpg()
                    else focusRequester.requestFocus()
                },
            ),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.onBackground),
            ),
        ),
    ) {
        Box(
            modifier = Modifier.background(
                color = if (isFocused) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (BuildConfig.CHANNEL_LOGOS_ENABLED && iptv.logoUrl.isNotBlank()) {
                    IptvLogoImage(
                        logoUrl = iptv.logoUrl,
                        contentDescription = iptv.name,
                        size = 28.dp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceAround,
                ) {
                    Text(
                        text = iptv.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        color = if (isFocused) MaterialTheme.colorScheme.background
                        else MaterialTheme.colorScheme.onBackground,
                    )

                    Text(
                        text = currentProgramme?.title ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        modifier = Modifier.alpha(0.8f),
                        color = if (isFocused) MaterialTheme.colorScheme.background
                        else MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            // 节目进度条
            if (showProgrammeProgress && currentProgramme != null && currentProgramme.isLive()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(currentProgramme.progress())
                        .height(3.dp)
                        .background(
                            if (isFocused) MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                        ),
                )
            }

            if (replaySupported) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "支持回看",
                    tint = if (isFocused) MaterialTheme.colorScheme.background
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = 5.dp),
                )
            }
        }
    }
}

@Preview
@Composable
private fun LeanbackPanelIptvItemPreview() {
    LeanbackTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            LeanbackPanelIptvItem(
                iptvProvider = { Iptv.EXAMPLE },
                currentProgrammeProvider = {
                    EpgProgramme(
                        startAt = System.currentTimeMillis() - 100000,
                        endAt = System.currentTimeMillis() + 200000,
                        title = "新闻联播",
                    )
                },
                showProgrammeProgressProvider = { true },
            )

            LeanbackPanelIptvItem(
                iptvProvider = { Iptv.EXAMPLE },
                currentProgrammeProvider = {
                    EpgProgramme(
                        startAt = System.currentTimeMillis() - 100000,
                        endAt = System.currentTimeMillis() + 200000,
                        title = "新闻联播",
                    )
                },
                showProgrammeProgressProvider = { true },
                initialFocusedProvider = { true },
            )
        }
    }
}