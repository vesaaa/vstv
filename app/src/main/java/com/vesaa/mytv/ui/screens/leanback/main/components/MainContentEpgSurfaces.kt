package com.vesaa.mytv.ui.screens.leanback.main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vesaa.mytv.data.entities.Epg
import com.vesaa.mytv.data.entities.EpgList
import com.vesaa.mytv.data.entities.EpgList.Companion.currentProgrammes
import com.vesaa.mytv.data.entities.Iptv
import com.vesaa.mytv.data.entities.IptvFavoriteEntry
import com.vesaa.mytv.data.entities.IptvGroup
import com.vesaa.mytv.data.entities.IptvGroupList
import com.vesaa.mytv.ui.screens.leanback.classicpanel.LeanbackClassicPanelScreen
import com.vesaa.mytv.ui.screens.leanback.components.LeanbackVisible
import com.vesaa.mytv.ui.screens.leanback.panel.LeanbackPanelTempScreen
import com.vesaa.mytv.ui.screens.leanback.quickpanel.LeanbackQuickPanelScreen
import com.vesaa.mytv.ui.screens.leanback.quickpanel.LeanbackQuickPanelSubPanel
import com.vesaa.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import com.vesaa.mytv.ui.screens.leanback.toast.LeanbackToastState
import com.vesaa.mytv.ui.screens.leanback.video.LeanbackVideoPlayerState
import com.vesaa.mytv.utils.IptvCatchup

/**
 * 仅依赖 [EpgList] 的叠加界面（临时换台信息、选台面板、快捷面板）。
 * 与全屏播放层拆分后，节目单大规模更新时重组范围落在此树，尽量不波及 [LeanbackVideoScreen]。
 */
@Composable
internal fun LeanbackMainEpgSurfaces(
    epgList: EpgList,
    mainContentState: LeanbackMainContentState,
    uiIptvGroupList: IptvGroupList,
    channelOrderList: List<Iptv>,
    currentFavorites: List<IptvFavoriteEntry>,
    settingsViewModel: LeanbackSettingsViewModel,
    videoPlayerState: LeanbackVideoPlayerState,
    playReplayWindow: (Iptv, Long, Long, String) -> Unit,
    playbackStatusText: String,
    replayCapabilityDetailText: String,
    resolveExtraStreamHeaders: (Iptv) -> String?,
    onIptvGroupLongPressHide: (IptvGroup) -> Unit,
    onIptvGroupLongPressAddToFavorites: (IptvGroup) -> Unit,
    /** 与主界面数字选台输入互斥：有输入时不显示临时换台条 */
    channelNoSelectIdle: () -> Boolean,
) {
    val currentIptv = mainContentState.currentIptv
    val programmesForCurrentChannel = remember(epgList, currentIptv) {
        epgList.currentProgrammes(currentIptv)
    }
    val epgForCurrentChannel = remember(epgList, currentIptv) {
        epgList.firstOrNull { it.matchesIptv(currentIptv) } ?: Epg()
    }

    LeanbackVisible({
        mainContentState.isTempPanelVisible
            && !mainContentState.isSettingsVisible
            && !mainContentState.isPanelVisible
            && !mainContentState.isQuickPanelVisible
            && channelNoSelectIdle()
    }) {
        LeanbackPanelTempScreen(
            channelNoProvider = {
                val idx = channelOrderList.indexOf(mainContentState.currentIptv)
                if (idx >= 0) (idx + 1).toString().padStart(2, '0') else "--"
            },
            currentIptvProvider = { mainContentState.currentIptv },
            currentIptvUrlIdxProvider = { mainContentState.currentIptvUrlIdx },
            currentProgrammesProvider = { programmesForCurrentChannel },
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
            onReplayByProgramme = { iptv, startMs, endMs ->
                playReplayWindow(iptv, startMs, endMs, "回看中 - 节目回看")
            },
            onClose = { mainContentState.isPanelVisible = false },
            iptvFavoriteEnableProvider = { settingsViewModel.iptvChannelFavoriteEnable },
        )
    }

    LeanbackVisible({ mainContentState.isQuickPanelVisible && !mainContentState.isSettingsVisible }) {
        LeanbackQuickPanelScreen(
            currentIptvProvider = { mainContentState.currentIptv },
            currentIptvUrlIdxProvider = { mainContentState.currentIptvUrlIdx },
            currentProgrammesProvider = { programmesForCurrentChannel },
            playbackStatusProvider = { playbackStatusText },
            replayCapabilityProvider = {
                when (IptvCatchup.capabilityOf(mainContentState.currentIptv)) {
                    IptvCatchup.Capability.SUPPORTED_BY_TEMPLATE -> "模板命中"
                    IptvCatchup.Capability.SUPPORTED_BY_DVR_URL -> "DVR命中"
                    IptvCatchup.Capability.UNSUPPORTED -> "不支持"
                }
            },
            replayCapabilityDetailProvider = { replayCapabilityDetailText },
            currentEpgProvider = { epgForCurrentChannel },
            currentIptvChannelNoProvider = {
                val idx = channelOrderList.indexOf(mainContentState.currentIptv)
                if (idx >= 0) (idx + 1).toString().padStart(2, '0') else "--"
            },
            videoPlayerMetadataProvider = { videoPlayerState.metadata },
            currentPlaybackUrlProvider = { videoPlayerState.currentMediaUrl },
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
}
