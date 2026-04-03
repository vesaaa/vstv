package com.vesaa.mytv.utils

import android.util.Base64
import com.vesaa.mytv.defaults.AppBuiltinEndpoints
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 出站 HTTP 前对直播 / 节目单请求头做统一处理。
 *
 * 当 [AppBuiltinEndpoints.REQUEST_SIGNING_KEY_B64] 非空时，在 **User-Agent** 末尾追加与资源 URL、UA 内容绑定的短标签（HMAC-SHA256）；
 * 密钥为空则**不改变**任何头（与官方加密 bundle 一致）。
 *
 * Fork 使用占位密钥时，即使用户在设置里填写「正确」UA，实际发出的字符串也与官方包不同；对校验严格的 CDN 可能失败。
 * 若将来需在官方包启用校验，在私有 bundle 中配置与源站约定的密钥即可。
 */
object IptvOutboundHeaderPolicy {

    fun blendUserAgentValue(userAgent: String, resourceUrl: String): String {
        val ua = userAgent.trim()
        if (ua.isEmpty()) return userAgent

        val keyB64 = AppBuiltinEndpoints.REQUEST_SIGNING_KEY_B64.trim()
        if (keyB64.isEmpty()) return userAgent

        val keyBytes =
            try {
                Base64.decode(keyB64, Base64.DEFAULT)
            } catch (_: Exception) {
                return userAgent
            }
        if (keyBytes.size < 16) return userAgent

        return try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
            val payload = "$resourceUrl\u0000$ua".toByteArray(StandardCharsets.UTF_8)
            val tag = mac.doFinal(payload).copyOfRange(0, 6)
            val enc =
                Base64.encodeToString(
                    tag,
                    Base64.NO_PADDING or Base64.NO_WRAP or Base64.URL_SAFE,
                )
            "$ua VsTV-$enc"
        } catch (_: Exception) {
            userAgent
        }
    }

    fun applyToNormalizedHeadersText(normalizedHeadersText: String, resourceUrl: String): String {
        val raw = normalizedHeadersText
        if (raw.isBlank()) return raw

        val map = linkedMapOf<String, String>()
        raw.parseHttpHeaderLines().forEach { (k, v) -> map[k] = v }

        val uaEntry = map.entries.find { it.key.equals("User-Agent", ignoreCase = true) } ?: return raw
        val blended = blendUserAgentValue(uaEntry.value, resourceUrl)
        if (blended == uaEntry.value) return raw

        val newMap = LinkedHashMap(map)
        newMap[uaEntry.key] = blended
        return newMap.entries.joinToString("\n") { "${it.key}: ${it.value}" }
    }
}
