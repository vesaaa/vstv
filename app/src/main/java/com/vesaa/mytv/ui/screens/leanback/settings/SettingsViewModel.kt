package com.vesaa.mytv.ui.screens.leanback.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import android.os.SystemClock
import com.vesaa.mytv.AppGlobal
import com.vesaa.mytv.data.entities.Iptv
import com.vesaa.mytv.data.work.EpgRefreshWorkScheduler
import com.vesaa.mytv.data.entities.IptvFavoriteEntry
import com.vesaa.mytv.ui.utils.SP

class LeanbackSettingsViewModel : ViewModel() {
    private var _appBootLaunch by mutableStateOf(SP.appBootLaunch)
    var appBootLaunch: Boolean
        get() = _appBootLaunch
        set(value) {
            _appBootLaunch = value
            SP.appBootLaunch = value
        }

    private var _appLastLatestVersion by mutableStateOf(SP.appLastLatestVersion)
    var appLastLatestVersion: String
        get() = _appLastLatestVersion
        set(value) {
            _appLastLatestVersion = value
            SP.appLastLatestVersion = value
        }

    private var _appDeviceDisplayType by mutableStateOf(SP.appDeviceDisplayType)
    var appDeviceDisplayType: SP.AppDeviceDisplayType
        get() = _appDeviceDisplayType
        set(value) {
            _appDeviceDisplayType = value
            SP.appDeviceDisplayType = value
        }

    private var _debugShowFps by mutableStateOf(SP.debugShowFps)
    var debugShowFps: Boolean
        get() = _debugShowFps
        set(value) {
            _debugShowFps = value
            SP.debugShowFps = value
        }

    private var _debugAppLog by mutableStateOf(SP.debugAppLog)
    var debugAppLog: Boolean
        get() = _debugAppLog
        set(value) {
            _debugAppLog = value
            SP.debugAppLog = value
        }

    private var _iptvLastIptvIdx by mutableIntStateOf(SP.iptvLastIptvIdx)
    var iptvLastIptvIdx: Int
        get() = _iptvLastIptvIdx
        set(value) {
            _iptvLastIptvIdx = value
            SP.iptvLastIptvIdx = value
        }

    private var _iptvChannelChangeFlip by mutableStateOf(SP.iptvChannelChangeFlip)
    var iptvChannelChangeFlip: Boolean
        get() = _iptvChannelChangeFlip
        set(value) {
            _iptvChannelChangeFlip = value
            SP.iptvChannelChangeFlip = value
        }

    private var _iptvSourceCacheTime by mutableLongStateOf(SP.iptvSourceCacheTime)
    var iptvSourceCacheTime: Long
        get() = _iptvSourceCacheTime
        set(value) {
            _iptvSourceCacheTime = value
            SP.iptvSourceCacheTime = value
        }

    private var _iptvSourceUrl by mutableStateOf(SP.iptvSourceUrl)
    var iptvSourceUrl: String
        get() = _iptvSourceUrl
        set(value) {
            _iptvSourceUrl = value
            SP.iptvSourceUrl = value
        }

    private var _iptvSourceRequestHeaders by mutableStateOf(SP.iptvSourceRequestHeaders)
    var iptvSourceRequestHeaders: String
        get() = _iptvSourceRequestHeaders
        set(value) {
            _iptvSourceRequestHeaders = value
            SP.iptvSourceRequestHeaders = value
        }

    private var _iptvChannelRequestHeaders by mutableStateOf(SP.iptvChannelRequestHeaders)
    var iptvChannelRequestHeaders: String
        get() = _iptvChannelRequestHeaders
        set(value) {
            _iptvChannelRequestHeaders = value
            SP.iptvChannelRequestHeaders = value
        }

    private var _httpServerAdvertiseIp by mutableStateOf(SP.httpServerAdvertiseIp)
    var httpServerAdvertiseIp: String
        get() = _httpServerAdvertiseIp
        set(value) {
            _httpServerAdvertiseIp = value
            SP.httpServerAdvertiseIp = value
        }

    private var _iptvPlayableHostList by mutableStateOf(SP.iptvPlayableHostList)
    var iptvPlayableHostList: Set<String>
        get() = _iptvPlayableHostList
        set(value) {
            _iptvPlayableHostList = value
            SP.iptvPlayableHostList = value
        }

    private var _iptvChannelNoSelectEnable by mutableStateOf(SP.iptvChannelNoSelectEnable)
    var iptvChannelNoSelectEnable: Boolean
        get() = _iptvChannelNoSelectEnable
        set(value) {
            _iptvChannelNoSelectEnable = value
            SP.iptvChannelNoSelectEnable = value
        }

    private var _iptvSourceUrlHistoryList by mutableStateOf(SP.iptvSourceUrlHistoryList)
    var iptvSourceUrlHistoryList: Set<String>
        get() = _iptvSourceUrlHistoryList
        set(value) {
            _iptvSourceUrlHistoryList = value
            SP.iptvSourceUrlHistoryList = value
        }

