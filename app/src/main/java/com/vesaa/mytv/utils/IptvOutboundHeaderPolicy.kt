package com.vesaa.mytv.utils

import android.util.Base64
import com.vesaa.mytv.defaults.AppBuiltinEndpoints
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** 出站前按 [com.vesaa.mytv.defaults.AppBuiltinEndpoints] 调整直播相关请求的 User-Agent。 */
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
