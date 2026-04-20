package com.vesaa.mytv.data.repositories.git.parser

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Release 含多个 APK 时，自动更新默认下载 **常规 vstv** 包：`vstv-x.y.z-all-sdk21.apk`
 *（不以 `-HarmonyOS`、`-disguised` 等变体后缀结尾）。HarmonyOS 变体需用户手动选择下载。
 *
 * 历史上还有 `-lite.apk`（已在 1.9.14 移除编译），和 `-original.apk`（更早命名，仍兼容）。
 * 这些旧附件名不再匹配常规包，用户仍可从 Release 页手动下载。
 */
fun JsonArray.pickVstvDefaultApkBrowserUrl(): String? {
    val apkPairs = mapNotNull { el ->
        val o = el.jsonObject
        val name = o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val url = o["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
        if (!name.endsWith(".apk", ignoreCase = true)) return@mapNotNull null
        name to url
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
