package com.vesaa.mytv.data.repositories.git.parser

import android.os.Build
import com.vesaa.mytv.BuildConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 根据**当前安装的包**从 GitHub Release 附件中选唯一匹配的 APK，**禁止跨渠道混用**：
 *
 * - **Z视介 / 鸿蒙兼容包**（`APPLICATION_ID=com.chinablue.tv`）：只匹配鸿蒙发行附件（如 `vstv-*-HarmonyOS.apk`），
 *   不能选原味 `arm` / `x86_64`。在 x86 设备上若 Release 仅有 ARM 版鸿蒙包，则无可用更新（返回 null）。
 * - **原味 ARM**（`com.vesaa.mytv` 且本机为 armeabi / arm64）：只匹配 `vstv-*-arm.apk`（及历史命名回退），不选鸿蒙包、不选 x86_64。
 * - **原味 x86_64**：只匹配 `vstv-*-x86_64.apk`，无该附件则返回 null，**不回退** arm，避免 ABI 错误。
 *
 * 历史命名（`-all-sdk21.apk`、`-all-sdk21-original.apk` 等）仅用于**非鸿蒙**原味链路兼容。
 */
fun JsonArray.pickVstvDefaultApkBrowserUrl(): String? {
    val apkPairs = mapNotNull { el ->
        val o = el.jsonObject
        val name = o["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val url = o["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
        if (!name.endsWith(".apk", ignoreCase = true)) return@mapNotNull null
        name to url
    }
    val isHarmonyChannel = BuildConfig.APPLICATION_ID.equals("com.chinablue.tv", ignoreCase = true)
    val preferX86 = Build.SUPPORTED_ABIS.any { abi ->
        val normalized = abi.lowercase()
        normalized.contains("x86_64") || normalized == "x86"
    }

    if (isHarmonyChannel) {
        if (preferX86) {
            // 若以后上架鸿蒙 x86 专包，在此匹配；当前 CI 仅打 disguisedArm，勿把 ARM 鸿蒙包装到 x86 上装
            apkPairs.firstOrNull { (name, _) ->
                name.endsWith("-HarmonyOS-x86_64.apk", ignoreCase = true) ||
                    name.endsWith("-all-sdk21-HarmonyOS-x86_64.apk", ignoreCase = true)
            }?.second?.let { return it }
            return null
        }
        apkPairs.firstOrNull { (name, _) ->
            name.endsWith("-HarmonyOS.apk", ignoreCase = true) ||
                name.endsWith("-all-sdk21-HarmonyOS.apk", ignoreCase = true)
        }?.second?.let { return it }
        return null
    }

    // 以下为 com.vesaa.mytv 原味；按 CPU 二选一，与鸿蒙附件互斥
    if (preferX86) {
        apkPairs.firstOrNull { (name, _) ->
            name.endsWith("-x86_64.apk", ignoreCase = true) &&
                !name.contains("HarmonyOS", ignoreCase = true)
        }?.second?.let { return it }
        return null
    }

    apkPairs.firstOrNull { (name, _) ->
        name.endsWith("-arm.apk", ignoreCase = true) &&
            !name.contains("HarmonyOS", ignoreCase = true)
    }?.second?.let { return it }
    apkPairs.firstOrNull { (name, _) ->
        name.endsWith("-all-sdk21.apk", ignoreCase = true) &&
            !name.contains("-lite", ignoreCase = true)
    }?.second?.let { return it }
    apkPairs.firstOrNull { (name, _) ->
        name.endsWith("-all-sdk21-original.apk", ignoreCase = true)
    }?.second?.let { return it }
    return apkPairs.firstOrNull { (name, _) ->
        !name.contains("HarmonyOS", ignoreCase = true) &&
            !name.contains("disguised", ignoreCase = true)
    }?.second ?: apkPairs.firstOrNull()?.second
}
