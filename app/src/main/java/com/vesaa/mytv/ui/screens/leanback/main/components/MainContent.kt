package com.vesaa.mytv.ui.screens.leanback.main.components

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.vesaa.mytv.data.entities.EpgList
import com.vesaa.mytv.data.IptvFavoriteMigration
import com.vesaa.mytv.data.entities.Iptv
import com.vesaa.mytv.data.entities.IptvFavoriteEntry
import com.vesaa.mytv.data.entities.IptvGroup
import com.vesaa.mytv.data.entities.IptvGroupList
import com.vesaa.mytv.data.entities.IptvGroupList.Companion.iptvIdx
import com.vesaa.mytv.data.entities.IptvGroupList.Companion.iptvList
import com.vesaa.mytv.data.entities.IptvList
import com.vesaa.mytv.data.entities.withoutHiddenGroupNames
import com.vesaa.mytv.ui.screens.leanback.components.LeanbackVisible
import com.vesaa.mytv.ui.screens.leanback.monitor.LeanbackMonitorScreen
import com.vesaa.mytv.ui.screens.leanback.panel.LeanbackPanelChannelNoSelectScreen
import com.vesaa.mytv.ui.screens.leanback.panel.LeanbackPanelDateTimeScreen
import com.vesaa.mytv.ui.screens.leanback.panel.rememberLeanbackPanelChannelNoSelectState
import com.vesaa.mytv.ui.screens.leanback.quickpanel.QuickPanelSplitMode
import com.vesaa.mytv.ui.screens.leanback.quickpanel.LeanbackQuickPanelSubPanel
import com.vesaa.mytv.ui.screens.leanback.settings.LeanbackSettingsScreen
import com.vesaa.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import com.vesaa.mytv.ui.screens.leanback.toast.LeanbackToastState
import com.vesaa.mytv.ui.screens.leanback.update.LeanbackUpdateScreen
import com.vesaa.mytv.ui.screens.leanback.video.LeanbackVideoPlayerState
import com.vesaa.mytv.ui.screens.leanback.video.LeanbackVideoScreen
import com.vesaa.mytv.ui.screens.leanback.video.rememberLeanbackVideoPlayerState
import com.vesaa.mytv.ui.utils.SP
import com.vesaa.mytv.ui.utils.handleLeanbackDragGestures
import com.vesaa.mytv.ui.utils.handleLeanbackKeyEvents
import com.vesaa.mytv.utils.IptvCatchup

