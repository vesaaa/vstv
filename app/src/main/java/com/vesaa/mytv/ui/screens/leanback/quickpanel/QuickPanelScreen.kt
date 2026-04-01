package com.vesaa.mytv.ui.screens.leanback.quickpanel

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

/** 底部频道信息 + 播放信息 + 按钮行总高度预留（右侧详情侧栏用，避免盖住底栏） */
private val QuickPanelBottomToolbarReserve = 252.dp

/**
 * 右侧详情侧栏底部留白：小于 [QuickPanelBottomToolbarReserve]，侧栏可向下延伸、更贴近底栏区域。
 */
private val QuickPanelRightSheetBottomReserve = 168.dp

/** 打开节目单时仅留少量底边距，侧栏尽量占满纵向 */
private val QuickPanelEpgSheetBottomMargin = 12.dp

private val QuickPanelMenuIconSize = 20.dp

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
    val showBottomChrome = subPanel != LeanbackQuickPanelSubPanel.Epg

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
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val bottomInsetRight = QuickPanelRightSheetBottomReserve + childPadding.bottom
            val bottomInsetEpg = QuickPanelEpgSheetBottomMargin + childPadding.bottom
            val bottomInsetDefault = QuickPanelBottomToolbarReserve + childPadding.bottom
            val sideTopPad = 12.dp
            val sideStartPad = childPadding.start

            val epgBottom = if (showBottomChrome) bottomInsetDefault else bottomInsetEpg

            when (subPanel) {
                LeanbackQuickPanelSubPanel.Epg ->
                    LeanbackQuickPanelEpgLeftSheet(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = sideStartPad,
                                top = sideTopPad,
                                bottom = epgBottom,
                            )
                            .fillMaxHeight()
                            .fillMaxWidth(0.40f),
                        iptvProvider = currentIptvProvider,
                        epgProvider = currentEpgProvider,
                        autoCloseState = autoCloseState,
                    )

                LeanbackQuickPanelSubPanel.VideoDetail ->
                    LeanbackQuickPanelMetadataRightSheet(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(
                                end = sideStartPad,
                                top = sideTopPad,
                                bottom = bottomInsetRight,
                            )
                            .fillMaxHeight()
                            .fillMaxWidth(0.336f),
                        title = "视频信息",
                        body = formatQuickPanelVideoDetailBody(videoPlayerMetadataProvider()),
                        autoCloseState = autoCloseState,
                    )

                LeanbackQuickPanelSubPanel.AudioDetail ->
                    LeanbackQuickPanelMetadataRightSheet(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(
                                end = sideStartPad,
                                top = sideTopPad,
                                bottom = bottomInsetRight,
                            )
                            .fillMaxHeight()
                            .fillMaxWidth(0.336f),
                        title = "音频信息",
                        body = formatQuickPanelAudioDetailBody(videoPlayerMetadataProvider()),
                        autoCloseState = autoCloseState,
                    )

                LeanbackQuickPanelSubPanel.StreamDetail ->
                    LeanbackQuickPanelMetadataRightSheet(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(
                                end = sideStartPad,
                                top = sideTopPad,
                                bottom = bottomInsetRight,
                            )
                            .fillMaxHeight()
                            .fillMaxWidth(0.336f),
                        title = "解码与码流",
                        body = formatQuickPanelStreamDetailBody(videoPlayerMetadataProvider()),
                        autoCloseState = autoCloseState,
                    )

                LeanbackQuickPanelSubPanel.None -> Unit
            }
        }

        LeanbackPanelScreenTopRight(
            channelNoProvider = currentIptvChannelNoProvider,
        )

        if (showBottomChrome) {
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
                            leadingIcon = Icons.Filled.List,
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
                            leadingIcon = Icons.Filled.Videocam,
                            titleProvider = {
                                formatQuickPanelVideoButtonLabel(videoPlayerMetadataProvider())
                            },
                            titleMaxLines = 1,
                            titleOverflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 220.dp),
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
                            leadingIcon = Icons.Filled.MusicNote,
                            titleProvider = {
                                formatQuickPanelAudioButtonLabel(videoPlayerMetadataProvider())
                            },
                            titleMaxLines = 1,
                            titleOverflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 240.dp),
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
                            leadingIcon = Icons.Filled.Memory,
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
                            leadingIcon = Icons.Filled.Home,
                            titleProvider = { "主菜单" },
                            onSelect = onMoreSettings,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeanbackQuickPanelButton(
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    titleProvider: () -> String,
    subtitleProvider: (() -> String)? = null,
    titleMaxLines: Int = 2,
    titleOverflow: TextOverflow = TextOverflow.Clip,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(QuickPanelMenuIconSize),
                )
                Spacer(Modifier.width(5.dp))
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = titleProvider(),
                    textAlign = TextAlign.Center,
                    maxLines = titleMaxLines,
                    overflow = titleOverflow,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (subtitleProvider != null) {
                    Text(
                        text = subtitleProvider(),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
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
            leadingIcon = Icons.Filled.AltRoute,
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
        leadingIcon = Icons.Filled.AspectRatio,
        titleProvider = {
            "画面比例 " + when (videoPlayerAspectRatioProvider()) {
                16f / 9f -> "16:9"
                4f / 3f -> "4:3"
                screenAspectRatio -> "自动拉伸"
                else -> "原始"
            }
        },
        titleMaxLines = 1,
        titleOverflow = TextOverflow.Ellipsis,
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
