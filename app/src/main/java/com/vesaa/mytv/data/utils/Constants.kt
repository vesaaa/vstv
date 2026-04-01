package com.vesaa.mytv.data.utils

/**
 * 常量
 */
object Constants {
    /**
     * 应用对外展示名称（桌面、关于、网页设置页等）
     */
    const val APP_TITLE = "VsTV"

    /**
     * 应用 代码仓库
     */
    const val APP_REPO = "https://github.com/vesaaa/vstv"

    /**
     * IPTV 源地址占位（不再内置可播地址；留空由用户扫码/网页配置）
     */
    const val IPTV_SOURCE_URL = ""

    /**
     * IPTV源缓存时间（毫秒）
     */
    const val IPTV_SOURCE_CACHE_TIME = 1000 * 60 * 60 * 24L // 24小时

    /**
     * 节目单默认地址（gzip XMLTV）。亦可改为同站明文：`http://epg.51zmt.top:8000/e.xml`。
     * 存储为空时生效；默认无自定义 UA。
     */
    const val EPG_XML_URL = "http://epg.51zmt.top:8000/e1.xml.gz"

    /** 内置 APTV 节目单；未单独配置 UA 时拉取使用 User-Agent: aptv */
    const val EPG_XML_URL_APTV = "https://epg.aptv.app/pp.xml.gz"

    /** 内置节目单地址列表（顺序：默认 51zmt → APTV），勿写入历史 URL 集合 */
    val EPG_BUILTIN_XML_URLS: List<String> = listOf(EPG_XML_URL, EPG_XML_URL_APTV)

    /**
     * 节目单刷新时间阈值（小时）
     */
    const val EPG_REFRESH_TIME_THRESHOLD = 2 // 不到2点不刷新

    /**
     * Git最新版本信息
     */
    const val GIT_RELEASE_LATEST_URL =
        "https://api.github.com/repos/vesaaa/vstv/releases/latest"

    /** 历史占位：曾拼接至 APK 下载 URL；默认更新已改为 GitHub 直链，避免第三方镜像失效。 */
    const val GITHUB_PROXY = "https://mirror.ghproxy.com/"

    /**
     * HTTP请求重试次数
     */
    const val HTTP_RETRY_COUNT = 10L

    /**
     * HTTP请求重试间隔时间（毫秒）
     */
    const val HTTP_RETRY_INTERVAL = 3000L

    /**
     * 播放器 userAgent
     */
    const val VIDEO_PLAYER_USER_AGENT = "ExoPlayer"

    /**
     * 日志历史最大保留条数
     */
    const val LOG_HISTORY_MAX_SIZE = 50

    /**
     * 播放器加载超时
     */
    const val VIDEO_PLAYER_LOAD_TIMEOUT = 1000L * 15 // 15秒

    /**
     * 界面 超时未操作自动关闭界面
     */
    const val UI_SCREEN_AUTO_CLOSE_DELAY = 1000L * 15 // 15秒

    /**
     * 界面 时间显示前后范围
     */
    const val UI_TIME_SHOW_RANGE = 1000L * 30 // 前后30秒

    /**
     * 界面 临时面板界面显示时间
     */
    const val UI_TEMP_PANEL_SCREEN_SHOW_DURATION = 1500L // 1.5秒
}