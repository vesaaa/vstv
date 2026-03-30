package top.yogiczy.mytv.utils

import okhttp3.Headers

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
