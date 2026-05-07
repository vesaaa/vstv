package com.vesaa.mytv.data.repositories.git.parser

import android.os.Build
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Release 含多个 APK 时，自动更新优先下载常规包：
 * - x86_64 设备优先：`vstv-x.y.z-x86_64.apk`
 * - 其他设备默认：`vstv-x.y.z-arm.apk`
 * HarmonyOS 变体（`vstv-x.y.z-HarmonyOS.apk`）保持手动下载，避免与常规包名混装。
 *
 * 历史版本命名（如 `-all-sdk21.apk`、`-all-sdk21-original.apk`）仍兼容回退匹配。
 */
fun JsonArray.pickVstvDefaultApkBrowserUrl(): String? {
    val apkPairs = mapNotNull { el ->
        val o = el.jsonObject
        val name = o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val url = o["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
        if (!name.endsWith(".apk", ignoreCase = true)) return@mapNotNull null
        name to url
    }
    val preferX86 = Build.SUPPORTED_ABIS.any { abi ->
        val normalized = abi.lowercase()
        normalized.contains("x86_64") || normalized == "x86"
    }
    if (preferX86) {
        apkPairs.firstOrNull { (name, _) ->
            name.endsWith("-x86_64.apk", ignoreCase = true)
        }?.second?.let { return it }
    }
    apkPairs.firstOrNull { (name, _) ->
        name.endsWith("-arm.apk", ignoreCase = true)
    }?.second?.let { return it }
    // 兼容旧版命名
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
