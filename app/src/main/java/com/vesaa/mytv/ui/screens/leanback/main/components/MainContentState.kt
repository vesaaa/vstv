package com.vesaa.mytv.ui.screens.leanback.main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.os.SystemClock
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
    enum class ChangeReason { INIT, USER, AUTO_RETRY, CUTOFF_RETRY }
    enum class PlaybackMode { LIVE, REPLAY }

    /** 用户手动切台后的一小段保护窗口：忽略迟到错误/断流重试，避免旧会话回调串扰到新频道。 */
    private var suppressAutoRetryUntilElapsedMs: Long = 0L

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
    private var _playbackMode by mutableStateOf(PlaybackMode.LIVE)
    val playbackMode get() = _playbackMode
    private var _replayHint by mutableStateOf("")
    val replayHint get() = _replayHint

    /** 待处理的回看跳转偏移量（毫秒）；用于 [IptvCatchup.Capability.SUPPORTED_BY_DVR_SEEK] 模式 */
    private var pendingReplaySeekMs: Long? = null

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

    private fun String?.normHeaderKey(): String = this?.trim().orEmpty()

    /**
     * 在频道导航顺序中定位当前频道下标：
     * 1) 优先按「频道 + 当前生效请求头」联合匹配，避免同频道不同头时命中错误条目；
     * 2) 再回退到仅按频道匹配（兼容旧行为）。
     */
    private fun findCurrentOrderIndex(order: List<Iptv>): Int {
        if (order.isEmpty()) return -1
        val curHeader = streamRequestHeadersForPlayback.normHeaderKey()
        val idxByIptvAndHeader = order.indexOfFirst { item ->
            item == _currentIptv && navStreamHeadersForIptv(item).normHeaderKey() == curHeader
        }
        if (idxByIptvAndHeader >= 0) return idxByIptvAndHeader
        return order.indexOfFirst { it == _currentIptv }
    }

    fun syncChannelNavigation(
        order: List<Iptv>,
        streamHeadersForIptv: (Iptv) -> String?,
    ) {
        channelNavigationOrder = order
        navStreamHeadersForIptv = streamHeadersForIptv
    }

    init {
        changeCurrentIptv(
            iptv = iptvGroupList.iptvList.getOrElse(SP.iptvLastIptvIdx) {
                iptvGroupList.firstOrNull()?.iptvList?.firstOrNull() ?: Iptv()
            },
            reason = ChangeReason.INIT,
        )

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

            // 处理 DVR Seek 逻辑：如果当前处于回看模式且有待跳转的偏移
            pendingReplaySeekMs?.let { seekBackMs ->
                log.d("检测到 DVR Seek 待处理，尝试跳转: -$seekBackMs ms")
                coroutineScope.launch {
                    delay(300) // 稍作延迟确保 HLS Timeline 已加载完毕并计算出有效时长
                    videoPlayerState.seekBack(seekBackMs)
                    pendingReplaySeekMs = null
                }
            }
        }

        videoPlayerState.onError {
            if (_playbackMode == PlaybackMode.REPLAY) {
                if (_currentIptv.urlList.isNotEmpty()) {
                    val idx = _currentIptvUrlIdx.coerceIn(_currentIptv.urlList.indices)
                    SP.iptvPlayableHostList -= getUrlHost(_currentIptv.urlList[idx])
                }
                return@onError
            }
            val now = SystemClock.elapsedRealtime()
            val suppressAutoRetry = now < suppressAutoRetryUntilElapsedMs
            if (_currentIptv.urlList.isNotEmpty() &&
                _currentIptvUrlIdx < _currentIptv.urlList.size - 1 &&
                !suppressAutoRetry
            ) {
                changeCurrentIptv(_currentIptv, _currentIptvUrlIdx + 1, reason = ChangeReason.AUTO_RETRY)
            }

            if (_currentIptv.urlList.isNotEmpty()) {
                val idx = _currentIptvUrlIdx.coerceIn(_currentIptv.urlList.indices)
                SP.iptvPlayableHostList -= getUrlHost(_currentIptv.urlList[idx])
            }
        }

        videoPlayerState.onCutoff {
            val now = SystemClock.elapsedRealtime()
            val suppressAutoRetry = now < suppressAutoRetryUntilElapsedMs
            if (_currentIptv.urlList.isEmpty() || suppressAutoRetry) return@onCutoff

            // 有下一条线路 → 切下一条；已是最后一条（或单线路）→ 重新 prepare 同线路重连
            val nextIdx = if (_currentIptvUrlIdx < _currentIptv.urlList.size - 1) {
                _currentIptvUrlIdx + 1
            } else {
                _currentIptvUrlIdx
            }
            changeCurrentIptv(_currentIptv, nextIdx, reason = ChangeReason.CUTOFF_RETRY)
        }
    }

    private fun getPrevIptv(): Iptv {
        val order = channelNavigationOrder
        if (order.isNotEmpty()) {
            val i = findCurrentOrderIndex(order)
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
            val i = findCurrentOrderIndex(order)
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
        reason: ChangeReason = ChangeReason.USER,
    ) {
        // 自动线路轮询不应打断用户正在操作的选台界面
        if (reason == ChangeReason.USER || reason == ChangeReason.INIT) {
            _isPanelVisible = false
        }

        streamRequestHeadersForPlayback = streamRequestHeaders
        _playbackMode = PlaybackMode.LIVE
        _replayHint = ""

        // 用户手动切台（尤其切组后）时，短时间内忽略自动轮询，防止旧错误回调串扰到新频道。
        if (reason == ChangeReason.USER && iptv != _currentIptv) {
            suppressAutoRetryUntilElapsedMs = SystemClock.elapsedRealtime() + 3000L
        }

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
        changeCurrentIptv(
            iptv = iptv,
            streamRequestHeaders = navStreamHeadersForIptv(iptv),
            reason = ChangeReason.USER,
        )
    }

    fun changeCurrentIptvToNext() {
        val iptv = getNextIptv()
        changeCurrentIptv(
            iptv = iptv,
            streamRequestHeaders = navStreamHeadersForIptv(iptv),
            reason = ChangeReason.USER,
        )
    }

    fun playCurrentIptvWithOverrideUrl(
        overrideUrl: String,
        streamRequestHeaders: String? = null,
        replayHint: String = "回看中",
        seekBackMs: Long? = null,
    ) {
        if (overrideUrl.isBlank()) return
        _isTempPanelVisible = true
        streamRequestHeadersForPlayback = streamRequestHeaders
        _playbackMode = PlaybackMode.REPLAY
        _replayHint = replayHint
        pendingReplaySeekMs = seekBackMs
        log.d("回看播放${_currentIptv.name} (seek: $seekBackMs): $overrideUrl")
        videoPlayerState.prepare(
            overrideUrl,
            streamRequestHeadersForPlayback?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    fun backToLive() {
        if (_currentIptv.urlList.isEmpty()) return
        val idx = _currentIptvUrlIdx.coerceIn(_currentIptv.urlList.indices)
        changeCurrentIptv(
            iptv = _currentIptv,
            urlIdx = idx,
            streamRequestHeaders = streamRequestHeadersForPlayback,
            reason = ChangeReason.USER,
        )
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