@Composable
fun LeanbackMainContent(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
    iptvGroupList: IptvGroupList = IptvGroupList(),
    epgList: EpgList = EpgList(),
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
) {
    val configuration = LocalConfiguration.current

    val defaultAspectRatioProvider = {
        when (settingsViewModel.videoPlayerAspectRatio) {
            SP.VideoPlayerAspectRatio.ORIGINAL -> null
            SP.VideoPlayerAspectRatio.SIXTEEN_NINE -> 16f / 9f
            SP.VideoPlayerAspectRatio.FOUR_THREE -> 4f / 3f
            SP.VideoPlayerAspectRatio.AUTO -> {
                configuration.screenHeightDp.toFloat() / configuration.screenWidthDp.toFloat()
            }
        }
    }
    val videoPlayerState = rememberLeanbackVideoPlayerState(
        defaultAspectRatioProvider = defaultAspectRatioProvider,
    )
    val splitPane1PlayerState = rememberLeanbackVideoPlayerState(
        defaultAspectRatioProvider = defaultAspectRatioProvider,
    )
    val splitPane2PlayerState = rememberLeanbackVideoPlayerState(
        defaultAspectRatioProvider = defaultAspectRatioProvider,
    )
    val splitPane3PlayerState = rememberLeanbackVideoPlayerState(
        defaultAspectRatioProvider = defaultAspectRatioProvider,
    )
    val mainContentState = rememberLeanbackMainContentState(
        videoPlayerState = videoPlayerState,
        iptvGroupList = iptvGroupList,
    )
    val favoritesOnlyUi =
        settingsViewModel.iptvChannelFavoriteEnable && settingsViewModel.iptvChannelFavoritesOnlyMode
    val currentFavorites = settingsViewModel.currentSourceFavoriteEntries
    val expandedEntries = settingsViewModel.iptvExpandedChannelEntries
    val expandedHeaderMap = remember(expandedEntries) {
        expandedEntries.associateBy(
            keySelector = { IptvFavoriteEntry.stableKeyFrom(it.urlList, it.channelName) },
            valueTransform = { it.playbackRequestHeaders.trim().takeIf { h -> h.isNotEmpty() } },
        )
    }
    val resolveExtraStreamHeaders: (Iptv) -> String? = remember(
        currentFavorites,
        expandedHeaderMap,
    ) {
        { iptv ->
            currentFavorites
                .find { e -> IptvFavoriteEntry.stableKeyFrom(iptv.urlList, iptv.channelName) == e.stableKey() }
                ?.playbackRequestHeaders?.trim()?.takeIf { it.isNotEmpty() }
                ?: expandedHeaderMap[IptvFavoriteEntry.stableKeyFrom(iptv.urlList, iptv.channelName)]
        }
    }
    val iptvGroupListFiltered = remember(iptvGroupList, settingsViewModel.iptvHiddenGroupFilterEpoch) {
        iptvGroupList.withoutHiddenGroupNames(SP.iptvHiddenGroupNames)
    }
    val mergedIptvGroupList = remember(
        iptvGroupListFiltered,
        settingsViewModel.iptvExpandedChannelEnable,
        expandedEntries,
    ) {
        if (!settingsViewModel.iptvExpandedChannelEnable || expandedEntries.isEmpty()) {
            iptvGroupListFiltered
        } else {
            IptvGroupList(
                iptvGroupListFiltered + IptvGroup(
                    name = IptvGroup.EXPANDED_GROUP_NAME,
                    iptvList = IptvList(expandedEntries.map { it.toIptv() }),
                )
            )
        }
    }
    val uiIptvGroupList = if (favoritesOnlyUi) IptvGroupList() else mergedIptvGroupList
    val channelOrderList =
        if (favoritesOnlyUi) currentFavorites.map { it.toIptv() }
        else uiIptvGroupList.iptvList
    var splitMode by remember { mutableStateOf(QuickPanelSplitMode.Off) }
    var splitFocusedPane by remember { mutableIntStateOf(0) }
    var splitActivePane by remember { mutableIntStateOf(0) }
    var splitPaneStates by remember { mutableStateOf(List(4) { SplitPanePlaybackState() }) }
    val splitPlayerStateAt: (Int) -> LeanbackVideoPlayerState = remember(
        splitPane1PlayerState,
        splitPane2PlayerState,
        splitPane3PlayerState,
    ) {
        { pane ->
            when (pane) {
                1 -> splitPane1PlayerState
                2 -> splitPane2PlayerState
                3 -> splitPane3PlayerState
                else -> videoPlayerState
            }
        }
    }
    val playReplayWindow = remember(mainContentState, resolveExtraStreamHeaders) {
        { targetIptv: Iptv, rawStartMs: Long, rawEndMs: Long, replayHint: String ->
            runCatching {
                val iptv = targetIptv
                if (iptv.urlList.isEmpty()) {
                    LeanbackToastState.I.showToast("当前频道无可用播放地址")
                    return@runCatching
                }
                val maxHours = IptvCatchup.maxCatchupHours(iptv)
                val window = IptvCatchup.clampWindow(
                    rawStartMs = rawStartMs,
                    rawEndMs = rawEndMs,
                    maxHours = maxHours,
                )
                if (window == null) {
                    LeanbackToastState.I.showToast("回看时间无效或超出范围")
                    return@runCatching
                }
                val idx = mainContentState.currentIptvUrlIdx
                    .coerceIn(0, (iptv.urlList.size - 1).coerceAtLeast(0))
                val baseUrl = iptv.urlList.getOrNull(idx).orEmpty()
                if (baseUrl.isBlank()) {
                    LeanbackToastState.I.showToast("当前频道播放地址为空")
                    return@runCatching
                }
                val replayUrl = IptvCatchup.buildCatchupUrlWithFallback(iptv, baseUrl, window)
                if (replayUrl.isBlank()) {
                    LeanbackToastState.I.showToast("该源未提供回看地址模板")
                    return@runCatching
                }
                if (mainContentState.currentIptv != iptv) {
                    mainContentState.changeCurrentIptv(
                        iptv = iptv,
                        streamRequestHeaders = resolveExtraStreamHeaders(iptv),
                    )
                }
                mainContentState.playCurrentIptvWithOverrideUrl(
                    overrideUrl = replayUrl,
                    streamRequestHeaders = resolveExtraStreamHeaders(iptv),
                    replayHint = replayHint,
                )
                LeanbackToastState.I.showToast("已开始回看")
            }.onFailure {
                LeanbackToastState.I.showToast("回看请求失败，请重试")
            }
            Unit
        }
    }
    val playbackStatusText =
        if (mainContentState.playbackMode == LeanbackMainContentState.PlaybackMode.REPLAY) {
            mainContentState.replayHint.ifBlank { "回看中" }
        } else {
            ""
        }
    val replayCapabilityDetailText = when (IptvCatchup.capabilityOf(mainContentState.currentIptv)) {
        IptvCatchup.Capability.SUPPORTED_BY_TEMPLATE ->
            "模板命中：catchup/catchup-source"
        IptvCatchup.Capability.SUPPORTED_BY_DVR_URL ->
            "DVR命中：URL包含 /dvr/ 或时移参数"
        IptvCatchup.Capability.UNSUPPORTED ->
            "未命中模板或DVR规则"
    }
    var lastReplayError by remember { mutableStateOf("") }
    LaunchedEffect(mainContentState.playbackMode, videoPlayerState.error) {
        if (mainContentState.playbackMode != LeanbackMainContentState.PlaybackMode.REPLAY) {
            lastReplayError = ""
            return@LaunchedEffect
        }
        val err = videoPlayerState.error?.trim().orEmpty()
        if (err.isBlank() || err == lastReplayError) return@LaunchedEffect
        lastReplayError = err
        LeanbackToastState.I.showToast(replayErrorTip(err))
    }
    val clearExtraSplitPanes = {
        splitPane1PlayerState.stop()
        splitPane2PlayerState.stop()
        splitPane3PlayerState.stop()
        splitPaneStates = splitPaneStates.mapIndexed { idx, state ->
            if (idx == 0) state else SplitPanePlaybackState()
        }
    }
    val exitSplitMode = {
        splitMode = QuickPanelSplitMode.Off
        splitFocusedPane = 0
        splitActivePane = 0
        clearExtraSplitPanes()
    }
    val splitPaneCount = if (splitMode == QuickPanelSplitMode.FourGrid) 4 else 2
    val isSplitMode = splitMode != QuickPanelSplitMode.Off
    LaunchedEffect(isSplitMode, splitPaneCount, splitActivePane) {
        if (!isSplitMode) {
            videoPlayerState.applyMuted(false)
            splitPane1PlayerState.applyMuted(true)
            splitPane2PlayerState.applyMuted(true)
            splitPane3PlayerState.applyMuted(true)
            return@LaunchedEffect
        }
        val active = splitActivePane.coerceIn(0, splitPaneCount - 1)
        videoPlayerState.applyMuted(active != 0)
        splitPane1PlayerState.applyMuted(active != 1)
        splitPane2PlayerState.applyMuted(active != 2 || splitPaneCount < 3)
        splitPane3PlayerState.applyMuted(active != 3 || splitPaneCount < 4)
    }
    val resolveAdjacentChannel: (Iptv, Boolean) -> Iptv = { current: Iptv, next: Boolean ->
        if (channelOrderList.isEmpty()) {
            Iptv()
        } else {
            val i = channelOrderList.indexOf(current).let { if (it < 0) 0 else it }
            if (next) {
                channelOrderList.getOrElse(i + 1) { channelOrderList.first() }
            } else {
                channelOrderList.getOrElse(i - 1) { channelOrderList.last() }
            }
        }
    }
    val playIptvInPane: (Int, Iptv, String?) -> Unit = { paneIndex, iptv, streamHeaders ->
        if (iptv.urlList.isEmpty()) {
            LeanbackToastState.I.showToast("当前频道无可用播放地址")
        } else {
            val headers = streamHeaders?.trim()?.takeIf { it.isNotEmpty() } ?: resolveExtraStreamHeaders(iptv)
            if (paneIndex <= 0) {
                mainContentState.changeCurrentIptv(
                    iptv = iptv,
                    streamRequestHeaders = headers,
                )
            } else {
                val currentFps = videoPlayerState.metadata.videoRenderedFps
                val activatedPaneCount = splitPaneStates.drop(1).count { it.iptv.urlList.isNotEmpty() }
                if (
                    splitMode == QuickPanelSplitMode.FourGrid &&
                    paneIndex >= 2 &&
                    activatedPaneCount >= 2 &&
                    currentFps in 0.1f..24f
                ) {
                    LeanbackToastState.I.showToast("当前设备性能不足，无法激活该子屏")
                } else {
                    val url = iptv.urlList.firstOrNull().orEmpty()
                    if (url.isBlank()) {
                        LeanbackToastState.I.showToast("当前频道播放地址为空")
                    } else {
                        splitPlayerStateAt(paneIndex).prepare(url, headers)
                        splitPaneStates = splitPaneStates.mapIndexed { idx, old ->
                            if (idx == paneIndex) {
                                old.copy(
                                    iptv = iptv,
                                    urlIdx = 0,
                                    streamHeaders = headers,
                                )
                            } else old
                        }
                    }
                }
            }
        }
    }
    val changeActivePaneChannel: (Boolean) -> Unit = { isUp ->
        if (!isSplitMode) {
            if (isUp) {
                if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentIptvToNext()
                else mainContentState.changeCurrentIptvToPrev()
            } else {
                if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentIptvToPrev()
                else mainContentState.changeCurrentIptvToNext()
            }
        } else {
            val targetPane = splitActivePane.coerceIn(0, splitPaneCount - 1)
            if (targetPane == 0) {
                if (isUp) {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentIptvToNext()
                    else mainContentState.changeCurrentIptvToPrev()
                } else {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentIptvToPrev()
                    else mainContentState.changeCurrentIptvToNext()
                }
            } else {
                val current = splitPaneStates[targetPane].iptv
                val base = if (current.urlList.isNotEmpty()) current else mainContentState.currentIptv
                val next = resolveAdjacentChannel(base, !isUp)
                playIptvInPane(targetPane, next, resolveExtraStreamHeaders(next))
            }
        }
    }
    val changeActivePaneLine: (Boolean) -> Unit = { isLeft ->
        val pane = if (isSplitMode) splitActivePane.coerceIn(0, splitPaneCount - 1) else 0
        if (pane == 0) {
            if (mainContentState.currentIptv.urlList.size > 1) {
                mainContentState.changeCurrentIptv(
                    iptv = mainContentState.currentIptv,
                    urlIdx = if (isLeft) mainContentState.currentIptvUrlIdx - 1 else mainContentState.currentIptvUrlIdx + 1,
                )
            }
        } else {
            val paneState = splitPaneStates[pane]
            if (paneState.iptv.urlList.size > 1) {
                val size = paneState.iptv.urlList.size
                val nextIdx = if (isLeft) paneState.urlIdx - 1 else paneState.urlIdx + 1
                val normalizedIdx = (nextIdx + size) % size
                val url = paneState.iptv.urlList[normalizedIdx]
                splitPlayerStateAt(pane).prepare(url, paneState.streamHeaders ?: resolveExtraStreamHeaders(paneState.iptv))
                splitPaneStates = splitPaneStates.mapIndexed { idx, old ->
                    if (idx == pane) old.copy(urlIdx = normalizedIdx) else old
                }
            }
        }
    }
    LaunchedEffect(mainContentState.currentIptv, mainContentState.currentIptvUrlIdx) {
        splitPaneStates = splitPaneStates.mapIndexed { idx, old ->
            if (idx == 0) {
                old.copy(
                    iptv = mainContentState.currentIptv,
                    urlIdx = mainContentState.currentIptvUrlIdx,
                )
            } else old
        }
    }

    val onIptvGroupLongPressHide: (IptvGroup) -> Unit = { group ->
        val isSpecial = group.name == IptvGroup.FAVORITE_GROUP_NAME ||
            group.name == IptvGroup.EXPANDED_GROUP_NAME
        if (group.name.isNotBlank() && !isSpecial) {
            SP.iptvHiddenGroupNames = SP.iptvHiddenGroupNames + group.name
            settingsViewModel.bumpIptvHiddenGroupFilterEpoch()
            LeanbackToastState.I.showToast("已隐藏分组：${group.name}")
        }
    }
    val onIptvGroupLongPressAddToFavorites: (IptvGroup) -> Unit = addToFav@{ group ->
        if (!settingsViewModel.iptvChannelFavoriteEnable) {
            LeanbackToastState.I.showToast("请先在设置 → 精选设置 中启用精选")
            return@addToFav
        }
        val added = settingsViewModel.addIptvGroupToFavorites(group.iptvList)
        if (added > 0) {
            LeanbackToastState.I.showToast("已添加到精选频道：$added 个")
        } else {
            LeanbackToastState.I.showToast("该分组频道已全部在精选中")
        }
    }

    LaunchedEffect(iptvGroupList) {
        IptvFavoriteMigration.runOnceIfNeeded(iptvGroupList)
        settingsViewModel.reloadFavoriteEntriesFromDisk()
    }

    LaunchedEffect(
        iptvGroupList,
        settingsViewModel.iptvChannelFavoriteEnable,
        settingsViewModel.iptvChannelFavoritesOnlyMode,
        settingsViewModel.iptvHiddenGroupFilterEpoch,
    ) {
        mainContentState.updateIptvGroupList(uiIptvGroupList)
    }

    LaunchedEffect(
        uiIptvGroupList,
        favoritesOnlyUi,
        settingsViewModel.iptvHiddenGroupFilterEpoch,
    ) {
        if (favoritesOnlyUi) return@LaunchedEffect
        val visible = uiIptvGroupList.iptvList
        if (visible.isEmpty()) {
            if (mainContentState.currentIptv.urlList.isNotEmpty()) {
                mainContentState.changeCurrentIptv(Iptv())
            }
            return@LaunchedEffect
        }
        val cur = mainContentState.currentIptv
        if (cur !in visible) {
            // 列表晚于首帧就绪时，init 可能在空列表上选了空 Iptv；此处若用 first() 会丢掉「上次频道」。
            // 按 SP 中保存的扁平序号恢复，并与换台逻辑一致（隐藏分组后序号已钳制在可见列表范围内）。
            val idx = SP.iptvLastIptvIdx.coerceIn(0, (visible.size - 1).coerceAtLeast(0))
            val target = visible[idx]
            mainContentState.changeCurrentIptv(
                iptv = target,
                streamRequestHeaders = resolveExtraStreamHeaders(target),
                reason = LeanbackMainContentState.ChangeReason.INIT,
            )
        }
    }

    LaunchedEffect(
        iptvGroupList,
        favoritesOnlyUi,
        currentFavorites,
        settingsViewModel.iptvHiddenGroupFilterEpoch,
        settingsViewModel.iptvExpandedChannelEnable,
        settingsViewModel.iptvExpandedChannelEntries,
    ) {
        val order =
            if (favoritesOnlyUi) currentFavorites.map { it.toIptv() }
            else uiIptvGroupList.iptvList
        mainContentState.syncChannelNavigation(
            order = order,
            streamHeadersForIptv = resolveExtraStreamHeaders,
        )
    }

    LaunchedEffect(
        settingsViewModel.iptvChannelFavoriteEnable,
        settingsViewModel.iptvChannelFavoritesOnlyMode,
        currentFavorites,
    ) {
        if (!settingsViewModel.iptvChannelFavoriteEnable ||
            !settingsViewModel.iptvChannelFavoritesOnlyMode
        ) {
            return@LaunchedEffect
        }
        val entries = currentFavorites
        if (entries.isEmpty()) return@LaunchedEffect
        val cur = mainContentState.currentIptv
        val curKey = IptvFavoriteEntry.stableKeyFrom(cur.urlList, cur.channelName)
        if (entries.none { it.stableKey() == curKey }) {
            val first = entries.first()
            mainContentState.changeCurrentIptv(
                first.toIptv(),
                streamRequestHeaders = first.playbackRequestHeaders.trim().takeIf { it.isNotEmpty() },
            )
        }
    }

    var sessionOnboardingDismissed by remember { mutableStateOf(false) }
    /** 仅直播源未配置时引导进入设置首页；节目单为可选，不参与引导 */
    val needsLeanbackOnboarding = settingsViewModel.iptvSourceUrl.isBlank()

    LaunchedEffect(settingsViewModel.iptvSourceUrl) {
        val needs = settingsViewModel.iptvSourceUrl.isBlank()
        if (!needs) {
            sessionOnboardingDismissed = false
            return@LaunchedEffect
        }
        if (sessionOnboardingDismissed) return@LaunchedEffect
        mainContentState.isSettingsVisible = true
        mainContentState.isQuickPanelVisible = false
    }

    LaunchedEffect(SP.iptvSourceUrl, iptvGroupList.iptvList.size) {
        if (SP.iptvSourceUrl.isNotBlank() && iptvGroupList.iptvList.isNotEmpty()) {
            mainContentState.isQuickPanelVisible = false
        }
    }

    val panelChannelNoSelectState = rememberLeanbackPanelChannelNoSelectState(
        onChannelNoConfirm = {
            val channelNo = it.toInt() - 1
            val order = channelOrderList
            if (channelNo in order.indices) {
                val iptv = order[channelNo]
                val header = if (favoritesOnlyUi) {
                    currentFavorites.find { e ->
                        IptvFavoriteEntry.stableKeyFrom(iptv.urlList, iptv.channelName) == e.stableKey()
                    }?.playbackRequestHeaders?.trim()?.takeIf { h -> h.isNotEmpty() }
                } else {
                    resolveExtraStreamHeaders(iptv)
                }
                val targetPane = if (isSplitMode) splitFocusedPane.coerceIn(0, splitPaneCount - 1) else 0
                playIptvInPane(targetPane, iptv, header)
            }
        }
    )

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // 防止切换到其他界面时焦点丢失
        // TODO 换一个更好的解决方案
        while (true) {
            if (!mainContentState.isPanelVisible
                && !mainContentState.isSettingsVisible
                && !mainContentState.isQuickPanelVisible
                && !isSplitMode
            ) {
                focusRequester.requestFocus()
            }
            delay(100)
        }
    }
    LaunchedEffect(splitMode) {
        if (splitMode != QuickPanelSplitMode.Off) {
            delay(32)
            runCatching { focusRequester.requestFocus() }
        }
    }

    val videoFocusEnabled =
        !mainContentState.isPanelVisible &&
            !mainContentState.isSettingsVisible &&
            !mainContentState.isQuickPanelVisible &&
            !isSplitMode

    val liveDpadVerticalChannel: ((isUp: Boolean) -> Unit)? = remember(
        videoFocusEnabled,
        panelChannelNoSelectState.channelNo,
        settingsViewModel.iptvChannelChangeFlip,
    ) {
        if (videoFocusEnabled && panelChannelNoSelectState.channelNo.isEmpty()) {
            { isUp: Boolean ->
                if (isUp) {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentIptvToNext()
                    else mainContentState.changeCurrentIptvToPrev()
                } else {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentIptvToPrev()
                    else mainContentState.changeCurrentIptvToNext()
                }
            }
        } else {
            null
        }
    }

    LeanbackBackPressHandledArea(
        modifier = modifier,
        onLiveDpadVertical = liveDpadVerticalChannel,
        onBackPressed = {
            if (mainContentState.isPanelVisible) mainContentState.isPanelVisible = false
            else if (mainContentState.isSettingsVisible) mainContentState.isSettingsVisible = false
            else if (mainContentState.isQuickPanelVisible) {
                if (mainContentState.quickPanelSubPanel != LeanbackQuickPanelSubPanel.None) {
                    mainContentState.quickPanelSubPanel = LeanbackQuickPanelSubPanel.None
                } else {
                    mainContentState.isQuickPanelVisible = false
                }
            }
            else onBackPressed()
        },
    ) {
        if (isSplitMode) {
            LeanbackSplitPlaybackScreen(
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .focusable(),
                mode = splitMode,
                paneStates = listOf(
                    videoPlayerState,
                    splitPane1PlayerState,
                    splitPane2PlayerState,
                    splitPane3PlayerState,
                ),
                focusedPane = splitFocusedPane.coerceIn(0, splitPaneCount - 1),
                activePane = splitActivePane.coerceIn(0, splitPaneCount - 1),
                onFocusedPaneChange = {
                    val pane = it.coerceIn(0, splitPaneCount - 1)
                    splitFocusedPane = pane
                    splitActivePane = pane
                },
                onActivePaneChange = { splitActivePane = it.coerceIn(0, splitPaneCount - 1) },
                onOpenQuickPanelFromSafeArea = { mainContentState.isQuickPanelVisible = true },
                onOpenChannelPanelForFocused = {
                    splitFocusedPane = splitFocusedPane.coerceIn(0, splitPaneCount - 1)
                    mainContentState.isPanelVisible = true
                },
                onChannelUp = { changeActivePaneChannel(true) },
                onChannelDown = { changeActivePaneChannel(false) },
                onLineLeft = { changeActivePaneLine(true) },
                onLineRight = { changeActivePaneLine(false) },
            )
        } else {
            LeanbackVideoScreen(
                state = videoPlayerState,
                showMetadataProvider = { false },
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .focusable(videoFocusEnabled)
                    .handleLeanbackKeyEvents(
                        onLeft = { changeActivePaneLine(true) },
                        onRight = { changeActivePaneLine(false) },
                        onSelect = { mainContentState.isPanelVisible = true },
                        onSettings = { mainContentState.isQuickPanelVisible = true },
                        onNumber = {
                            if (settingsViewModel.iptvChannelNoSelectEnable) {
                                panelChannelNoSelectState.input(it)
                            }
                        },
                        onLongDown = { mainContentState.isQuickPanelVisible = true },
                    )
                    .handleLeanbackDragGestures(
                        onSwipeDown = { changeActivePaneChannel(false) },
                        onSwipeUp = { changeActivePaneChannel(true) },
                        onSwipeRight = { changeActivePaneLine(true) },
                        onSwipeLeft = { changeActivePaneLine(false) },
                    ),
            )
        }

        CompositionLocalProvider(
            LocalDensity provides Density(
                density = LocalDensity.current.density * settingsViewModel.uiDensityScaleRatio,
                fontScale = LocalDensity.current.fontScale * settingsViewModel.uiFontScaleRatio,
            )
        ) {
            LeanbackVisible({
                !mainContentState.isTempPanelVisible
                        && !mainContentState.isSettingsVisible
                        && !mainContentState.isPanelVisible
                        && !mainContentState.isQuickPanelVisible
                        && panelChannelNoSelectState.channelNo.isEmpty()
            }) {
                LeanbackPanelDateTimeScreen(
                    showModeProvider = { settingsViewModel.uiTimeShowMode }
                )
            }

            LeanbackPanelChannelNoSelectScreen(
                channelNoProvider = { panelChannelNoSelectState.channelNo }
            )

            LeanbackMainEpgSurfaces(
                epgList = epgList,
                mainContentState = mainContentState,
                uiIptvGroupList = uiIptvGroupList,
                channelOrderList = channelOrderList,
                currentFavorites = currentFavorites,
                settingsViewModel = settingsViewModel,
                videoPlayerState = videoPlayerState,
                playReplayWindow = playReplayWindow,
                playbackStatusText = playbackStatusText,
                replayCapabilityDetailText = replayCapabilityDetailText,
                onIptvGroupLongPressHide = onIptvGroupLongPressHide,
                onIptvGroupLongPressAddToFavorites = onIptvGroupLongPressAddToFavorites,
                onIptvSelected = { iptv, streamHeaders ->
                    val pane = if (isSplitMode) splitFocusedPane.coerceIn(0, splitPaneCount - 1) else 0
                    val headers = streamHeaders ?: resolveExtraStreamHeaders(iptv)
                    playIptvInPane(pane, iptv, headers)
                },
                splitModeProvider = { splitMode },
                onSplitModeChange = { newMode ->
                    when (newMode) {
                        QuickPanelSplitMode.Off -> exitSplitMode()
                        QuickPanelSplitMode.LeftRight -> {
                            splitMode = newMode
                            splitFocusedPane = splitFocusedPane.coerceIn(0, 1)
                            splitActivePane = splitActivePane.coerceIn(0, 1)
                            splitPane2PlayerState.stop()
                            splitPane3PlayerState.stop()
                            splitPaneStates = splitPaneStates.mapIndexed { idx, old ->
                                if (idx <= 1) old else SplitPanePlaybackState()
                            }
                        }
                        QuickPanelSplitMode.FourGrid -> {
                            splitMode = newMode
                            splitFocusedPane = splitFocusedPane.coerceIn(0, 3)
                            splitActivePane = splitActivePane.coerceIn(0, 3)
                        }
                    }
                },
                onSplitExit = { exitSplitMode() },
                channelNoSelectIdle = { panelChannelNoSelectState.channelNo.isEmpty() },
            )
        }

        LeanbackVisible({ mainContentState.isSettingsVisible }) {
            LeanbackSettingsScreen(
                onRequestClose = {
                    mainContentState.isSettingsVisible = false
                    if (needsLeanbackOnboarding) sessionOnboardingDismissed = true
                },
            )
        }

        LeanbackVisible({ settingsViewModel.debugShowFps }) {
            LeanbackMonitorScreen()
        }

        if (!mainContentState.isQuickPanelVisible && !mainContentState.isSettingsVisible) {
            // 全局左下角触摸入口：单屏/分屏统一支持双击（兼容模拟器）与长按唤起快捷面板。
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(220.dp)
                    .height(140.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                mainContentState.quickPanelSubPanel = LeanbackQuickPanelSubPanel.None
                                mainContentState.isQuickPanelVisible = true
                            },
                            onLongPress = {
                                mainContentState.quickPanelSubPanel = LeanbackQuickPanelSubPanel.None
                                mainContentState.isQuickPanelVisible = true
                            },
                        )
                    },
            )
        }

        LeanbackUpdateScreen()
    }
}