    private var _iptvChannelFavoriteEnable by mutableStateOf(SP.iptvChannelFavoriteEnable)
    var iptvChannelFavoriteEnable: Boolean
        get() = _iptvChannelFavoriteEnable
        set(value) {
            _iptvChannelFavoriteEnable = value
            SP.iptvChannelFavoriteEnable = value
            if (!value) {
                iptvChannelFavoritesOnlyMode = false
                iptvChannelFavoriteListVisible = false
            }
        }

    private var _iptvChannelFavoriteListVisible by mutableStateOf(SP.iptvChannelFavoriteListVisible)
    var iptvChannelFavoriteListVisible: Boolean
        get() = _iptvChannelFavoriteListVisible
        set(value) {
            _iptvChannelFavoriteListVisible = value
            SP.iptvChannelFavoriteListVisible = value
        }

    private var _iptvChannelFavoritesOnlyMode by mutableStateOf(SP.iptvChannelFavoritesOnlyMode)
    var iptvChannelFavoritesOnlyMode: Boolean
        get() = _iptvChannelFavoritesOnlyMode
        set(value) {
            _iptvChannelFavoritesOnlyMode = value
            SP.iptvChannelFavoritesOnlyMode = value
        }

    private var _iptvExpandedChannelEnable by mutableStateOf(SP.iptvExpandedChannelEnable)
    var iptvExpandedChannelEnable: Boolean
        get() = _iptvExpandedChannelEnable
        set(value) {
            _iptvExpandedChannelEnable = value
            SP.iptvExpandedChannelEnable = value
        }

    private var _iptvExpandedChannelEntries by mutableStateOf(SP.loadExpandedChannelEntries())
    val iptvExpandedChannelEntries: List<IptvFavoriteEntry>
        get() = _iptvExpandedChannelEntries

    private var _iptvExpandedChannelSourceCount by mutableIntStateOf(SP.expandedChannelSourceCount())
    val iptvExpandedChannelSourceCount: Int
        get() = _iptvExpandedChannelSourceCount

    private var _iptvChannelFavoriteEntries by mutableStateOf(SP.loadFavoriteEntries())
    var iptvChannelFavoriteEntries: List<IptvFavoriteEntry>
        get() = _iptvChannelFavoriteEntries
        set(value) {
            _iptvChannelFavoriteEntries = value
            SP.saveFavoriteEntries(value)
            if (value.isEmpty()) iptvChannelFavoritesOnlyMode = false
        }

    fun isIptvFavorite(iptv: Iptv): Boolean {
        val key = IptvFavoriteEntry.stableKeyFrom(iptv.urlList, iptv.channelName)
        return iptvChannelFavoriteEntries.any { it.stableKey() == key }
    }

    private var lastIptvFavoriteToggleKey: String? = null
    private var lastIptvFavoriteToggleElapsedMs: Long = 0L

    /** 与列表中某频道同一稳定键时移除，否则按当前订阅头快照新增 */
    fun toggleIptvFavorite(iptv: Iptv) {
        val key = IptvFavoriteEntry.stableKeyFrom(iptv.urlList, iptv.channelName)
        val now = SystemClock.elapsedRealtime()
        if (key == lastIptvFavoriteToggleKey && now - lastIptvFavoriteToggleElapsedMs < 450L) {
            return
        }
        lastIptvFavoriteToggleKey = key
        lastIptvFavoriteToggleElapsedMs = now
        val cur = iptvChannelFavoriteEntries
        if (cur.any { it.stableKey() == key }) {
            iptvChannelFavoriteEntries = cur.filter { it.stableKey() != key }
        } else {
            iptvChannelFavoriteEntries =
                cur + IptvFavoriteEntry.fromIptv(iptv, SP.currentIptvSourceRequestHeadersSnapshot())
        }
    }

    /** 从 SP 重新加载（例如 [com.vesaa.mytv.data.IptvFavoriteMigration] 写入后） */
    fun reloadFavoriteEntriesFromDisk() {
        _iptvChannelFavoriteEntries = SP.loadFavoriteEntries()
    }

    fun reloadExpandedChannelsFromDisk() {
        _iptvExpandedChannelEntries = SP.loadExpandedChannelEntries()
        _iptvExpandedChannelSourceCount = SP.expandedChannelSourceCount()
    }

    /** 用「当前精选」覆盖写入当前直播源对应的扩展频道桶（不影响其他源）。 */
    fun updateExpandedChannelsFromFavoritesOfCurrentSource() {
        SP.updateExpandedChannelsForCurrentSource(iptvChannelFavoriteEntries)
        reloadExpandedChannelsFromDisk()
    }

    fun clearExpandedChannels() {
        SP.clearExpandedChannels()
        reloadExpandedChannelsFromDisk()
    }

    /** 网页推送等直接写入 SP 后，刷新内存中的直播源/节目单配置（设置页与 SP 一致）。 */
    fun reloadWebPushedStreamingConfigFromDisk() {
        _iptvSourceUrl = SP.iptvSourceUrl
        _iptvSourceRequestHeaders = SP.iptvSourceRequestHeaders
        _iptvChannelRequestHeaders = SP.iptvChannelRequestHeaders
        _iptvSourceUrlHistoryList = SP.iptvSourceUrlHistoryList
        _epgXmlUrl = SP.epgXmlUrl
        _epgXmlRequestHeaders = SP.epgXmlRequestHeaders
        _epgXmlUrlHistoryList = SP.epgXmlUrlHistoryList
    }

