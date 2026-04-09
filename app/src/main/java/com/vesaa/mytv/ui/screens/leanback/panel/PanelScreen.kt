package com.vesaa.mytv.ui.screens.leanback.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vesaa.mytv.data.entities.EpgList
import com.vesaa.mytv.data.entities.EpgList.Companion.currentProgrammes
import com.vesaa.mytv.data.entities.Iptv
import com.vesaa.mytv.data.entities.IptvFavoriteEntry
import com.vesaa.mytv.data.entities.IptvGroup
import com.vesaa.mytv.data.entities.IptvGroupList
import com.vesaa.mytv.data.entities.IptvGroupList.Companion.iptvList
import com.vesaa.mytv.data.entities.IptvList
import com.vesaa.mytv.data.utils.Constants
import com.vesaa.mytv.ui.rememberLeanbackChildPadding
import com.vesaa.mytv.ui.screens.leanback.panel.components.LeanbackPanelChannelNo
import com.vesaa.mytv.ui.screens.leanback.panel.components.LeanbackPanelDateTime
import com.vesaa.mytv.ui.screens.leanback.panel.components.LeanbackPanelIptvFavoriteList
import com.vesaa.mytv.ui.screens.leanback.panel.components.LeanbackPanelIptvGroupList
import com.vesaa.mytv.ui.screens.leanback.panel.components.LeanbackPanelIptvInfo
import com.vesaa.mytv.ui.screens.leanback.panel.components.LeanbackPanelPlayerInfo
import com.vesaa.mytv.ui.screens.leanback.toast.LeanbackToastState
import com.vesaa.mytv.ui.screens.leanback.video.player.LeanbackVideoPlayer
import com.vesaa.mytv.ui.theme.LeanbackTheme

@Composable
fun LeanbackPanelScreen(
    modifier: Modifier = Modifier,
    iptvGroupListProvider: () -> IptvGroupList = { IptvGroupList() },
    /** 面板顶部频道号与数字选台顺序（全列表或仅收藏） */
    channelOrderListProvider: () -> List<Iptv> = { emptyList() },
    epgListProvider: () -> EpgList = { EpgList() },
    currentIptvProvider: () -> Iptv = { Iptv() },
    currentIptvUrlIdxProvider: () -> Int = { 0 },
    videoPlayerMetadataProvider: () -> LeanbackVideoPlayer.Metadata = { LeanbackVideoPlayer.Metadata() },
    showProgrammeProgressProvider: () -> Boolean = { false },
    iptvFavoriteEnableProvider: () -> Boolean = { true },
    iptvFavoriteEntriesProvider: () -> List<IptvFavoriteEntry> = { emptyList() },
    iptvFavoriteListVisibleProvider: () -> Boolean = { false },
    onIptvFavoriteListVisibleChange: (Boolean) -> Unit = {},
    /** 只看收藏：底部仅展示收藏列表且不可退回空分组 */
    iptvFavoritesOnlyModeProvider: () -> Boolean = { false },
    onIptvSelected: (Iptv, String?) -> Unit = { _, _ -> },
    onIptvFavoriteToggle: (Iptv) -> Unit = {},
    onIptvGroupLongPressHide: (IptvGroup) -> Unit = {},
    onIptvGroupLongPressAddToFavorites: (IptvGroup) -> Unit = {},
    onClose: () -> Unit = {},
    autoCloseState: PanelAutoCloseState = rememberPanelAutoCloseState(
        timeout = Constants.UI_SCREEN_AUTO_CLOSE_DELAY,
        onTimeout = onClose,
    ),
) {
    LaunchedEffect(Unit) {
        autoCloseState.active()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) },
    ) {
        LeanbackPanelScreenTopRight(
            channelNoProvider = {
                val order = channelOrderListProvider()
                val cur = currentIptvProvider()
                val idx = order.indexOfFirst { it == cur }
                if (idx >= 0) (idx + 1).toString().padStart(2, '0') else "--"
            }
        )

        LeanbackPanelScreenBottom(
            iptvGroupListProvider = iptvGroupListProvider,
            epgListProvider = epgListProvider,
            currentIptvProvider = currentIptvProvider,
            currentIptvUrlIdxProvider = currentIptvUrlIdxProvider,
            videoPlayerMetadataProvider = videoPlayerMetadataProvider,
            showProgrammeProgressProvider = showProgrammeProgressProvider,
            iptvFavoriteEnableProvider = iptvFavoriteEnableProvider,
            iptvFavoriteEntriesProvider = iptvFavoriteEntriesProvider,
            iptvFavoriteListVisibleProvider = iptvFavoriteListVisibleProvider,
            onIptvFavoriteListVisibleChange = onIptvFavoriteListVisibleChange,
            iptvFavoritesOnlyModeProvider = iptvFavoritesOnlyModeProvider,
            onIptvSelected = onIptvSelected,
            onIptvFavoriteToggle = onIptvFavoriteToggle,
            onIptvGroupLongPressHide = onIptvGroupLongPressHide,
            onIptvGroupLongPressAddToFavorites = onIptvGroupLongPressAddToFavorites,
            onUserAction = { autoCloseState.active() },
        )
    }
}

