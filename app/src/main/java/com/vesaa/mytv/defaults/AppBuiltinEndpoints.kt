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

    /**
     * Base64 编码的 HMAC 密钥（建议 32 字节）。**空字符串** 表示不在出站请求中改写 User-Agent。
     * 占位构建使用非空伪密钥，使未覆盖 bundle 的安装包对 UA 追加与官方不一致的后缀。
     */
    const val REQUEST_SIGNING_KEY_B64: String =
        "w7zT2KpQm8vNx0R4fLc9HgYaJb3DeU5oStViMn1qXr0="
}
