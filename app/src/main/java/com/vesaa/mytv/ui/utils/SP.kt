package com.vesaa.mytv.ui.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.vesaa.mytv.data.entities.IptvFavoriteEntry
import com.vesaa.mytv.data.utils.Constants
import com.vesaa.mytv.utils.Logger
import com.vesaa.mytv.utils.normalizeIptvRequestHeadersInput
import com.vesaa.mytv.utils.parseHttpHeaderLines

private val spJson = Json { ignoreUnknownKeys = true }

/**
 * 应用配置存储
 */
object SP {
    private const val SP_NAME = "mytv"
    private const val SP_MODE = Context.MODE_PRIVATE
    private lateinit var sp: SharedPreferences

    fun getInstance(context: Context): SharedPreferences =
        context.getSharedPreferences(SP_NAME, SP_MODE)

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = getInstance(context.applicationContext)
    }

    enum class KEY {
        /** ==================== 应用 ==================== */
        /** 开机自启 */
        APP_BOOT_LAUNCH,

        /** 上一次最新版本 */
        APP_LAST_LATEST_VERSION,

        /** 设备显示类型 */
        APP_DEVICE_DISPLAY_TYPE,

        /** ==================== 调式 ==================== */
        /** 显示fps */
        DEBUG_SHOW_FPS,

        /** 应用调试日志（网页「日志」页 + HTTP 请求摘要；默认关） */
        DEBUG_APP_LOG,

        /** ==================== 直播源 ==================== */
        /** 上一次直播源序号 */
        IPTV_LAST_IPTV_IDX,

        /** 换台反转 */
        IPTV_CHANNEL_CHANGE_FLIP,

        /** 直播源精简 */
        IPTV_SOURCE_SIMPLIFY,

        /** 直播源url */
        IPTV_SOURCE_URL,

        /** 直播源缓存时间（毫秒） */
        IPTV_SOURCE_CACHE_TIME,

        /** 直播源可播放host列表 */
        IPTV_PLAYABLE_HOST_LIST,

        /** 直播源历史列表 */
        IPTV_SOURCE_URL_HISTORY_LIST,

        /** 直播源拉取时附加的 HTTP 请求头（多行 Name: Value） */
        IPTV_SOURCE_REQUEST_HEADERS,

        /** 各订阅 URL 对应的请求头（JSON 对象） */
        IPTV_SOURCE_HEADERS_BY_URL_JSON,

        /** 设置页/二维码展示的 IP（空则自动） */
        HTTP_SERVER_ADVERTISE_IP,

        /** 是否启用数字选台 */
        IPTV_CHANNEL_NO_SELECT_ENABLE,

        /** 是否启用直播源频道收藏 */
        IPTV_CHANNEL_FAVORITE_ENABLE,

        /** 显示直播源频道收藏列表 */
        IPTV_CHANNEL_FAVORITE_LIST_VISIBLE,

        /** 直播源频道收藏列表（旧版，仅 channelName；迁移后清空） */
        IPTV_CHANNEL_FAVORITE_LIST,

        /** 直播源频道收藏（JSON 列表，含 URL 与播放请求头快照） */
        IPTV_CHANNEL_FAVORITES_JSON,

        /** 只看收藏：界面与换台顺序仅含收藏夹内频道（依赖收藏启用） */
        IPTV_CHANNEL_FAVORITES_ONLY_MODE,

        /** ==================== 节目单 ==================== */
        /** 启用节目单 */
        EPG_ENABLE,

        /** 节目单 xml url */
        EPG_XML_URL,

        /** 节目单刷新时间阈值（小时） */
        EPG_REFRESH_TIME_THRESHOLD,

        /** 节目单历史列表 */
        EPG_XML_URL_HISTORY_LIST,

        /** 节目单拉取时附加的 HTTP 请求头（与当前节目单 URL 绑定；多行 Name: Value） */
        EPG_XML_REQUEST_HEADERS,

        /** 各节目单 URL 对应的请求头（JSON 对象） */
        EPG_XML_HEADERS_BY_URL_JSON,

        /** ==================== 界面 ==================== */
        /** 显示节目进度 */
        UI_SHOW_EPG_PROGRAMME_PROGRESS,

        /** 使用经典选台界面 */
        UI_USE_CLASSIC_PANEL_SCREEN,

        /** 界面密度缩放比例 */
        UI_DENSITY_SCALE_RATIO,

        /** 界面字体缩放比例 */
        UI_FONT_SCALE_RATIO,

        /** 时间显示模式 */
        UI_TIME_SHOW_MODE,

        /** 画中画模式 */
        UI_PIP_MODE,

        /** ==================== 更新 ==================== */
        /** 更新强提醒（弹窗形式） */
        UPDATE_FORCE_REMIND,

        /** 上次完整下载至缓存的安装包对应 Release 版本号（与 GitHub tag 一致） */
        UPDATE_LAST_DOWNLOADED_APK_VERSION,

        /** 上次完整下载的安装包 URL（与版本一起用于判断缓存是否仍有效） */
        UPDATE_LAST_DOWNLOADED_APK_URL,

        /** ==================== 播放器 ==================== */
        /** 播放器 加载超时 */
        VIDEO_PLAYER_LOAD_TIMEOUT,

        /** 播放器 画面比例 */
        VIDEO_PLAYER_ASPECT_RATIO,
    }

    /** ==================== 应用 ==================== */
    /** 开机自启 */
    var appBootLaunch: Boolean
        get() = sp.getBoolean(KEY.APP_BOOT_LAUNCH.name, false)
        set(value) = sp.edit().putBoolean(KEY.APP_BOOT_LAUNCH.name, value).apply()

    /** 上一次最新版本 */
    var appLastLatestVersion: String
        get() = sp.getString(KEY.APP_LAST_LATEST_VERSION.name, "")!!
        set(value) = sp.edit().putString(KEY.APP_LAST_LATEST_VERSION.name, value).apply()

    /** 设备显示类型 */
    var appDeviceDisplayType: AppDeviceDisplayType
        get() = AppDeviceDisplayType.fromValue(sp.getInt(KEY.APP_DEVICE_DISPLAY_TYPE.name, 0))
        set(value) = sp.edit().putInt(KEY.APP_DEVICE_DISPLAY_TYPE.name, value.value).apply()

    /** ==================== 调式 ==================== */
    /** 显示fps */
    var debugShowFps: Boolean
        get() = sp.getBoolean(KEY.DEBUG_SHOW_FPS.name, false)
        set(value) = sp.edit().putBoolean(KEY.DEBUG_SHOW_FPS.name, value).apply()

    /** 应用调试日志：写入 Logger 历史并在 OkHttp 中记录请求/响应摘要（响应体仅记录长度） */
    var debugAppLog: Boolean
        get() = sp.getBoolean(KEY.DEBUG_APP_LOG.name, false)
        set(value) {
            val prev = sp.getBoolean(KEY.DEBUG_APP_LOG.name, false)
            sp.edit().putBoolean(KEY.DEBUG_APP_LOG.name, value).apply()
            if (!value && prev) {
                Logger.clearHistory()
            }
        }

    /** ==================== 直播源 ==================== */
    /** 上一次直播源序号 */
    var iptvLastIptvIdx: Int
        get() = sp.getInt(KEY.IPTV_LAST_IPTV_IDX.name, 0)
        set(value) = sp.edit().putInt(KEY.IPTV_LAST_IPTV_IDX.name, value).apply()

    /** 换台反转 */
    var iptvChannelChangeFlip: Boolean
        get() = sp.getBoolean(KEY.IPTV_CHANNEL_CHANGE_FLIP.name, false)
        set(value) = sp.edit().putBoolean(KEY.IPTV_CHANNEL_CHANGE_FLIP.name, value).apply()

    /** 直播源精简 */
    var iptvSourceSimplify: Boolean
        get() = sp.getBoolean(KEY.IPTV_SOURCE_SIMPLIFY.name, false)
        set(value) = sp.edit().putBoolean(KEY.IPTV_SOURCE_SIMPLIFY.name, value).apply()

    /** 直播源 url */
    var iptvSourceUrl: String
        get() = sp.getString(KEY.IPTV_SOURCE_URL.name, "") ?: ""
        set(value) = sp.edit().putString(KEY.IPTV_SOURCE_URL.name, value).apply()

    /** 拉取 m3u/tvbox 订阅时使用的额外请求头（每行「Name: Value」；单行无冒号时视为仅 User-Agent 取值） */
    var iptvSourceRequestHeaders: String
        get() = sp.getString(KEY.IPTV_SOURCE_REQUEST_HEADERS.name, "") ?: ""
        set(value) = sp.edit().putString(KEY.IPTV_SOURCE_REQUEST_HEADERS.name, value).apply()

    /**
     * 播放频道流时的 HTTP User-Agent：与 [iptvSourceRequestHeaders] 中的 User-Agent 一致；
     * 未配置 User-Agent 时使用 [Constants.VIDEO_PLAYER_USER_AGENT]。
     */
    fun playbackHttpUserAgent(): String {
        val map = normalizeIptvRequestHeadersInput(iptvSourceRequestHeaders).parseHttpHeaderLines()
        val ua = map.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value?.trim()
        return ua?.takeIf { it.isNotEmpty() } ?: Constants.VIDEO_PLAYER_USER_AGENT
    }

    /** 扫码/设置页展示的局域网 IP；空表示自动选择 */
    var httpServerAdvertiseIp: String
        get() = sp.getString(KEY.HTTP_SERVER_ADVERTISE_IP.name, "") ?: ""
        set(value) = sp.edit().putString(KEY.HTTP_SERVER_ADVERTISE_IP.name, value).apply()

    private var iptvSourceHeadersByUrlJsonRaw: String
        get() = sp.getString(KEY.IPTV_SOURCE_HEADERS_BY_URL_JSON.name, "{}") ?: "{}"
        set(value) = sp.edit().putString(KEY.IPTV_SOURCE_HEADERS_BY_URL_JSON.name, value).apply()

    /** 读取某订阅 URL 已保存的请求头文本 */
    fun getIptvSourceHeadersForUrl(url: String): String {
        if (url.isBlank()) return ""
        val map = runCatching {
            spJson.decodeFromString<Map<String, String>>(iptvSourceHeadersByUrlJsonRaw)
        }.getOrElse { emptyMap() }
        return map[url].orEmpty()
    }

    /** 将请求头文本与订阅 URL 绑定保存 */
    fun putIptvSourceHeadersForUrl(url: String, headers: String) {
        if (url.isBlank()) return
        val map = runCatching {
            spJson.decodeFromString<Map<String, String>>(iptvSourceHeadersByUrlJsonRaw).toMutableMap()
        }.getOrElse { mutableMapOf() }
        if (headers.isBlank()) {
            map.remove(url)
        } else {
            map[url] = headers
        }
        iptvSourceHeadersByUrlJsonRaw = spJson.encodeToString(map)
    }

    /**
     * 网页/扫码推送直播源后同步落盘（[apply] 异步可能导致用户立刻杀进程时配置未写入）。
     */
    fun commitIptvWebSettings(url: String, requestHeaders: String) {
        val rawJson = sp.getString(KEY.IPTV_SOURCE_HEADERS_BY_URL_JSON.name, "{}") ?: "{}"
        val map = runCatching {
            spJson.decodeFromString<Map<String, String>>(rawJson).toMutableMap()
        }.getOrElse { mutableMapOf() }
        if (url.isNotBlank()) {
            if (requestHeaders.isBlank()) {
                map.remove(url)
            } else {
                map[url] = requestHeaders
            }
        }
        sp.edit()
            .putString(KEY.IPTV_SOURCE_URL.name, url)
            .putString(KEY.IPTV_SOURCE_REQUEST_HEADERS.name, requestHeaders)
            .putString(KEY.IPTV_SOURCE_HEADERS_BY_URL_JSON.name, spJson.encodeToString(map))
            .commit()
        val trimmed = url.trim()
        if (trimmed.isNotEmpty()) {
            val hist =
                (sp.getStringSet(KEY.IPTV_SOURCE_URL_HISTORY_LIST.name, emptySet()) ?: emptySet()).toMutableSet()
            hist.add(trimmed)
            sp.edit().putStringSet(KEY.IPTV_SOURCE_URL_HISTORY_LIST.name, hist).commit()
        }
    }

    /** 直播源缓存时间（毫秒） */
    var iptvSourceCacheTime: Long
        get() = sp.getLong(KEY.IPTV_SOURCE_CACHE_TIME.name, Constants.IPTV_SOURCE_CACHE_TIME)
        set(value) = sp.edit().putLong(KEY.IPTV_SOURCE_CACHE_TIME.name, value).apply()

    /** 直播源可播放host列表 */
    var iptvPlayableHostList: Set<String>
        get() = sp.getStringSet(KEY.IPTV_PLAYABLE_HOST_LIST.name, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY.IPTV_PLAYABLE_HOST_LIST.name, value).apply()

    /** 直播源历史列表 */
    var iptvSourceUrlHistoryList: Set<String>
        get() = sp.getStringSet(KEY.IPTV_SOURCE_URL_HISTORY_LIST.name, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY.IPTV_SOURCE_URL_HISTORY_LIST.name, value).apply()

    /** 是否启用数字选台 */
    var iptvChannelNoSelectEnable: Boolean
        get() = sp.getBoolean(KEY.IPTV_CHANNEL_NO_SELECT_ENABLE.name, true)
        set(value) = sp.edit().putBoolean(KEY.IPTV_CHANNEL_NO_SELECT_ENABLE.name, value).apply()

    /** 是否启用直播源频道收藏 */
    var iptvChannelFavoriteEnable: Boolean
        get() = sp.getBoolean(KEY.IPTV_CHANNEL_FAVORITE_ENABLE.name, true)
        set(value) = sp.edit().putBoolean(KEY.IPTV_CHANNEL_FAVORITE_ENABLE.name, value).apply()

    /** 显示直播源频道收藏列表 */
    var iptvChannelFavoriteListVisible: Boolean
        get() = sp.getBoolean(KEY.IPTV_CHANNEL_FAVORITE_LIST_VISIBLE.name, false)
        set(value) = sp.edit().putBoolean(KEY.IPTV_CHANNEL_FAVORITE_LIST_VISIBLE.name, value)
            .apply()

    /** 只看收藏：仅展示收藏夹及其中频道，换台与频道号亦仅限收藏列表 */
    var iptvChannelFavoritesOnlyMode: Boolean
        get() = sp.getBoolean(KEY.IPTV_CHANNEL_FAVORITES_ONLY_MODE.name, false)
        set(value) = sp.edit().putBoolean(KEY.IPTV_CHANNEL_FAVORITES_ONLY_MODE.name, value).apply()

    /** 直播源频道收藏列表（旧版）；迁移逻辑见 [com.vesaa.mytv.data.IptvFavoriteMigration] */
    var iptvChannelFavoriteList: Set<String>
        get() = sp.getStringSet(KEY.IPTV_CHANNEL_FAVORITE_LIST.name, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY.IPTV_CHANNEL_FAVORITE_LIST.name, value).apply()

    /**
     * 当前订阅拉流用的请求头快照：优先该订阅 URL 在 JSON 里绑定的头，否则全局 [iptvSourceRequestHeaders]。
     * 写入收藏时固化，删除源后仍按快照播放。
     */
    fun currentIptvSourceRequestHeadersSnapshot(): String {
        val url = iptvSourceUrl.trim()
        if (url.isNotEmpty()) {
            val per = getIptvSourceHeadersForUrl(url).trim()
            if (per.isNotEmpty()) return per
        }
        return iptvSourceRequestHeaders.trim()
    }

    fun loadFavoriteEntries(): List<IptvFavoriteEntry> {
        val raw = sp.getString(KEY.IPTV_CHANNEL_FAVORITES_JSON.name, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return runCatching {
            spJson.decodeFromString(ListSerializer(IptvFavoriteEntry.serializer()), raw)
        }.getOrElse { emptyList() }
    }

    fun saveFavoriteEntries(list: List<IptvFavoriteEntry>) {
        sp.edit()
            .putString(
                KEY.IPTV_CHANNEL_FAVORITES_JSON.name,
                spJson.encodeToString(ListSerializer(IptvFavoriteEntry.serializer()), list),
            )
            .apply()
    }

    /** ==================== 节目单 ==================== */
    /** 启用节目单 */
    var epgEnable: Boolean
        get() = sp.getBoolean(KEY.EPG_ENABLE.name, true)
        set(value) = sp.edit().putBoolean(KEY.EPG_ENABLE.name, value).apply()

    /** 节目单 xml url */
    var epgXmlUrl: String
        get() = (sp.getString(KEY.EPG_XML_URL.name, "") ?: "").ifBlank { Constants.EPG_XML_URL }
        set(value) = sp.edit().putString(KEY.EPG_XML_URL.name, value).apply()

    /** 存储中节目单 URL 是否为空（生效地址来自内置默认，用于网页/文案提示） */
    val isEpgXmlUrlStoredBlank: Boolean
        get() = (sp.getString(KEY.EPG_XML_URL.name, "") ?: "").isBlank()

    /** 节目单刷新时间阈值（小时） */
    var epgRefreshTimeThreshold: Int
        get() = sp.getInt(KEY.EPG_REFRESH_TIME_THRESHOLD.name, Constants.EPG_REFRESH_TIME_THRESHOLD)
        set(value) = sp.edit().putInt(KEY.EPG_REFRESH_TIME_THRESHOLD.name, value).apply()

    /** 节目单历史列表 */
    var epgXmlUrlHistoryList: Set<String>
        get() = sp.getStringSet(KEY.EPG_XML_URL_HISTORY_LIST.name, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY.EPG_XML_URL_HISTORY_LIST.name, value).apply()

    /** 拉取节目单 XML 时使用的额外请求头（与 [epgXmlUrl] 成对；格式同 [iptvSourceRequestHeaders]） */
    var epgXmlRequestHeaders: String
        get() = sp.getString(KEY.EPG_XML_REQUEST_HEADERS.name, "") ?: ""
        set(value) = sp.edit().putString(KEY.EPG_XML_REQUEST_HEADERS.name, value).apply()

    private var epgXmlHeadersByUrlJsonRaw: String
        get() = sp.getString(KEY.EPG_XML_HEADERS_BY_URL_JSON.name, "{}") ?: "{}"
        set(value) = sp.edit().putString(KEY.EPG_XML_HEADERS_BY_URL_JSON.name, value).apply()

    /** 读取某节目单 URL 已保存的请求头文本 */
    fun getEpgHeadersForUrl(url: String): String {
        if (url.isBlank()) return ""
        val map = runCatching {
            spJson.decodeFromString<Map<String, String>>(epgXmlHeadersByUrlJsonRaw)
        }.getOrElse { emptyMap() }
        return map[url].orEmpty()
    }

    /** 将请求头文本与节目单 URL 绑定保存 */
    fun putEpgHeadersForUrl(url: String, headers: String) {
        if (url.isBlank()) return
        val map = runCatching {
            spJson.decodeFromString<Map<String, String>>(epgXmlHeadersByUrlJsonRaw).toMutableMap()
        }.getOrElse { mutableMapOf() }
        if (headers.isBlank()) {
            map.remove(url)
        } else {
            map[url] = headers
        }
        epgXmlHeadersByUrlJsonRaw = spJson.encodeToString(map)
    }

    /**
     * 网页/扫码推送节目单后同步落盘（地址与请求头一组；[apply] 异步可能导致未写入即杀进程）。
     */
    fun commitEpgWebSettings(url: String, requestHeaders: String) {
        val rawJson = sp.getString(KEY.EPG_XML_HEADERS_BY_URL_JSON.name, "{}") ?: "{}"
        val map = runCatching {
            spJson.decodeFromString<Map<String, String>>(rawJson).toMutableMap()
        }.getOrElse { mutableMapOf() }
        if (url.isNotBlank()) {
            if (requestHeaders.isBlank()) {
                map.remove(url)
            } else {
                map[url] = requestHeaders
            }
        }
        sp.edit()
            .putString(KEY.EPG_XML_URL.name, url)
            .putString(KEY.EPG_XML_REQUEST_HEADERS.name, requestHeaders)
            .putString(KEY.EPG_XML_HEADERS_BY_URL_JSON.name, spJson.encodeToString(map))
            .commit()
    }

    /** ==================== 界面 ==================== */
    /** 显示节目进度 */
    var uiShowEpgProgrammeProgress: Boolean
        get() = sp.getBoolean(KEY.UI_SHOW_EPG_PROGRAMME_PROGRESS.name, true)
        set(value) = sp.edit().putBoolean(KEY.UI_SHOW_EPG_PROGRAMME_PROGRESS.name, value).apply()

    /** 使用经典选台界面 */
    var uiUseClassicPanelScreen: Boolean
        get() = sp.getBoolean(KEY.UI_USE_CLASSIC_PANEL_SCREEN.name, true)
        set(value) = sp.edit().putBoolean(KEY.UI_USE_CLASSIC_PANEL_SCREEN.name, value).apply()

    /** 界面密度缩放比例 */
    var uiDensityScaleRatio: Float
        get() = sp.getFloat(KEY.UI_DENSITY_SCALE_RATIO.name, 1f)
        set(value) = sp.edit().putFloat(KEY.UI_DENSITY_SCALE_RATIO.name, value).apply()

    /** 界面字体缩放比例 */
    var uiFontScaleRatio: Float
        get() = sp.getFloat(KEY.UI_FONT_SCALE_RATIO.name, 1f)
        set(value) = sp.edit().putFloat(KEY.UI_FONT_SCALE_RATIO.name, value).apply()

    /** 时间显示模式 */
    var uiTimeShowMode: UiTimeShowMode
        get() = UiTimeShowMode.fromValue(sp.getInt(KEY.UI_TIME_SHOW_MODE.name, 0))
        set(value) = sp.edit().putInt(KEY.UI_TIME_SHOW_MODE.name, value.value).apply()

    /** 画中画模式 */
    var uiPipMode: Boolean
        get() = sp.getBoolean(KEY.UI_PIP_MODE.name, false)
        set(value) = sp.edit().putBoolean(KEY.UI_PIP_MODE.name, value).apply()

    /** ==================== 更新 ==================== */
    /** 更新强提醒（弹窗形式） */
    var updateForceRemind: Boolean
        get() = sp.getBoolean(KEY.UPDATE_FORCE_REMIND.name, false)
        set(value) = sp.edit().putBoolean(KEY.UPDATE_FORCE_REMIND.name, value).apply()

    /** 上次完整下载的安装包版本（空表示无有效缓存） */
    var updateLastDownloadedApkVersion: String
        get() = sp.getString(KEY.UPDATE_LAST_DOWNLOADED_APK_VERSION.name, "") ?: ""
        set(value) = sp.edit().putString(KEY.UPDATE_LAST_DOWNLOADED_APK_VERSION.name, value).apply()

    /** 上次完整下载的安装包下载地址 */
    var updateLastDownloadedApkUrl: String
        get() = sp.getString(KEY.UPDATE_LAST_DOWNLOADED_APK_URL.name, "") ?: ""
        set(value) = sp.edit().putString(KEY.UPDATE_LAST_DOWNLOADED_APK_URL.name, value).apply()

    /** ==================== 播放器 ==================== */
    /** 播放器 加载超时 */
    var videoPlayerLoadTimeout: Long
        get() = sp.getLong(KEY.VIDEO_PLAYER_LOAD_TIMEOUT.name, Constants.VIDEO_PLAYER_LOAD_TIMEOUT)
        set(value) = sp.edit().putLong(KEY.VIDEO_PLAYER_LOAD_TIMEOUT.name, value).apply()

    /** 播放器 画面比例 */
    var videoPlayerAspectRatio: VideoPlayerAspectRatio
        get() = VideoPlayerAspectRatio.fromValue(
            sp.getInt(KEY.VIDEO_PLAYER_ASPECT_RATIO.name, VideoPlayerAspectRatio.ORIGINAL.value)
        )
        set(value) = sp.edit().putInt(KEY.VIDEO_PLAYER_ASPECT_RATIO.name, value.value).apply()

    enum class UiTimeShowMode(val value: Int) {
        /** 隐藏 */
        HIDDEN(0),

        /** 常显 */
        ALWAYS(1),

        /** 整点 */
        EVERY_HOUR(2),

        /** 半点 */
        HALF_HOUR(3);

        companion object {
            fun fromValue(value: Int): UiTimeShowMode {
                return entries.firstOrNull { it.value == value } ?: ALWAYS
            }
        }
    }

    enum class AppDeviceDisplayType(val value: Int) {
        /** tv端 */
        LEANBACK(0),

        /** 手机端 */
        MOBILE(1),

        /** 平板端 */
        PAD(2);

        companion object {
            fun fromValue(value: Int): AppDeviceDisplayType {
                return entries.firstOrNull { it.value == value } ?: LEANBACK
            }
        }
    }

    enum class VideoPlayerAspectRatio(val value: Int) {
        /** 原始 */
        ORIGINAL(0),

        /** 16:9 */
        SIXTEEN_NINE(1),

        /** 4:3 */
        FOUR_THREE(2),

        /** 自动拉伸 */
        AUTO(3);

        companion object {
            fun fromValue(value: Int): VideoPlayerAspectRatio {
                return entries.firstOrNull { it.value == value } ?: ORIGINAL
            }
        }
    }
}