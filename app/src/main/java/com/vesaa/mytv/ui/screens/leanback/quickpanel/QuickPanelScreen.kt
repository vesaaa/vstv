package com.vesaa.mytv.ui.screens.leanback.quickpanel

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ButtonDefaults
import com.vesaa.mytv.data.entities.Epg
import com.vesaa.mytv.data.entities.EpgProgrammeCurrent
import com.vesaa.mytv.data.entities.Iptv
import com.vesaa.mytv.data.utils.Constants
import com.vesaa.mytv.ui.rememberLeanbackChildPadding
import com.vesaa.mytv.ui.screens.leanback.panel.LeanbackPanelScreenTopRight
import com.vesaa.mytv.ui.screens.leanback.panel.PanelAutoCloseState
import com.vesaa.mytv.ui.screens.leanback.panel.components.LeanbackPanelIptvInfo
import com.vesaa.mytv.ui.screens.leanback.panel.components.LeanbackPanelPlayerInfo
import com.vesaa.mytv.ui.screens.leanback.panel.rememberPanelAutoCloseState
import com.vesaa.mytv.ui.screens.leanback.quickpanel.components.LeanbackQuickPanelIptvChannelsDialog
import com.vesaa.mytv.ui.screens.leanback.video.player.LeanbackVideoPlayer
import com.vesaa.mytv.ui.theme.LeanbackTheme
import com.vesaa.mytv.ui.utils.handleLeanbackKeyEvents
import com.vesaa.mytv.ui.utils.handleLeanbackUserAction

