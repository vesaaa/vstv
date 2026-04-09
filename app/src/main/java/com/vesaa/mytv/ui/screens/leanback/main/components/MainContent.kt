package com.vesaa.mytv.ui.screens.leanback.main.components

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import com.vesaa.mytv.data.entities.Epg
import com.vesaa.mytv.data.entities.EpgList
import com.vesaa.mytv.data.entities.EpgList.Companion.currentProgrammes
import com.vesaa.mytv.data.IptvFavoriteMigration
import com.vesaa.mytv.data.entities.Iptv
import com.vesaa.mytv.data.entities.IptvFavoriteEntry
import com.vesaa.mytv.data.entities.IptvGroup
import com.vesaa.mytv.data.entities.IptvGroupList
import com.vesaa.mytv.data.entities.IptvGroupList.Companion.iptvIdx
import com.vesaa.mytv.data.entities.IptvGroupList.Companion.iptvList
import com.vesaa.mytv.data.entities.IptvList
import com.vesaa.mytv.data.entities.withoutHiddenGroupNames
import com.vesaa.mytv.ui.screens.leanback.classicpanel.LeanbackClassicPanelScreen
import com.vesaa.mytv.ui.screens.leanback.components.LeanbackVisible
import com.vesaa.mytv.ui.screens.leanback.monitor.LeanbackMonitorScreen
import com.vesaa.mytv.ui.screens.leanback.panel.LeanbackPanelChannelNoSelectScreen
import com.vesaa.mytv.ui.screens.leanback.panel.LeanbackPanelDateTimeScreen
import com.vesaa.mytv.ui.screens.leanback.panel.LeanbackPanelTempScreen
import com.vesaa.mytv.ui.screens.leanback.panel.rememberLeanbackPanelChannelNoSelectState
import com.vesaa.mytv.ui.screens.leanback.quickpanel.LeanbackQuickPanelScreen
import com.vesaa.mytv.ui.screens.leanback.quickpanel.LeanbackQuickPanelSubPanel
import com.vesaa.mytv.ui.screens.leanback.settings.LeanbackSettingsScreen
import com.vesaa.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import com.vesaa.mytv.ui.screens.leanback.toast.LeanbackToastState
import com.vesaa.mytv.ui.screens.leanback.update.LeanbackUpdateScreen
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

    val videoPlayerState = rememberLeanbackVideoPlayerState(
        defaultAspectRatioProvider = {
            when (settingsViewModel.videoPlayerAspectRatio) {
                SP.VideoPlayerAspectRatio.ORIGINAL -> null
                SP.VideoPlayerAspectRatio.SIXTEEN_NINE -> 16f / 9f
                SP.VideoPlayerAspectRatio.FOUR_THREE -> 4f / 3f
                SP.VideoPlayerAspectRatio.AUTO -> {
                    configuration.screenHeightDp.toFloat() / configuration.screenWidthDp.toFloat()
                }
            }
        }
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
    val playReplayWindow: (Iptv, Long, Long, String) -> Unit = replay@{ targetIptv, rawStartMs, rawEndMs, replayHint ->
        val iptv = targetIptv
        if (!IptvCatchup.supportCatchup(iptv)) {
            LeanbackToastState.I.showToast("当前频道不支持回看")
            return@replay
        }
        if (iptv.urlList.isEmpty()) {
            LeanbackToastState.I.showToast("当前频道无可用播放地址")
            return@replay
        }
        val maxHours = IptvCatchup.maxCatchupHours(iptv)
        val window = IptvCatchup.clampWindow(
            rawStartMs = rawStartMs,
            rawEndMs = rawEndMs,
            maxHours = maxHours,
        )
        if (window == null) {
            LeanbackToastState.I.showToast("回看时间无效或超出范围")
            return@replay
        }
        val idx = mainContentState.currentIptvUrlIdx
            .coerceIn(0, (iptv.urlList.size - 1).coerceAtLeast(0))
        val baseUrl = iptv.urlList.getOrNull(idx).orEmpty()
        if (baseUrl.isBlank()) {
            LeanbackToastState.I.showToast("当前频道播放地址为空")
            return@replay
        }
        val replayUrl = IptvCatchup.buildCatchupUrl(iptv, baseUrl, window)
        if (replayUrl.isNullOrBlank()) {
            LeanbackToastState.I.showToast("该源未提供回看地址模板")
            return@replay
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
            mainContentState.changeCurrentIptv(visible.first())
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
                if (favoritesOnlyUi) {
                    val entry = currentFavorites.find { e ->
                        IptvFavoriteEntry.stableKeyFrom(iptv.urlList, iptv.channelName) == e.stableKey()
                    }
                    mainContentState.changeCurrentIptv(
                        iptv,
                        streamRequestHeaders = entry?.playbackRequestHeaders?.trim()
                            ?.takeIf { it.isNotEmpty() },
                    )
                } else {
                    mainContentState.changeCurrentIptv(
                        iptv,
                        streamRequestHeaders = resolveExtraStreamHeaders(iptv),
                    )
                }
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
            ) {
                focusRequester.requestFocus()
            }
            delay(100)
        }
    }

    val videoFocusEnabled =
        !mainContentState.isPanelVisible &&
            !mainContentState.isSettingsVisible &&
            !mainContentState.isQuickPanelVisible

    val liveDpadVerticalChannel: ((isUp: Boolean) -> Unit)? =
        if (videoFocusEnabled && panelChannelNoSelectState.channelNo.isEmpty()) {
            { isUp ->
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
        LeanbackVideoScreen(
            state = videoPlayerState,
            showMetadataProvider = { false },
            modifier = Modifier
                .focusRequester(focusRequester)
                .focusable(videoFocusEnabled)
                .handleLeanbackKeyEvents(
                    onLeft = {
                        if (mainContentState.currentIptv.urlList.size > 1) {
                            mainContentState.changeCurrentIptv(
                                iptv = mainContentState.currentIptv,
                                urlIdx = mainContentState.currentIptvUrlIdx - 1,
                            )
                        }
                    },
                    onRight = {
                        if (mainContentState.currentIptv.urlList.size > 1) {
                            mainContentState.changeCurrentIptv(
                                iptv = mainContentState.currentIptv,
                                urlIdx = mainContentState.currentIptvUrlIdx + 1,
                            )
                        }
                    },
                    onSelect = { mainContentState.isPanelVisible = true },
                    onLongSelect = { mainContentState.isQuickPanelVisible = true },
                    onSettings = { mainContentState.isQuickPanelVisible = true },
                    onNumber = {
                        if (settingsViewModel.iptvChannelNoSelectEnable) {
                            panelChannelNoSelectState.input(it)
                        }
                    },
                    onLongDown = { mainContentState.isQuickPanelVisible = true },
                )
                .handleLeanbackDragGestures(
                    onSwipeDown = {
                        if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentIptvToNext()
                        else mainContentState.changeCurrentIptvToPrev()
                    },
                    onSwipeUp = {
                        if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentIptvToPrev()
                        else mainContentState.changeCurrentIptvToNext()
                    },
                    onSwipeRight = {
                        if (mainContentState.currentIptv.urlList.size > 1) {
                            mainContentState.changeCurrentIptv(
                                iptv = mainContentState.currentIptv,
                                urlIdx = mainContentState.currentIptvUrlIdx - 1,
                            )
                        }
                    },
                    onSwipeLeft = {
                        if (mainContentState.currentIptv.urlList.size > 1) {
                            mainContentState.changeCurrentIptv(
                                iptv = mainContentState.currentIptv,
                                urlIdx = mainContentState.currentIptvUrlIdx + 1,
                            )
                        }
                    },
                ),
        )

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

            LeanbackVisible({
                mainContentState.isTempPanelVisible
                        && !mainContentState.isSettingsVisible
                        && !mainContentState.isPanelVisible
                        && !mainContentState.isQuickPanelVisible
                        && panelChannelNoSelectState.channelNo.isEmpty()
            }) {
                LeanbackPanelTempScreen(
                    channelNoProvider = {
                        val idx = channelOrderList.indexOf(mainContentState.currentIptv)
                        if (idx >= 0) (idx + 1).toString().padStart(2, '0') else "--"
                    },
                    currentIptvProvider = { mainContentState.currentIptv },
                    currentIptvUrlIdxProvider = { mainContentState.currentIptvUrlIdx },
                    currentProgrammesProvider = { epgList.currentProgrammes(mainContentState.currentIptv) },
                    playbackStatusProvider = { playbackStatusText },
                    showProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
                )
            }

            LeanbackVisible({ mainContentState.isPanelVisible }) {
                LeanbackClassicPanelScreen(
                    iptvGroupListProvider = { uiIptvGroupList },
                    epgListProvider = { epgList },
                    currentIptvProvider = { mainContentState.currentIptv },
                    playbackStatusProvider = { playbackStatusText },
                    showProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
                    onIptvSelected = { iptv, streamHeaders ->
                        mainContentState.changeCurrentIptv(
                            iptv,
                            streamRequestHeaders = streamHeaders ?: resolveExtraStreamHeaders(iptv),
                        )
                    },
                    onIptvFavoriteToggle = {
                        if (!settingsViewModel.iptvChannelFavoriteEnable) {
                            LeanbackToastState.I.showToast("请先在设置 → 精选设置 中启用精选")
                            return@LeanbackClassicPanelScreen
                        }

                        val was = settingsViewModel.isIptvFavorite(it)
                        settingsViewModel.toggleIptvFavorite(it)
                        if (was) {
                            LeanbackToastState.I.showToast("已移出精选: ${it.channelName}")
                        } else {
                            LeanbackToastState.I.showToast("已加入精选: ${it.channelName}")
                        }
                    },
                    iptvFavoriteEntriesProvider = { currentFavorites },
                    iptvFavoriteListVisibleProvider = { settingsViewModel.iptvChannelFavoriteListVisible },
                    onIptvFavoriteListVisibleChange = {
                        settingsViewModel.iptvChannelFavoriteListVisible = it
                    },
                    onIptvGroupLongPressHide = onIptvGroupLongPressHide,
                    onIptvGroupLongPressAddToFavorites = onIptvGroupLongPressAddToFavorites,
                    replaySupportedForIptv = { iptv -> IptvCatchup.supportCatchup(iptv) },
                    onReplayByProgramme = { iptv, startMs, endMs ->
                        playReplayWindow(iptv, startMs, endMs, "回看中 - 节目回看")
                    },
                    onClose = { mainContentState.isPanelVisible = false },
                    iptvFavoriteEnableProvider = { settingsViewModel.iptvChannelFavoriteEnable }
                )
            }
        }

        LeanbackVisible({ mainContentState.isQuickPanelVisible && !mainContentState.isSettingsVisible }) {
            LeanbackQuickPanelScreen(
                currentIptvProvider = { mainContentState.currentIptv },
                currentIptvUrlIdxProvider = { mainContentState.currentIptvUrlIdx },
                currentProgrammesProvider = { epgList.currentProgrammes(mainContentState.currentIptv) },
                    playbackStatusProvider = { playbackStatusText },
                    replayCapabilityProvider = {
                        when (IptvCatchup.capabilityOf(mainContentState.currentIptv)) {
                            IptvCatchup.Capability.SUPPORTED_BY_TEMPLATE -> "模板命中"
                            IptvCatchup.Capability.SUPPORTED_BY_DVR_URL -> "DVR命中"
                            IptvCatchup.Capability.UNSUPPORTED -> "不支持"
                        }
                    },
                    replayCapabilityDetailProvider = { replayCapabilityDetailText },
                currentEpgProvider = {
                    epgList.firstOrNull { it.matchesIptv(mainContentState.currentIptv) } ?: Epg()
                },
                currentIptvChannelNoProvider = {
                    val idx = channelOrderList.indexOf(mainContentState.currentIptv)
                    if (idx >= 0) (idx + 1).toString().padStart(2, '0') else "--"
                },
                videoPlayerMetadataProvider = { videoPlayerState.metadata },
                videoPlayerAspectRatioProvider = { videoPlayerState.aspectRatio },
                onChangeVideoPlayerAspectRatio = { videoPlayerState.aspectRatio = it },
                onIptvUrlIdxChange = {
                    mainContentState.changeCurrentIptv(
                        iptv = mainContentState.currentIptv,
                        urlIdx = it,
                    )
                },
                catchupSupportedProvider = {
                    IptvCatchup.supportCatchup(mainContentState.currentIptv)
                },
                onReplayUnsupported = {
                    LeanbackToastState.I.showToast("当前频道暂不支持回看")
                },
                isReplayActiveProvider = {
                    mainContentState.playbackMode == LeanbackMainContentState.PlaybackMode.REPLAY
                },
                onBackToLive = {
                    mainContentState.backToLive()
                    LeanbackToastState.I.showToast("已返回直播")
                },
                catchupMaxHoursProvider = {
                    IptvCatchup.maxCatchupHours(mainContentState.currentIptv)
                },
                onReplayByBackMinutes = { backMinutes ->
                    val end = System.currentTimeMillis()
                    val start = end - backMinutes * 60L * 1000L
                    playReplayWindow(mainContentState.currentIptv, start, end, "回看中 -${backMinutes}分钟")
                },
                onReplayByProgramme = { startMs, endMs ->
                    playReplayWindow(mainContentState.currentIptv, startMs, endMs, "回看中 - 节目回看")
                },
                subPanel = mainContentState.quickPanelSubPanel,
                onSubPanelChange = { mainContentState.quickPanelSubPanel = it },
                onMoreSettings = {
                    mainContentState.quickPanelSubPanel = LeanbackQuickPanelSubPanel.None
                    mainContentState.isSettingsVisible = true
                },
                onClose = {
                    mainContentState.quickPanelSubPanel = LeanbackQuickPanelSubPanel.None
                    mainContentState.isQuickPanelVisible = false
                },
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