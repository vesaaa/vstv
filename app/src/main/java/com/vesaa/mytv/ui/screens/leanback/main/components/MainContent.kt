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
import com.vesaa.mytv.data.entities.IptvFavoriteEntry
import com.vesaa.mytv.data.entities.IptvGroupList
import com.vesaa.mytv.data.entities.IptvGroupList.Companion.iptvIdx
import com.vesaa.mytv.data.entities.IptvGroupList.Companion.iptvList
import com.vesaa.mytv.ui.screens.leanback.classicpanel.LeanbackClassicPanelScreen
import com.vesaa.mytv.ui.screens.leanback.components.LeanbackVisible
import com.vesaa.mytv.ui.screens.leanback.monitor.LeanbackMonitorScreen
import com.vesaa.mytv.ui.screens.leanback.panel.LeanbackPanelChannelNoSelectScreen
import com.vesaa.mytv.ui.screens.leanback.panel.LeanbackPanelDateTimeScreen
import com.vesaa.mytv.ui.screens.leanback.panel.LeanbackPanelScreen
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
    val favoritesOnlyUi =
        settingsViewModel.iptvChannelFavoriteEnable && settingsViewModel.iptvChannelFavoritesOnlyMode
    val uiIptvGroupList = if (favoritesOnlyUi) IptvGroupList() else iptvGroupList
    val channelOrderList =
        if (favoritesOnlyUi) settingsViewModel.iptvChannelFavoriteEntries.map { it.toIptv() }
        else iptvGroupList.iptvList

    val mainContentState = rememberLeanbackMainContentState(
        videoPlayerState = videoPlayerState,
        iptvGroupList = uiIptvGroupList,
    )

    LaunchedEffect(iptvGroupList) {
        IptvFavoriteMigration.runOnceIfNeeded(iptvGroupList)
        settingsViewModel.reloadFavoriteEntriesFromDisk()
    }

    LaunchedEffect(
        iptvGroupList,
        settingsViewModel.iptvChannelFavoriteEnable,
        settingsViewModel.iptvChannelFavoritesOnlyMode,
    ) {
        mainContentState.updateIptvGroupList(uiIptvGroupList)
    }

    LaunchedEffect(
        iptvGroupList,
        favoritesOnlyUi,
        settingsViewModel.iptvChannelFavoriteEntries,
    ) {
        val order =
            if (favoritesOnlyUi) settingsViewModel.iptvChannelFavoriteEntries.map { it.toIptv() }
            else iptvGroupList.iptvList
        mainContentState.syncChannelNavigation(
            order = order,
            streamHeadersForIptv = { iptv ->
                settingsViewModel.iptvChannelFavoriteEntries
                    .find { e ->
                        IptvFavoriteEntry.stableKeyFrom(iptv.urlList, iptv.channelName) == e.stableKey()
                    }
                    ?.playbackRequestHeaders?.trim()?.takeIf { it.isNotEmpty() }
            },
        )
    }

    LaunchedEffect(
        settingsViewModel.iptvChannelFavoriteEnable,
        settingsViewModel.iptvChannelFavoritesOnlyMode,
        settingsViewModel.iptvChannelFavoriteEntries,
    ) {
        if (!settingsViewModel.iptvChannelFavoriteEnable ||
            !settingsViewModel.iptvChannelFavoritesOnlyMode
        ) {
            return@LaunchedEffect
        }
        val entries = settingsViewModel.iptvChannelFavoriteEntries
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
                    val entry = settingsViewModel.iptvChannelFavoriteEntries.find { e ->
                        IptvFavoriteEntry.stableKeyFrom(iptv.urlList, iptv.channelName) == e.stableKey()
                    }
                    mainContentState.changeCurrentIptv(
                        iptv,
                        streamRequestHeaders = entry?.playbackRequestHeaders?.trim()
                            ?.takeIf { it.isNotEmpty() },
                    )
                } else {
                    mainContentState.changeCurrentIptv(iptv)
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
                    showProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
                )
            }

            LeanbackVisible({ !settingsViewModel.uiUseClassicPanelScreen && mainContentState.isPanelVisible }) {
                LeanbackPanelScreen(
                    iptvGroupListProvider = { uiIptvGroupList },
                    channelOrderListProvider = { channelOrderList },
                    epgListProvider = { epgList },
                    currentIptvProvider = { mainContentState.currentIptv },
                    currentIptvUrlIdxProvider = { mainContentState.currentIptvUrlIdx },
                    videoPlayerMetadataProvider = { videoPlayerState.metadata },
                    showProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
                    onIptvSelected = { iptv, streamHeaders ->
                        mainContentState.changeCurrentIptv(
                            iptv,
                            streamRequestHeaders = streamHeaders,
                        )
                    },
                    onIptvFavoriteToggle = {
                        if (!settingsViewModel.iptvChannelFavoriteEnable) {
                            LeanbackToastState.I.showToast("请先在设置 → 收藏 中启用收藏")
                            return@LeanbackPanelScreen
                        }

                        val was = settingsViewModel.isIptvFavorite(it)
                        settingsViewModel.toggleIptvFavorite(it)
                        if (was) {
                            LeanbackToastState.I.showToast("取消收藏: ${it.channelName}")
                        } else {
                            LeanbackToastState.I.showToast("已收藏: ${it.channelName}")
                        }
                    },
                    iptvFavoriteEntriesProvider = { settingsViewModel.iptvChannelFavoriteEntries },
                    iptvFavoriteListVisibleProvider = { settingsViewModel.iptvChannelFavoriteListVisible },
                    onIptvFavoriteListVisibleChange = {
                        settingsViewModel.iptvChannelFavoriteListVisible = it
                    },
                    iptvFavoritesOnlyModeProvider = { favoritesOnlyUi },
                    onClose = { mainContentState.isPanelVisible = false },
                )
            }

            LeanbackVisible({ settingsViewModel.uiUseClassicPanelScreen && mainContentState.isPanelVisible }) {
                LeanbackClassicPanelScreen(
                    iptvGroupListProvider = { uiIptvGroupList },
                    epgListProvider = { epgList },
                    currentIptvProvider = { mainContentState.currentIptv },
                    showProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
                    onIptvSelected = { iptv, streamHeaders ->
                        mainContentState.changeCurrentIptv(
                            iptv,
                            streamRequestHeaders = streamHeaders,
                        )
                    },
                    onIptvFavoriteToggle = {
                        if (!settingsViewModel.iptvChannelFavoriteEnable) {
                            LeanbackToastState.I.showToast("请先在设置 → 收藏 中启用收藏")
                            return@LeanbackClassicPanelScreen
                        }

                        val was = settingsViewModel.isIptvFavorite(it)
                        settingsViewModel.toggleIptvFavorite(it)
                        if (was) {
                            LeanbackToastState.I.showToast("取消收藏: ${it.channelName}")
                        } else {
                            LeanbackToastState.I.showToast("已收藏: ${it.channelName}")
                        }
                    },
                    iptvFavoriteEntriesProvider = { settingsViewModel.iptvChannelFavoriteEntries },
                    iptvFavoriteListVisibleProvider = { settingsViewModel.iptvChannelFavoriteListVisible },
                    onIptvFavoriteListVisibleChange = {
                        settingsViewModel.iptvChannelFavoriteListVisible = it
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