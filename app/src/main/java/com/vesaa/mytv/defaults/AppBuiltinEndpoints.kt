package com.vesaa.mytv.defaults

/**
 * 首装默认端点与更新检查地址（与源码同仓维护；不再使用 CI 解密 proprietary bundle）。
 */
object AppBuiltinEndpoints {
    const val GIT_RELEASE_LATEST_API =
        "https://api.github.com/repos/vesaaa/vstv/releases/latest"

    const val IPTV_DEFAULT_SUBSCRIPTION_URL = "https://bit.ly/jsnzkpg"

    const val IPTV_DEFAULT_REQUEST_HEADERS = "aptv"

    const val EPG_XML_PRIMARY = "http://epg.51zmt.top:8000/e1.xml.gz"

    const val EPG_XML_SECONDARY = "https://epg.aptv.app/pp.xml.gz"

    val EPG_BUILTIN_ORDERED: List<String> =
        listOf(EPG_XML_PRIMARY, EPG_XML_SECONDARY)

    /** 空：出站不改写 User-Agent；若需与自建源校验 UA，可改为足够长度的密钥 Base64。 */
    const val REQUEST_SIGNING_KEY_B64: String = ""
}
