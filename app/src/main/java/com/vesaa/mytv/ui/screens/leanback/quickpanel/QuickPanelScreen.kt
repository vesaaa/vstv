package com.vesaa.mytv.ui.screens.leanback.quickpanel

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Glow
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

private val QuickPanelMenuIconSize = 24.dp

/**
 * 底部按钮行仅缩小留白与间距（约 15%），不缩小字号与图标尺寸 [QuickPanelMenuIconSize]。
 */
private const val QuickPanelMenuLayoutFactor = 0.85f

/** TvLazyRow 视口左缘会裁剪子项；首项需内缩，否则按钮左侧圆角/焦点环显示不全 */
private val QuickPanelBottomMenuRowStartInset = (8f * QuickPanelMenuLayoutFactor).dp

private val QuickPanelMenuItemSpacing = (10f * QuickPanelMenuLayoutFactor).dp

private val QuickPanelMenuButtonInnerPaddingH = (4f * QuickPanelMenuLayoutFactor).dp

private val QuickPanelMenuButtonInnerPaddingV = (2f * QuickPanelMenuLayoutFactor).dp

private val QuickPanelMenuIconTextGap = (4f * QuickPanelMenuLayoutFactor).dp

/** 底部快捷栏横向列表槽位（用 [items] DSL，避免依赖部分 tv-foundation 版本不存在的 [item] 导入） */
private enum class QuickPanelBottomMenuSlot {
    Epg,
    Replay,
    BackLive,
    MultiLine,
    AspectRatio,
    Video,
    Audio,
    Stream,
    Home,
}

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
    playbackStatusProvider: () -> String = { "" },
    replayCapabilityProvider: () -> String = { "" },
    replayCapabilityDetailProvider: () -> String = { "" },
    isReplayActiveProvider: () -> Boolean = { false },
    onBackToLive: () -> Unit = {},
    catchupSupportedProvider: () -> Boolean = { false },
    onReplayUnsupported: () -> Unit = {},
    catchupMaxHoursProvider: () -> Int = { 24 },
    onReplayByBackMinutes: (Int) -> Unit = {},
    onReplayByProgramme: (Long, Long) -> Unit = { _, _ -> },
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
    val rootFocusRequester = remember { FocusRequester() }
    val focusMenuEpg = remember { FocusRequester() }
    val focusMenuVideo = remember { FocusRequester() }
    val focusMenuAudio = remember { FocusRequester() }
    val focusMenuStream = remember { FocusRequester() }
    var lastSubPanel by remember { mutableStateOf(LeanbackQuickPanelSubPanel.None) }
    val showBottomChrome = subPanel != LeanbackQuickPanelSubPanel.Epg

    LaunchedEffect(Unit) {
        autoCloseState.active()
        delay(16)
        focusMenuEpg.requestFocus()
    }

    LaunchedEffect(subPanel) {
        val previous = lastSubPanel
        lastSubPanel = subPanel
        if (subPanel == LeanbackQuickPanelSubPanel.None && previous != LeanbackQuickPanelSubPanel.None) {
            delay(48)
            when (previous) {
                LeanbackQuickPanelSubPanel.Epg -> focusMenuEpg.requestFocus()
                LeanbackQuickPanelSubPanel.ReplayDetail -> focusMenuEpg.requestFocus()
                LeanbackQuickPanelSubPanel.VideoDetail -> focusMenuVideo.requestFocus()
                LeanbackQuickPanelSubPanel.AudioDetail -> focusMenuAudio.requestFocus()
                LeanbackQuickPanelSubPanel.StreamDetail -> focusMenuStream.requestFocus()
                LeanbackQuickPanelSubPanel.None -> Unit
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
            .focusRequester(rootFocusRequester)
            .focusable()
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
            }
            .onPreviewKeyEvent {
                if (it.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                val isBack =
                    it.key == Key.Back ||
                        it.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK
                if (!isBack) return@onPreviewKeyEvent false
                if (subPanel != LeanbackQuickPanelSubPanel.None) {
                    onSubPanelChange(LeanbackQuickPanelSubPanel.None)
                } else {
                    onClose()
                }
                true
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
                        replaySupportedProvider = catchupSupportedProvider,
                        autoCloseState = autoCloseState,
                        onSelectProgramme = { programme ->
                            if (
                                catchupSupportedProvider() &&
                                programme.endAt in 1 until System.currentTimeMillis()
                            ) {
                                onReplayByProgramme(programme.startAt, programme.endAt)
                                onSubPanelChange(LeanbackQuickPanelSubPanel.None)
                            }
                        },
                    )

                LeanbackQuickPanelSubPanel.ReplayDetail ->
                    LeanbackQuickPanelReplayRightSheet(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(
                                end = sideStartPad,
                                top = sideTopPad,
                                bottom = bottomInsetRight,
                            )
                            .fillMaxHeight()
                            .fillMaxWidth(0.336f),
                        capabilityLabelProvider = replayCapabilityProvider,
                        capabilityDetailProvider = replayCapabilityDetailProvider,
                        maxHoursProvider = catchupMaxHoursProvider,
                        replaySupportedProvider = catchupSupportedProvider,
                        onReplayByBackMinutes = {
                            onReplayByBackMinutes(it)
                            onSubPanelChange(LeanbackQuickPanelSubPanel.None)
                        },
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
                        playbackStatusProvider = playbackStatusProvider,
                    )

                    LeanbackPanelPlayerInfo(
                        metadataProvider = videoPlayerMetadataProvider,
                    )

                    val menuListState = rememberTvLazyListState()
                    val showMultiLineMenuItem = currentIptvProvider().urlList.size > 1
                    val menuSlots = remember(showMultiLineMenuItem, isReplayActiveProvider(), catchupSupportedProvider()) {
                        buildList {
                            add(QuickPanelBottomMenuSlot.Epg)
                            add(QuickPanelBottomMenuSlot.Replay)
                            if (isReplayActiveProvider()) add(QuickPanelBottomMenuSlot.BackLive)
                            if (showMultiLineMenuItem) add(QuickPanelBottomMenuSlot.MultiLine)
                            add(QuickPanelBottomMenuSlot.AspectRatio)
                            add(QuickPanelBottomMenuSlot.Video)
                            add(QuickPanelBottomMenuSlot.Audio)
                            add(QuickPanelBottomMenuSlot.Stream)
                            add(QuickPanelBottomMenuSlot.Home)
                        }
                    }
                    TvLazyRow(
                        state = menuListState,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(QuickPanelMenuItemSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                        contentPadding = PaddingValues(
                            start = QuickPanelBottomMenuRowStartInset,
                            end = childPadding.end,
                        ),
                    ) {
                        items(
                            menuSlots,
                            key = { it },
                        ) { slot ->
                            when (slot) {
                                QuickPanelBottomMenuSlot.Epg ->
                                    LeanbackQuickPanelButton(
                                        buttonFocusRequester = focusMenuEpg,
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

                                QuickPanelBottomMenuSlot.MultiLine ->
                                    LeanbackQuickPanelActionMultipleChannels(
                                        currentIptvProvider = currentIptvProvider,
                                        currentIptvUrlIdxProvider = currentIptvUrlIdxProvider,
                                        onIptvUrlIdxChange = onIptvUrlIdxChange,
                                        onUserAction = { autoCloseState.active() },
                                    )

                                QuickPanelBottomMenuSlot.Replay ->
                                    LeanbackQuickPanelButton(
                                        leadingIcon = Icons.Filled.Schedule,
                                        titleProvider = {
                                            if (catchupSupportedProvider()) "支持回看" else "不支持"
                                        },
                                        titleMaxLines = 1,
                                        titleOverflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = (190f * QuickPanelMenuLayoutFactor).dp),
                                        onSelect = {
                                            if (catchupSupportedProvider()) {
                                                onSubPanelChange(
                                                    if (subPanel == LeanbackQuickPanelSubPanel.ReplayDetail) {
                                                        LeanbackQuickPanelSubPanel.None
                                                    } else {
                                                        LeanbackQuickPanelSubPanel.ReplayDetail
                                                    },
                                                )
                                            }
                                            else onReplayUnsupported()
                                        },
                                    )

                                QuickPanelBottomMenuSlot.BackLive ->
                                    LeanbackQuickPanelButton(
                                        leadingIcon = Icons.Filled.LiveTv,
                                        titleProvider = { "返回直播" },
                                        onSelect = onBackToLive,
                                    )

                                QuickPanelBottomMenuSlot.AspectRatio ->
                                    LeanbackQuickPanelActionVideoAspectRatio(
                                        videoPlayerAspectRatioProvider = videoPlayerAspectRatioProvider,
                                        onChangeVideoPlayerAspectRatio = onChangeVideoPlayerAspectRatio,
                                    )

                                QuickPanelBottomMenuSlot.Video ->
                                    LeanbackQuickPanelButton(
                                        buttonFocusRequester = focusMenuVideo,
                                        leadingIcon = Icons.Filled.Videocam,
                                        titleProvider = {
                                            formatQuickPanelVideoButtonLabel(videoPlayerMetadataProvider())
                                        },
                                        titleMaxLines = 1,
                                        titleOverflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = (220f * QuickPanelMenuLayoutFactor).dp),
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

                                QuickPanelBottomMenuSlot.Audio ->
                                    LeanbackQuickPanelButton(
                                        buttonFocusRequester = focusMenuAudio,
                                        leadingIcon = Icons.Filled.MusicNote,
                                        titleProvider = {
                                            formatQuickPanelAudioButtonLabel(videoPlayerMetadataProvider())
                                        },
                                        titleMaxLines = 1,
                                        titleOverflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = (240f * QuickPanelMenuLayoutFactor).dp),
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

                                QuickPanelBottomMenuSlot.Stream ->
                                    LeanbackQuickPanelButton(
                                        buttonFocusRequester = focusMenuStream,
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

                                QuickPanelBottomMenuSlot.Home ->
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
    }
}

@Composable
private fun LeanbackQuickPanelButton(
    modifier: Modifier = Modifier,
    /** 由父级持有时可从详情返回后 `requestFocus()`，保持该项为当前焦点 */
    buttonFocusRequester: FocusRequester? = null,
    leadingIcon: ImageVector? = null,
    titleProvider: () -> String,
    subtitleProvider: (() -> String)? = null,
    titleMaxLines: Int = 2,
    titleOverflow: TextOverflow = TextOverflow.Clip,
    onSelect: () -> Unit = {},
) {
    val defaultFocusRequester = remember { FocusRequester() }
    val focusRequester = buttonFocusRequester ?: defaultFocusRequester
    var isFocused by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val menuContentColor =
        if (isFocused) scheme.inverseOnSurface
        else scheme.onSurface.copy(alpha = 0.88f)
    val menuSubtitleColor =
        if (isFocused) scheme.inverseOnSurface.copy(alpha = 0.78f)
        else scheme.onSurfaceVariant.copy(alpha = 0.9f)
    androidx.tv.material3.Button(
        onClick = { },
        shape = ButtonDefaults.shape(
            shape = MaterialTheme.shapes.extraSmall,
            focusedShape = MaterialTheme.shapes.extraSmall,
            pressedShape = MaterialTheme.shapes.extraSmall,
            disabledShape = MaterialTheme.shapes.extraSmall,
            focusedDisabledShape = MaterialTheme.shapes.extraSmall,
        ),
        // TV Material3 默认 focusedScale=1.1f，聚焦会比未聚焦大约 10%；此处全部锁 1f，避免「选中变大」
        scale = ButtonDefaults.scale(
            scale = 1f,
            focusedScale = 1f,
            pressedScale = 1f,
            disabledScale = 1f,
            focusedDisabledScale = 1f,
        ),
        border = ButtonDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
            pressedBorder = Border.None,
            disabledBorder = Border.None,
            focusedDisabledBorder = Border.None,
        ),
        glow = ButtonDefaults.glow(
            glow = Glow.None,
            focusedGlow = Glow.None,
            pressedGlow = Glow.None,
        ),
        contentPadding = PaddingValues(
            horizontal = (16f * QuickPanelMenuLayoutFactor).dp,
            vertical = (8f * QuickPanelMenuLayoutFactor).dp,
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
            modifier = Modifier.padding(
                horizontal = QuickPanelMenuButtonInnerPaddingH,
                vertical = QuickPanelMenuButtonInnerPaddingV,
            ),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(QuickPanelMenuIconSize),
                    tint = menuContentColor,
                )
                Spacer(Modifier.width(QuickPanelMenuIconTextGap))
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = titleProvider(),
                    textAlign = TextAlign.Center,
                    maxLines = titleMaxLines,
                    overflow = titleOverflow,
                    style = MaterialTheme.typography.titleMedium,
                    color = menuContentColor,
                )
                if (subtitleProvider != null) {
                    Text(
                        text = subtitleProvider(),
                        style = MaterialTheme.typography.labelMedium,
                        color = menuSubtitleColor,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.padding(top = (2f * QuickPanelMenuLayoutFactor).dp),
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