@Composable
fun LeanbackQuickPanelScreen(
    modifier: Modifier = Modifier,
    currentIptvProvider: () -> Iptv = { Iptv() },
    currentIptvUrlIdxProvider: () -> Int = { 0 },
    currentProgrammesProvider: () -> EpgProgrammeCurrent? = { null },
    currentEpgProvider: () -> Epg = { Epg() },
    currentIptvChannelNoProvider: () -> String = { "" },
    videoPlayerMetadataProvider: () -> LeanbackVideoPlayer.Metadata = { LeanbackVideoPlayer.Metadata() },
    videoPlayerAspectRatioProvider: () -> Float = { 16f / 9f },
    onChangeVideoPlayerAspectRatio: (Float) -> Unit = {},
    onIptvUrlIdxChange: (Int) -> Unit = {},
    subPanel: LeanbackQuickPanelSubPanel = LeanbackQuickPanelSubPanel.None,
    onSubPanelChange: (LeanbackQuickPanelSubPanel) -> Unit = {},
    onMoreSettings: () -> Unit = {},
    onClose: () -> Unit = {},
    autoCloseState: PanelAutoCloseState = rememberPanelAutoCloseState(
        timeout = Constants.UI_SCREEN_AUTO_CLOSE_DELAY,
        onTimeout = onClose,
    ),
) {
    val childPadding = rememberLeanbackChildPadding()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        autoCloseState.active()
    }

    LaunchedEffect(subPanel) {
        if (subPanel == LeanbackQuickPanelSubPanel.None) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
            .focusRequester(focusRequester)
            .handleLeanbackUserAction { autoCloseState.active() }
            .pointerInput(subPanel) {
                detectTapGestures(
                    onTap = {
                        if (subPanel != LeanbackQuickPanelSubPanel.None) {
                            onSubPanelChange(LeanbackQuickPanelSubPanel.None)
                        } else {
                            onClose()
                        }
                    },
                )
            },
    ) {
        when (subPanel) {
            LeanbackQuickPanelSubPanel.Epg ->
                LeanbackQuickPanelEpgLeftSheet(
                    modifier = Modifier.align(Alignment.CenterStart),
                    iptvProvider = currentIptvProvider,
                    epgProvider = currentEpgProvider,
                    autoCloseState = autoCloseState,
                )

            LeanbackQuickPanelSubPanel.VideoDetail ->
                LeanbackQuickPanelMetadataRightSheet(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    title = "视频信息",
                    body = formatQuickPanelVideoDetailBody(videoPlayerMetadataProvider()),
                    autoCloseState = autoCloseState,
                )

            LeanbackQuickPanelSubPanel.AudioDetail ->
                LeanbackQuickPanelMetadataRightSheet(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    title = "音频信息",
                    body = formatQuickPanelAudioDetailBody(videoPlayerMetadataProvider()),
                    autoCloseState = autoCloseState,
                )

            LeanbackQuickPanelSubPanel.StreamDetail ->
                LeanbackQuickPanelMetadataRightSheet(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    title = "解码与码流",
                    body = formatQuickPanelStreamDetailBody(videoPlayerMetadataProvider()),
                    autoCloseState = autoCloseState,
                )

            LeanbackQuickPanelSubPanel.None -> Unit
        }

        LeanbackPanelScreenTopRight(
            channelNoProvider = currentIptvChannelNoProvider,
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(
                    start = childPadding.start,
                    bottom = childPadding.bottom,
                    top = 20.dp,
                ),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                LeanbackPanelIptvInfo(
                    iptvProvider = currentIptvProvider,
                    iptvUrlIdxProvider = currentIptvUrlIdxProvider,
                    currentProgrammesProvider = currentProgrammesProvider,
                )

                LeanbackPanelPlayerInfo(
                    metadataProvider = videoPlayerMetadataProvider,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LeanbackQuickPanelButton(
                        titleProvider = { "节目单" },
                        onSelect = {
                            onSubPanelChange(
                                if (subPanel == LeanbackQuickPanelSubPanel.Epg) {
                                    LeanbackQuickPanelSubPanel.None
                                } else {
                                    LeanbackQuickPanelSubPanel.Epg
                                },
                            )
                            autoCloseState.active()
                        },
                    )

                    LeanbackQuickPanelActionMultipleChannels(
                        currentIptvProvider = currentIptvProvider,
                        currentIptvUrlIdxProvider = currentIptvUrlIdxProvider,
                        onIptvUrlIdxChange = onIptvUrlIdxChange,
                        onUserAction = { autoCloseState.active() },
                    )

                    LeanbackQuickPanelActionVideoAspectRatio(
                        videoPlayerAspectRatioProvider = videoPlayerAspectRatioProvider,
                        onChangeVideoPlayerAspectRatio = onChangeVideoPlayerAspectRatio,
                    )

                    LeanbackQuickPanelButton(
                        titleProvider = { "视频信息" },
                        subtitleProvider = {
                            formatQuickPanelVideoMenuSubtitle(videoPlayerMetadataProvider())
                        },
                        onSelect = {
                            onSubPanelChange(
                                if (subPanel == LeanbackQuickPanelSubPanel.VideoDetail) {
                                    LeanbackQuickPanelSubPanel.None
                                } else {
                                    LeanbackQuickPanelSubPanel.VideoDetail
                                },
                            )
                            autoCloseState.active()
                        },
                    )

                    LeanbackQuickPanelButton(
                        titleProvider = { "音频信息" },
                        subtitleProvider = {
                            formatQuickPanelAudioMenuSubtitle(videoPlayerMetadataProvider())
                        },
                        onSelect = {
                            onSubPanelChange(
                                if (subPanel == LeanbackQuickPanelSubPanel.AudioDetail) {
                                    LeanbackQuickPanelSubPanel.None
                                } else {
                                    LeanbackQuickPanelSubPanel.AudioDetail
                                },
                            )
                            autoCloseState.active()
                        },
                    )

                    LeanbackQuickPanelButton(
                        titleProvider = { "解码与码流" },
                        onSelect = {
                            onSubPanelChange(
                                if (subPanel == LeanbackQuickPanelSubPanel.StreamDetail) {
                                    LeanbackQuickPanelSubPanel.None
                                } else {
                                    LeanbackQuickPanelSubPanel.StreamDetail
                                },
                            )
                            autoCloseState.active()
                        },
                    )

                    LeanbackQuickPanelButton(
                        titleProvider = { "主菜单" },
                        onSelect = onMoreSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun LeanbackQuickPanelButton(
    modifier: Modifier = Modifier,
    titleProvider: () -> String,
    subtitleProvider: (() -> String)? = null,
    onSelect: () -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    androidx.tv.material3.Button(
        onClick = { },
        shape = ButtonDefaults.shape(
            shape = MaterialTheme.shapes.small,
        ),
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
            }
            .handleLeanbackKeyEvents(
                onSelect = {
                    if (isFocused) onSelect()
                    else focusRequester.requestFocus()
                },
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 2.dp),
        ) {
            androidx.tv.material3.Text(
                text = titleProvider(),
                textAlign = TextAlign.Center,
            )
            if (subtitleProvider != null) {
                androidx.tv.material3.Text(
                    text = subtitleProvider(),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun LeanbackQuickPanelActionMultipleChannels(
    currentIptvProvider: () -> Iptv = { Iptv() },
    currentIptvUrlIdxProvider: () -> Int = { 0 },
    onIptvUrlIdxChange: (Int) -> Unit = {},
    onUserAction: () -> Unit = {},
) {
    if (currentIptvProvider().urlList.size > 1) {
        var showChannelsDialog by remember { mutableStateOf(false) }
        LeanbackQuickPanelButton(
            titleProvider = { "多线路" },
            onSelect = { showChannelsDialog = true },
        )
        LeanbackQuickPanelIptvChannelsDialog(
            showDialogProvider = { showChannelsDialog },
            onDismissRequest = { showChannelsDialog = false },
            iptvProvider = currentIptvProvider,
            iptvUrlIdxProvider = currentIptvUrlIdxProvider,
            onIptvUrlIdxChange = onIptvUrlIdxChange,
            onUserAction = onUserAction,
        )
    }
}

@Composable
private fun LeanbackQuickPanelActionVideoAspectRatio(
    videoPlayerAspectRatioProvider: () -> Float = { 16f / 9f },
    onChangeVideoPlayerAspectRatio: (Float) -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val screenAspectRatio =
        configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()
    LeanbackQuickPanelButton(
        titleProvider = {
            "画面比例 " + when (videoPlayerAspectRatioProvider()) {
                16f / 9f -> "16:9"
                4f / 3f -> "4:3"
                screenAspectRatio -> "自动拉伸"
                else -> "原始"
            }
        },
        onSelect = {
            onChangeVideoPlayerAspectRatio(
                when (videoPlayerAspectRatioProvider()) {
                    16f / 9f -> 4f / 3f
                    4f / 3f -> screenAspectRatio
                    screenAspectRatio -> 16f / 9f
                    else -> 16f / 9f
                },
            )
        },
    )
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun LeanbackQuickPanelScreenPreview() {
    LeanbackTheme {
        LeanbackQuickPanelScreen(
            currentIptvProvider = { Iptv.EXAMPLE },
            currentProgrammesProvider = { EpgProgrammeCurrent.EXAMPLE },
            videoPlayerMetadataProvider = {
                LeanbackVideoPlayer.Metadata(
                    videoWidth = 1920,
                    videoHeight = 1080,
                    videoMimeType = "video/avc",
                    audioMimeType = "audio/mp4a-latm",
                    audioChannels = 2,
                )
            },
        )
    }
}
