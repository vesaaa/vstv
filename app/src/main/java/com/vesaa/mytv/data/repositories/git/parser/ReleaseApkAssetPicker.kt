package com.vesaa.mytv.data.repositories.git.parser

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Release 含多个 APK 时，自动更新应下载与当前安装类型一致的包：
 * - 常规：`vstv-x.y.z-all-sdk21.apk`（不以 `-HarmonyOS` / `-lite` 结尾）
 * - lite（无台标）：`vstv-x.y.z-all-sdk21-lite.apk`
 */
fun JsonArray.pickVstvDefaultApkBrowserUrl(preferLiteApk: Boolean = false): String? {
    val apkPairs = mapNotNull { el ->
        val o = el.jsonObject
        val name = o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val url = o["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
        if (!name.endsWith(".apk", ignoreCase = true)) return@mapNotNull null
        name to url
    }
    if (preferLiteApk) {
        apkPairs.firstOrNull { (name, _) ->
            name.endsWith("-all-sdk21-lite.apk", ignoreCase = true)
        }?.second?.let { return it }
    }
    apkPairs.firstOrNull { (name, _) ->
        name.endsWith("-all-sdk21.apk", ignoreCase = true) &&
            !name.contains("-lite", ignoreCase = true)
    }?.second?.let { return it }
    // 旧版 Release 命名：…-all-sdk21-original.apk
    apkPairs.firstOrNull { (name, _) ->
        name.endsWith("-all-sdk21-original.apk", ignoreCase = true)
    }?.second?.let { return it }
    return apkPairs.firstOrNull { (name, _) ->
        !name.contains("HarmonyOS", ignoreCase = true) &&
            !name.contains("disguised", ignoreCase = true)
    }?.second ?: apkPairs.firstOrNull()?.second
}
