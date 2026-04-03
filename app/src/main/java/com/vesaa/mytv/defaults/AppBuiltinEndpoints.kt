package com.vesaa.mytv.defaults

/**
 * 首装或设置未填写时的内置端点缺省，可在应用内设置覆盖。
 * 正式安装包由构建流程写入与本仓库默认不同的取值。
 */
object AppBuiltinEndpoints {
    const val GIT_RELEASE_LATEST_API =
        "https://api.github.com/repos/octocat/Hello-World/releases/latest"

    const val IPTV_DEFAULT_SUBSCRIPTION_URL =
        "https://example.invalid/vstv/builtin-subscription.m3u8"

    const val IPTV_DEFAULT_REQUEST_HEADERS = "aptv"

    const val EPG_XML_PRIMARY =
        "https://example.invalid/vstv/builtin-epg-primary.xml.gz"

    const val EPG_XML_SECONDARY =
        "https://example.invalid/vstv/builtin-epg-secondary.xml.gz"

    val EPG_BUILTIN_ORDERED: List<String> =
        listOf(EPG_XML_PRIMARY, EPG_XML_SECONDARY)
}
