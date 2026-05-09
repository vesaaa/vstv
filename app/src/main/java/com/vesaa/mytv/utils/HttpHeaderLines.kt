package com.vesaa.mytv.utils

import okhttp3.Headers
import com.vesaa.mytv.data.utils.Constants

/**
 * 规范化订阅拉取用的请求头文本。
 * - 若整体为**单行**且不含 `:`，视为仅填写了 **User-Agent 取值**，自动变为 `User-Agent: 取值`。
 * - 多行或已含 `Name: Value` 的，按原文保留（仍走 [parseHttpHeaderLines]）。
 */
fun normalizeIptvRequestHeadersInput(text: String): String {
    val t = text.trim()
    if (t.isEmpty()) return ""
    val singleLine = !t.contains('\r') && !t.contains('\n')
    if (singleLine && !t.contains(':')) {
        return "User-Agent: $t"
    }
    return t
}

/**
 * 将多行「Name: Value」解析为键值对（忽略空行与非法行）。
 */
fun String.parseHttpHeaderLines(): Map<String, String> {
    val map = linkedMapOf<String, String>()
    lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .forEach { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) return@forEach
            val name = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim()
            if (name.isNotEmpty()) {
                map[name] = value
            }
        }
    return map
}

fun Map<String, String>.toOkHttpHeaders(): Headers {
    val b = Headers.Builder()
    forEach { (k, v) ->
        if (k.isNotBlank()) {
            b.add(k, v)
        }
    }
    return b.build()
}

/** 内置节目单 URL 在未写入自定义请求头时的默认头（与网页/设置中成组逻辑一致） */
fun builtinEpgDefaultRequestHeaders(xmlUrl: String): String {
    return when (xmlUrl.trim()) {
        Constants.EPG_XML_URL_APTV -> normalizeIptvRequestHeadersInput("aptv")
        else -> ""
    }
}

/**
 * 直播源 M3U `x-tvg-url` / `url-tvg` 在未单独配置请求头时的默认 User-Agent。
 * 与常见 IPTV 客户端拉取第三方 EPG 时的行为一致。
 */
private const val EPG_M3U_EMBEDDED_DEFAULT_UA = "AptvPlayer/1.5.2"

/**
 * 用户未配置 EPG 请求头时的兜底顺序：
 * 1) 与内置 APTV 节目单地址一致 → `aptv` UA；
 * 2) 当前拉取地址与直播源内嵌 `x-tvg-url` 一致 → [EPG_M3U_EMBEDDED_DEFAULT_UA]；
 * 3) 否则空（由 OkHttp 使用系统默认 UA）。
 */
fun defaultEpgRequestHeadersAfterUserEmpty(xmlUrl: String, embeddedXTvgUrlFromM3u: String): String {
    val u = xmlUrl.trim()
    val builtIn = builtinEpgDefaultRequestHeaders(u)
    if (builtIn.isNotBlank()) return builtIn
    val embedded = embeddedXTvgUrlFromM3u.trim()
    if (embedded.isNotEmpty() && u == embedded) {
        return normalizeIptvRequestHeadersInput(EPG_M3U_EMBEDDED_DEFAULT_UA)
    }
    return ""
}

/** 从多行请求头文本中提取 User-Agent 取值（用于设置页摘要）；未配置时返回空串 */
fun userAgentValueFromHeadersText(text: String): String {
    val norm = normalizeIptvRequestHeadersInput(text)
    if (norm.isBlank()) return ""
    return norm.parseHttpHeaderLines().entries
        .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
        ?.value?.trim().orEmpty()
}
