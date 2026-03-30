package top.yogiczy.mytv.utils

import okhttp3.Headers

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
