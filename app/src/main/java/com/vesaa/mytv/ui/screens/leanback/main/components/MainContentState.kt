package com.vesaa.mytv.ui.screens.leanback.main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.vesaa.mytv.data.entities.Iptv
import com.vesaa.mytv.data.entities.IptvGroupList
import com.vesaa.mytv.data.entities.IptvGroupList.Companion.iptvIdx
import com.vesaa.mytv.data.entities.IptvGroupList.Companion.iptvList
import com.vesaa.mytv.data.utils.Constants
import com.vesaa.mytv.ui.screens.leanback.quickpanel.LeanbackQuickPanelSubPanel
import com.vesaa.mytv.ui.screens.leanback.video.LeanbackVideoPlayerState
import com.vesaa.mytv.ui.screens.leanback.video.rememberLeanbackVideoPlayerState
import com.vesaa.mytv.ui.utils.SP
import com.vesaa.mytv.utils.Loggable
import kotlin.math.max

@Stable
class LeanbackMainContentState(
    coroutineScope: CoroutineScope,
    private val videoPlayerState: LeanbackVideoPlayerState,
    initialIptvGroupList: IptvGroupList,
) : Loggable() {
    /** 与 [rememberLeanbackMainContentState] 首次传入一致；后续由 [updateIptvGroupList] 与界面同步（避免 remember 无 key 时永远停留在空列表） */
    private var iptvGroupList by mutableStateOf(initialIptvGroupList)

    fun updateIptvGroupList(list: IptvGroupList) {
        iptvGroupList = list
    }
    private var _currentIptv by mutableStateOf(Iptv())
    val currentIptv get() = _currentIptv

    private var _currentIptvUrlIdx by mutableIntStateOf(0)
    val currentIptvUrlIdx get() = _currentIptvUrlIdx

    /** null 表示使用全局 [SP.playbackHttpUserAgent]；非 null 为本次拉流请求头快照（收藏条目等） */
    private var streamRequestHeadersForPlayback: String? = null

    private var _isPanelVisible by mutableStateOf(false)
    var isPanelVisible
        get() = _isPanelVisible
        set(value) {
            _isPanelVisible = value
        }

    private var _isSettingsVisible by mutableStateOf(false)
    var isSettingsVisible
        get() = _isSettingsVisible
        set(value) {
            _isSettingsVisible = value
        }

    private var _isTempPanelVisible by mutableStateOf(false)
    var isTempPanelVisible
        get() = _isTempPanelVisible
        set(value) {
            _isTempPanelVisible = value
        }

    private var _isQuickPanelVisible by mutableStateOf(false)
    var isQuickPanelVisible
        get() = _isQuickPanelVisible
        set(value) {
            _isQuickPanelVisible = value
        }

    private var _quickPanelSubPanel by mutableStateOf(LeanbackQuickPanelSubPanel.None)
    var quickPanelSubPanel
        get() = _quickPanelSubPanel
        set(value) {
            _quickPanelSubPanel = value
        }

    /** 与界面 [channelOrderList] 一致；非空时上下键在此列表上换台（仅收藏模式或从收藏点播时必需） */
    private var channelNavigationOrder by mutableStateOf<List<Iptv>>(emptyList())

    /** 换台目标若需独立拉流头（仅收藏模式下的条目），由此解析；否则 null */
    private var navStreamHeadersForIptv: (Iptv) -> String? = { null }

    fun syncChannelNavigation(
        order: List<Iptv>,
        streamHeadersForIptv: (Iptv) -> String?,
    ) {
        channelNavigationOrder = order
        navStreamHeadersForIptv = streamHeadersForIptv
    }

    init {
        changeCurrentIptv(iptvGroupList.iptvList.getOrElse(SP.iptvLastIptvIdx) {
            iptvGroupList.firstOrNull()?.iptvList?.firstOrNull() ?: Iptv()
        })

        videoPlayerState.onReady {
            coroutineScope.launch {
                val name = _currentIptv.name
                val urlIdx = _currentIptvUrlIdx
                delay(Constants.UI_TEMP_PANEL_SCREEN_SHOW_DURATION)
                if (name == _currentIptv.name && urlIdx == _currentIptvUrlIdx) {
                    _isTempPanelVisible = false
                }
            }

            if (_currentIptv.urlList.isNotEmpty()) {
                val idx = _currentIptvUrlIdx.coerceIn(_currentIptv.urlList.indices)
                SP.iptvPlayableHostList += getUrlHost(_currentIptv.urlList[idx])
            }
        }

        videoPlayerState.onError {
            if (_currentIptv.urlList.isNotEmpty() &&
                _currentIptvUrlIdx < _currentIptv.urlList.size - 1
            ) {
                changeCurrentIptv(_currentIptv, _currentIptvUrlIdx + 1)
            }

            if (_currentIptv.urlList.isNotEmpty()) {
                val idx = _currentIptvUrlIdx.coerceIn(_currentIptv.urlList.indices)
                SP.iptvPlayableHostList -= getUrlHost(_currentIptv.urlList[idx])
            }
        }

        videoPlayerState.onCutoff {
            if (_currentIptv.urlList.isNotEmpty()) {
                changeCurrentIptv(_currentIptv, _currentIptvUrlIdx)
            }
        }
    }

    private fun getPrevIptv(): Iptv {
        val order = channelNavigationOrder
        if (order.isNotEmpty()) {
            val i = order.indexOfFirst { it == _currentIptv }
            return if (i >= 0) {
                order.getOrElse(i - 1) { order.last() }
            } else {
                order.last()
            }
        }
        val currentIndex = iptvGroupList.iptvIdx(_currentIptv)
        return iptvGroupList.iptvList.getOrElse(currentIndex - 1) {
            iptvGroupList.lastOrNull()?.iptvList?.lastOrNull() ?: Iptv()
        }
    }

    private fun getNextIptv(): Iptv {
        val order = channelNavigationOrder
        if (order.isNotEmpty()) {
            val i = order.indexOfFirst { it == _currentIptv }
            return if (i >= 0) {
                order.getOrElse(i + 1) { order.first() }
            } else {
                order.first()
            }
        }
        val currentIndex = iptvGroupList.iptvIdx(_currentIptv)
        return iptvGroupList.iptvList.getOrElse(currentIndex + 1) {
            iptvGroupList.firstOrNull()?.iptvList?.firstOrNull() ?: Iptv()
        }
    }

    fun changeCurrentIptv(
        iptv: Iptv,
        urlIdx: Int? = null,
        streamRequestHeaders: String? = null,
    ) {
        _isPanelVisible = false

        streamRequestHeadersForPlayback = streamRequestHeaders

        if (iptv == _currentIptv && urlIdx == null) return

        if (iptv == _currentIptv && urlIdx != null && urlIdx != _currentIptvUrlIdx &&
            _currentIptv.urlList.isNotEmpty()
        ) {
            val oldIdx = _currentIptvUrlIdx.coerceIn(_currentIptv.urlList.indices)
            SP.iptvPlayableHostList -= getUrlHost(_currentIptv.urlList[oldIdx])
        }

        if (iptv.urlList.isEmpty()) {
            _isTempPanelVisible = false
            _currentIptv = iptv
            _currentIptvUrlIdx = 0
            val idx = iptvGroupList.iptvIdx(_currentIptv)
            if (idx >= 0) SP.iptvLastIptvIdx = idx
            return
        }

        _isTempPanelVisible = true

        _currentIptv = iptv
        val idxLast = iptvGroupList.iptvIdx(_currentIptv)
        if (idxLast >= 0) SP.iptvLastIptvIdx = idxLast

        _currentIptvUrlIdx = if (urlIdx == null) {
            // 优先从记忆中选择可播放的域名
            max(0, _currentIptv.urlList.indexOfFirst {
                SP.iptvPlayableHostList.contains(getUrlHost(it))
            })
        } else {
            (urlIdx + _currentIptv.urlList.size) % _currentIptv.urlList.size
        }

        val url = iptv.urlList[_currentIptvUrlIdx]
        log.d("播放${iptv.name}（${_currentIptvUrlIdx + 1}/${_currentIptv.urlList.size}）: $url")

        videoPlayerState.prepare(
            url,
            streamRequestHeadersForPlayback?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    fun changeCurrentIptvToPrev() {
        val iptv = getPrevIptv()
        changeCurrentIptv(iptv, streamRequestHeaders = navStreamHeadersForIptv(iptv))
    }

    fun changeCurrentIptvToNext() {
        val iptv = getNextIptv()
        changeCurrentIptv(iptv, streamRequestHeaders = navStreamHeadersForIptv(iptv))
    }
}

@Composable
fun rememberLeanbackMainContentState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    videoPlayerState: LeanbackVideoPlayerState = rememberLeanbackVideoPlayerState(),
    iptvGroupList: IptvGroupList = IptvGroupList(),
) = remember {
    LeanbackMainContentState(
        coroutineScope = coroutineScope,
        videoPlayerState = videoPlayerState,
        initialIptvGroupList = iptvGroupList,
    )
}

private fun getUrlHost(url: String): String {
    return url.split("://").getOrElse(1) { "" }.split("/").firstOrNull() ?: url
}