    private var _epgEnable by mutableStateOf(SP.epgEnable)
    var epgEnable: Boolean
        get() = _epgEnable
        set(value) {
            _epgEnable = value
            SP.epgEnable = value
            EpgRefreshWorkScheduler.schedule(AppGlobal.applicationContext)
        }

    private var _epgXmlUrl by mutableStateOf(SP.epgXmlUrl)
    var epgXmlUrl: String
        get() = _epgXmlUrl
        set(value) {
            _epgXmlUrl = value
            SP.epgXmlUrl = value
            EpgRefreshWorkScheduler.schedule(AppGlobal.applicationContext)
        }

    private var _epgXmlRequestHeaders by mutableStateOf(SP.epgXmlRequestHeaders)
    var epgXmlRequestHeaders: String
        get() = _epgXmlRequestHeaders
        set(value) {
            _epgXmlRequestHeaders = value
            SP.epgXmlRequestHeaders = value
        }

    private var _epgRefreshTimeThreshold by mutableIntStateOf(SP.epgRefreshTimeThreshold)
    var epgRefreshTimeThreshold: Int
        get() = _epgRefreshTimeThreshold
        set(value) {
            _epgRefreshTimeThreshold = value
            SP.epgRefreshTimeThreshold = value
        }

    private var _epgXmlUrlHistoryList by mutableStateOf(SP.epgXmlUrlHistoryList)
    var epgXmlUrlHistoryList: Set<String>
        get() = _epgXmlUrlHistoryList
        set(value) {
            _epgXmlUrlHistoryList = value
            SP.epgXmlUrlHistoryList = value
        }

    private var _uiShowEpgProgrammeProgress by mutableStateOf(SP.uiShowEpgProgrammeProgress)
    var uiShowEpgProgrammeProgress: Boolean
        get() = _uiShowEpgProgrammeProgress
        set(value) {
            _uiShowEpgProgrammeProgress = value
            SP.uiShowEpgProgrammeProgress = value
        }

    private var _uiUseClassicPanelScreen by mutableStateOf(SP.uiUseClassicPanelScreen)
    var uiUseClassicPanelScreen: Boolean
        get() = _uiUseClassicPanelScreen
        set(value) {
            _uiUseClassicPanelScreen = value
            SP.uiUseClassicPanelScreen = value
        }

    private var _uiDensityScaleRatio by mutableFloatStateOf(SP.uiDensityScaleRatio)
    var uiDensityScaleRatio: Float
        get() = _uiDensityScaleRatio
        set(value) {
            _uiDensityScaleRatio = value
            SP.uiDensityScaleRatio = value
        }

    private var _uiFontScaleRatio by mutableFloatStateOf(SP.uiFontScaleRatio)
    var uiFontScaleRatio: Float
        get() = _uiFontScaleRatio
        set(value) {
            _uiFontScaleRatio = value
            SP.uiFontScaleRatio = value
        }

    private var _uiTimeShowMode by mutableStateOf(SP.uiTimeShowMode)
    var uiTimeShowMode: SP.UiTimeShowMode
        get() = _uiTimeShowMode
        set(value) {
            _uiTimeShowMode = value
            SP.uiTimeShowMode = value
        }

    private var _uiPipMode by mutableStateOf(SP.uiPipMode)
    var uiPipMode: Boolean
        get() = _uiPipMode
        set(value) {
            _uiPipMode = value
            SP.uiPipMode = value
        }

    private var _updateForceRemind by mutableStateOf(SP.updateForceRemind)
    var updateForceRemind: Boolean
        get() = _updateForceRemind
        set(value) {
            _updateForceRemind = value
            SP.updateForceRemind = value
        }

    private var _videoPlayerLoadTimeout by mutableLongStateOf(SP.videoPlayerLoadTimeout)
    var videoPlayerLoadTimeout: Long
        get() = _videoPlayerLoadTimeout
        set(value) {
            _videoPlayerLoadTimeout = value
            SP.videoPlayerLoadTimeout = value
        }

    private var _videoPlayerAspectRatio by mutableStateOf(SP.videoPlayerAspectRatio)
    var videoPlayerAspectRatio: SP.VideoPlayerAspectRatio
        get() = _videoPlayerAspectRatio
        set(value) {
            _videoPlayerAspectRatio = value
            SP.videoPlayerAspectRatio = value
        }

    /** 主界面据此刷新「隐藏分组」过滤（设置里恢复、选台长按隐藏后 bump） */
    private var _iptvHiddenGroupFilterEpoch by mutableIntStateOf(0)
    val iptvHiddenGroupFilterEpoch: Int get() = _iptvHiddenGroupFilterEpoch

    fun bumpIptvHiddenGroupFilterEpoch() {
        _iptvHiddenGroupFilterEpoch++
    }
}