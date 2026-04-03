package com.vesaa.mytv.defaults

/** 首装默认端点；发版构建可写入与仓库默认不同的取值。 */
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

    /** Base64 HMAC 密钥；空表示出站不改写 UA。 */
    const val REQUEST_SIGNING_KEY_B64: String =
        "w7zT2KpQm8vNx0R4fLc9HgYaJb3DeU5oStViMn1qXr0="
}
