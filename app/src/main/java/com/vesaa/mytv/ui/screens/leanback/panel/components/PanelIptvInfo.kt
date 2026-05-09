package com.vesaa.mytv.ui.screens.leanback.panel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vesaa.mytv.data.entities.EpgProgrammeCurrent
import com.vesaa.mytv.data.entities.Iptv
import com.vesaa.mytv.ui.theme.LeanbackTheme
import com.vesaa.mytv.utils.isIPv6

@Composable
fun LeanbackPanelIptvInfo(
    modifier: Modifier = Modifier,
    iptvProvider: () -> Iptv = { Iptv() },
    iptvUrlIdxProvider: () -> Int = { 0 },
    currentProgrammesProvider: () -> EpgProgrammeCurrent? = { null },
    playbackStatusProvider: () -> String = { "" },
    videoResolutionTagProvider: () -> String = { "" },
) {
    val iptv = iptvProvider()
    val iptvUrlIdx = iptvUrlIdxProvider()
    val currentProgrammes = currentProgrammesProvider()
    val playbackStatus = playbackStatusProvider().trim()
    val videoResolutionTag = videoResolutionTagProvider().trim()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = iptv.name,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier
                    .alignByBaseline()
                    .weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.width(6.dp))

            Row(
                modifier = Modifier.padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.labelMedium,
                    LocalContentColor provides LocalContentColor.current.copy(alpha = 0.8f),
                ) {
                    val textModifier = Modifier.padding(vertical = 1.dp, horizontal = 2.dp)

                    // 多线路标识
                    if (iptv.urlList.size > 1) {
                        PanelInfoBadge(
                            text = "${iptvUrlIdx + 1}/${iptv.urlList.size}",
                            modifier = textModifier,
                        )
                    }

                    if (playbackStatus.isNotEmpty()) {
                        PanelInfoBadge(
                            text = playbackStatus,
                            modifier = textModifier,
                        )
                    }

                    // ipv4/ipv6（无播放地址时不访问 urlList[0]，避免空列表崩溃）
                    if (iptv.urlList.isNotEmpty()) {
                        val urlIdx = iptvUrlIdx.coerceIn(iptv.urlList.indices)
                        PanelInfoBadge(
                            text = if (iptv.urlList[urlIdx].isIPv6()) "IPV6" else "IPV4",
                            modifier = textModifier,
                        )
                    }

                    if (videoResolutionTag.isNotEmpty()) {
                        PanelInfoBadge(
                            text = videoResolutionTag,
                            modifier = textModifier,
                        )
                    }
                }
            }
        }

        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodyLarge,
            LocalContentColor provides LocalContentColor.current.copy(alpha = 0.8f),
        ) {
            Text(
                text = "正在播放：${currentProgrammes?.now?.title ?: "无节目"}",
                maxLines = 1,
            )
            Text(
                text = "稍后播放：${currentProgrammes?.next?.title ?: "无节目"}",
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelInfoBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = LocalContentColor.current.copy(alpha = 0.26f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Preview
@Composable
private fun LeanbackPanelIptvInfoPreview() {
    LeanbackTheme {
        LeanbackPanelIptvInfo(
            iptvProvider = { Iptv.EXAMPLE },
            iptvUrlIdxProvider = { 1 },
            currentProgrammesProvider = { EpgProgrammeCurrent.EXAMPLE },
        )
    }
}