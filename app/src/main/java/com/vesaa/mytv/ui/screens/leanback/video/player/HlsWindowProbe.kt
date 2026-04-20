package com.vesaa.mytv.ui.screens.leanback.video.player

import com.vesaa.mytv.ui.utils.SP
import com.vesaa.mytv.utils.IptvOutboundHeaderPolicy
import com.vesaa.mytv.utils.normalizeIptvRequestHeadersInput
import com.vesaa.mytv.utils.parseHttpHeaderLines
import java.net.HttpURLConnection
import java.net.URL

/**
 * 抓取并解析 HLS media playlist，推断其滑动窗口总时长（所有 `#EXTINF` 之和，毫秒）。
 *
 * 用于在切台时拿到源的真实窗口大小，从而按接近窗口末端起播以最大化开播缓冲余量。
 * - 主清单（含 `#EXT-X-STREAM-INF`）不处理：由 ExoPlayer 自己选择变体后再决定
 * - VOD 清单（含 `#EXT-X-ENDLIST` 或 `PLAYLIST-TYPE:VOD`）不处理：非直播无需起播点优化
 * - 网络请求失败、格式不合法等情况均返回 `null`，由调用方回退到默认 LiveConfiguration
 */
internal object HlsWindowProbe {
    private const val MAX_BYTES = 131_072 // 128KB 足够覆盖绝大多数直播清单
    private const val CONNECT_TIMEOUT_MS = 2000
    private const val READ_TIMEOUT_MS = 2000

    fun fetchWindowMs(url: String, streamRequestHeaders: String?): Long? {
        if (url.isEmpty()) return null
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as? HttpURLConnection ?: return null
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            applyHeaders(conn, url, streamRequestHeaders)

            val code = conn.responseCode
            if (code !in 200..299) return null

            val text = conn.inputStream.use { stream ->
                val buf = ByteArray(MAX_BYTES)
                var total = 0
                while (total < MAX_BYTES) {
                    val read = stream.read(buf, total, MAX_BYTES - total)
                    if (read <= 0) break
                    total += read
                }
                if (total <= 0) null else String(buf, 0, total, Charsets.UTF_8)
            }
            text?.let { parseWindowMs(it) }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun applyHeaders(conn: HttpURLConnection, url: String, streamRequestHeaders: String?) {
        val trimmed = streamRequestHeaders?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            conn.setRequestProperty(
                "User-Agent",
                IptvOutboundHeaderPolicy.blendUserAgentValue(SP.playbackHttpUserAgent(), url),
            )
            return
        }

        val norm = normalizeIptvRequestHeadersInput(trimmed)
        val blended = IptvOutboundHeaderPolicy.applyToNormalizedHeadersText(norm, url)
        val map = blended.parseHttpHeaderLines()
        val ua = map.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value?.trim()?.takeIf { it.isNotEmpty() }
        conn.setRequestProperty(
            "User-Agent",
            ua ?: IptvOutboundHeaderPolicy.blendUserAgentValue(SP.playbackHttpUserAgent(), url),
        )
        map.filterKeys { !it.equals("User-Agent", ignoreCase = true) }.forEach { (k, v) ->
            runCatching { conn.setRequestProperty(k, v) }
        }
    }

    internal fun parseWindowMs(text: String): Long? {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("#EXTM3U")) return null
        if (trimmed.contains("#EXT-X-STREAM-INF")) return null
        if (trimmed.contains("#EXT-X-ENDLIST")) return null
        if (VOD_TYPE_REGEX.containsMatchIn(trimmed)) return null

        var total = 0.0
        var segCount = 0
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trimStart()
            if (!line.startsWith("#EXTINF:")) continue
            val value = line.substring("#EXTINF:".length).substringBefore(",").trim()
            val v = value.toDoubleOrNull() ?: continue
            if (v > 0) {
                total += v
                segCount += 1
            }
        }
        if (segCount < 2 || total < 5.0) return null
        return (total * 1000).toLong()
    }

    private val VOD_TYPE_REGEX = Regex("""PLAYLIST-TYPE\s*:\s*VOD""", RegexOption.IGNORE_CASE)
}