@Composable
fun LeanbackPanelScreenTopRight(
    modifier: Modifier = Modifier,
    channelNoProvider: () -> String = { "" },
) {
    val childPadding = rememberLeanbackChildPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(top = childPadding.top, end = childPadding.end),
    ) {
        Row(
            modifier = Modifier.align(Alignment.TopEnd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeanbackPanelChannelNo(channelNoProvider = channelNoProvider)

            Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                Spacer(
                    modifier = Modifier
                        .background(Color.White)
                        .width(2.dp)
                        .height(30.dp),
                )
            }

            LeanbackPanelDateTime()
        }
    }
}

@Composable
private fun LeanbackPanelScreenBottom(
    modifier: Modifier = Modifier,
    iptvGroupListProvider: () -> IptvGroupList = { IptvGroupList() },
    epgListProvider: () -> EpgList = { EpgList() },
    currentIptvProvider: () -> Iptv = { Iptv() },
    currentIptvUrlIdxProvider: () -> Int = { 0 },
    videoPlayerMetadataProvider: () -> LeanbackVideoPlayer.Metadata = { LeanbackVideoPlayer.Metadata() },
    showProgrammeProgressProvider: () -> Boolean = { false },
    iptvFavoriteEnableProvider: () -> Boolean = { true },
    iptvFavoriteEntriesProvider: () -> List<IptvFavoriteEntry> = { emptyList() },
    iptvFavoriteListVisibleProvider: () -> Boolean = { false },
    onIptvFavoriteListVisibleChange: (Boolean) -> Unit = {},
    iptvFavoritesOnlyModeProvider: () -> Boolean = { false },
    onIptvSelected: (Iptv, String?) -> Unit = { _, _ -> },
    onIptvFavoriteToggle: (Iptv) -> Unit = {},
    onIptvGroupLongPressHide: (IptvGroup) -> Unit = {},
    onIptvGroupLongPressAddToFavorites: (IptvGroup) -> Unit = {},
    onUserAction: () -> Unit = {},
) {
    val childPadding = rememberLeanbackChildPadding()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            LeanbackPanelIptvInfo(
                modifier = Modifier
                    .padding(start = childPadding.start),
                iptvProvider = currentIptvProvider,
                iptvUrlIdxProvider = currentIptvUrlIdxProvider,
                currentProgrammesProvider = {
                    epgListProvider().currentProgrammes(currentIptvProvider())
                }
            )

            LeanbackPanelPlayerInfo(
                modifier = Modifier.padding(start = childPadding.start),
                metadataProvider = videoPlayerMetadataProvider
            )

            LeanbackPanelScreenBottomIptvList(
                iptvGroupListProvider = iptvGroupListProvider,
                epgListProvider = epgListProvider,
                currentIptvProvider = currentIptvProvider,
                showProgrammeProgressProvider = showProgrammeProgressProvider,
                iptvFavoriteEnableProvider = iptvFavoriteEnableProvider,
                iptvFavoriteEntriesProvider = iptvFavoriteEntriesProvider,
                iptvFavoriteListVisibleProvider = iptvFavoriteListVisibleProvider,
                onIptvFavoriteListVisibleChange = onIptvFavoriteListVisibleChange,
                iptvFavoritesOnlyModeProvider = iptvFavoritesOnlyModeProvider,
                onIptvSelected = onIptvSelected,
                onIptvFavoriteToggle = onIptvFavoriteToggle,
                onIptvGroupLongPressHide = onIptvGroupLongPressHide,
                onIptvGroupLongPressAddToFavorites = onIptvGroupLongPressAddToFavorites,
                onUserAction = onUserAction,
            )
        }
    }
}