@Composable
fun LeanbackBackPressHandledArea(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
    /** 直播全屏时上下键换台；在根节点拦截，避免 SurfaceView/AndroidView 抢走按键后 Compose 收不到 */
    onLiveDpadVertical: ((isUp: Boolean) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) = Box(
    modifier = Modifier
        .onPreviewKeyEvent {
            if (it.key == Key.Back && it.type == KeyEventType.KeyUp) {
                onBackPressed()
                return@onPreviewKeyEvent true
            }
            val vertical = onLiveDpadVertical
            if (vertical != null && it.type == KeyEventType.KeyUp) {
                val isUp = when {
                    it.key == Key.DirectionUp ||
                        it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_CHANNEL_UP -> true
                    it.key == Key.DirectionDown ||
                        it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN -> false
                    else -> null
                }
                if (isUp != null) {
                    vertical(isUp)
                    return@onPreviewKeyEvent true
                }
            }
            false
        }
        .then(modifier),
    content = content,
)

private data class SplitPanePlaybackState(
    val iptv: Iptv = Iptv(),
    val urlIdx: Int = 0,
    val streamHeaders: String? = null,
)

private fun replayErrorTip(errorText: String): String {
    val t = errorText.uppercase()
    return when {
        "BEHIND_LIVE_WINDOW" in t -> "回看时间窗已失效，请重新选择回看时间"
        "PARSING_CONTAINER_UNSUPPORTED" in t || "UNSUPPORTED" in t ->
            "回看流格式不受支持，请换其他频道或时间"
        "HTTP" in t || "4" in t || "5" in t ->
            "回看请求被服务端拒绝或链接失效"
        else -> "回看播放失败，请尝试更短时长或返回直播"
    }
}