@Composable
fun LeanbackPanelScreenBottomIptvList(
    modifier: Modifier = Modifier,
    iptvGroupListProvider: () -> IptvGroupList = { IptvGroupList() },
    epgListProvider: () -> EpgList = { EpgList() },
    currentIptvProvider: () -> Iptv = { Iptv() },
    showProgrammeProgressProvider: () -> Boolean = { false },
    iptvFavoriteEnableProvider: () -> Boolean = { true },
    iptvFavoriteEntriesProvider: () -> List<IptvFavoriteEntry> = { emptyList() },
    iptvFavoriteListVisibleProvider: () -> Boolean = { false },
    onIptvFavoriteListVisibleChange: (Boolean) -> Unit = {},
    iptvFavoritesOnlyModeProvider: () -> Boolean = { false },
    onIptvSelected: (Iptv, String?) -> Unit = { _, _ -> },
    onIptvFavoriteToggle: (Iptv) -> Unit = {},
    onIptvGroupLongPressHide: (IptvGroup) -> Unit = {},
    onIptvGroupLongPressAddToFavorites: (IptvGroup) -> Unit = {},
    onUserAction: () -> Unit = {},
) {
    val iptvFavoriteEnable = iptvFavoriteEnableProvider()
    val favoritesOnlyMode = iptvFavoritesOnlyModeProvider() && iptvFavoriteEnable
    var favoriteListVisible by remember { mutableStateOf(iptvFavoriteListVisibleProvider()) }
    val parentFavoriteListVisible = iptvFavoriteListVisibleProvider()
    LaunchedEffect(parentFavoriteListVisible) {
        favoriteListVisible = parentFavoriteListVisible
    }

    Box(modifier = modifier.height(150.dp)) {
        if (favoritesOnlyMode)
            LeanbackPanelIptvFavoriteList(
                favoriteEntriesProvider = iptvFavoriteEntriesProvider,
                epgListProvider = epgListProvider,
                currentIptvProvider = currentIptvProvider,
                showProgrammeProgressProvider = showProgrammeProgressProvider,
                onIptvSelected = onIptvSelected,
                onIptvFavoriteToggle = onIptvFavoriteToggle,
                onClose = {},
                closeWhenEmpty = false,
                allowCloseByUpOnFirstRow = false,
                onUserAction = onUserAction,
            )
        else if (favoriteListVisible)
            LeanbackPanelIptvFavoriteList(
                favoriteEntriesProvider = iptvFavoriteEntriesProvider,
                epgListProvider = epgListProvider,
                currentIptvProvider = currentIptvProvider,
                showProgrammeProgressProvider = showProgrammeProgressProvider,
                onIptvSelected = onIptvSelected,
                onIptvFavoriteToggle = onIptvFavoriteToggle,
                onClose = {
                    favoriteListVisible = false
                    onIptvFavoriteListVisibleChange(false)
                },
                onUserAction = onUserAction,
            )
        else
            LeanbackPanelIptvGroupList(
                iptvGroupListProvider = iptvGroupListProvider,
                epgListProvider = epgListProvider,
                currentIptvProvider = currentIptvProvider,
                showProgrammeProgressProvider = showProgrammeProgressProvider,
                onIptvSelected = { iptv -> onIptvSelected(iptv, null) },
                onIptvFavoriteToggle = onIptvFavoriteToggle,
                onIptvGroupLongPressHide = onIptvGroupLongPressHide,
                onIptvGroupLongPressAddToFavorites = onIptvGroupLongPressAddToFavorites,
                onToFavorite = {
                    if (!iptvFavoriteEnable) return@LeanbackPanelIptvGroupList

                    if (iptvFavoriteEntriesProvider().isNotEmpty()) {
                        favoriteListVisible = true
                        onIptvFavoriteListVisibleChange(true)
                    } else {
                        LeanbackToastState.I.showToast("没有精选频道")
                    }
                },
                onUserAction = onUserAction,
            )
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun LeanbackPanelScreenTopRightPreview() {
    LeanbackTheme {
        LeanbackPanelScreenTopRight(
            channelNoProvider = { "01" },
        )
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun LeanbackPanelScreenBottomPreview() {
    LeanbackTheme {
        LeanbackPanelScreenBottom(
            currentIptvProvider = { Iptv.EXAMPLE },
            currentIptvUrlIdxProvider = { 0 },
            iptvGroupListProvider = { IptvGroupList.EXAMPLE },
        )
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun LeanbackPanelScreenPreview() {
    LeanbackTheme {
        LeanbackPanelScreen(
            currentIptvProvider = { Iptv.EXAMPLE },
            currentIptvUrlIdxProvider = { 0 },
            iptvGroupListProvider = { IptvGroupList.EXAMPLE },
            channelOrderListProvider = { IptvGroupList.EXAMPLE.iptvList },
        )
    }
}