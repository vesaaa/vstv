package com.vesaa.mytv.utils

import com.vesaa.mytv.data.entities.Iptv
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

private val catchupTokenRegex = Regex("""\$\{\((b|e)\)([^}]*)}""")

data class CatchupWindow(
    val startMs: Long,
    val endMs: Long,
)

object IptvCatchup {
    private const val MaxCatchupHours = 24
    private const val DefaultAppendTemplate = "?playseek=\${(b)yyyyMMddHHmmss}-\${(e)yyyyMMddHHmmss}"

    enum class Capability {
        SUPPORTED_BY_TEMPLATE,
        SUPPORTED_BY_DVR_URL,
        UNSUPPORTED,
    }

    fun maxCatchupHours(iptv: Iptv): Int {
        val bySource = if (iptv.catchupDays > 0) iptv.catchupDays * 24 else MaxCatchupHours
        return max(1, minOf(MaxCatchupHours, bySource))
    }

    fun capabilityOf(iptv: Iptv): Capability {
        if (iptv.catchupSource.trim().isNotEmpty()) return Capability.SUPPORTED_BY_TEMPLATE
        val hasDvrUrl = iptv.urlList.any { isLikelyDvrUrl(it) }
        if (hasDvrUrl) return Capability.SUPPORTED_BY_DVR_URL
        if (iptv.catchup.trim().equals("append", ignoreCase = true)) {
            return Capability.SUPPORTED_BY_TEMPLATE
        }
        return Capability.UNSUPPORTED
    }

    fun supportCatchup(iptv: Iptv): Boolean {
        return capabilityOf(iptv) != Capability.UNSUPPORTED
    }

    fun capabilityText(iptv: Iptv): String {
        return when (capabilityOf(iptv)) {
            Capability.SUPPORTED_BY_TEMPLATE -> "模板命中"
            Capability.SUPPORTED_BY_DVR_URL -> "DVR命中"
            Capability.UNSUPPORTED -> "不支持"
        }
    }

    fun buildCatchupUrl(
        iptv: Iptv,
        baseUrl: String,
        window: CatchupWindow,
    ): String? {
        val source = pickTemplate(iptv)
        if (source.isBlank()) return null
        return renderCatchupTemplate(source, window.startMs, window.endMs, baseUrl)
    }

    /**
     * 优先使用 M3U 声明的 catchup 模板或 DVR 推断；若无模板则按常见运营商习惯追加
     * `?playseek=yyyyMMddHHmmss-yyyyMMddHHmmss` 作为兜底，供节目单点击尝试回看。
     * 仅返回首个候选地址；兼容地址（如 PLTV→TVOD）由候选列表顺序重试。
     */
    fun buildCatchupUrlWithFallback(
        iptv: Iptv,
        baseUrl: String,
        window: CatchupWindow,
    ): String {
        return buildCatchupUrlCandidatesWithFallback(iptv, baseUrl, window).firstOrNull().orEmpty()
    }

    /**
     * 回看地址候选列表（按优先级）：
     * 1) 先使用源内模板原样 URL；
     * 2) 再尝试将路径段 PLTV/pltv 转成 TVOD/tvod 的兼容 URL；
     * 3) 若源内无模板，则对默认 append 模板应用同样策略。
     */
    fun buildCatchupUrlCandidatesWithFallback(
        iptv: Iptv,
        baseUrl: String,
        window: CatchupWindow,
    ): List<String> {
        val candidates = mutableListOf<String>()
        val primary = buildCatchupUrl(iptv, baseUrl, window)
        if (primary.isNullOrBlank()) {
            val fallback = renderCatchupTemplate(DefaultAppendTemplate, window.startMs, window.endMs, baseUrl)
            addPrimaryAndNormalizedCandidates(candidates, fallback)
        } else {
            addPrimaryAndNormalizedCandidates(candidates, primary)
        }
        return candidates
    }

    fun clampWindow(
        nowMs: Long = System.currentTimeMillis(),
        rawStartMs: Long,
        rawEndMs: Long,
        maxHours: Int,
    ): CatchupWindow? {
        if (rawEndMs <= rawStartMs) return null
        val earliest = nowMs - maxHours * 60L * 60L * 1000L
        val s = max(rawStartMs, earliest)
        val e = minOf(rawEndMs, nowMs)
        if (e <= s) return null
        return CatchupWindow(startMs = s, endMs = e)
    }

    private fun renderCatchupTemplate(template: String, b: Long, e: Long, baseUrl: String): String {
        val replaced = catchupTokenRegex.replace(template) { m ->
            val symbol = m.groupValues[1]
            val formatRaw = m.groupValues[2].ifBlank { "yyyyMMddHHmmss" }
            val ts = if (symbol == "b") b else e
            formatTs(ts, formatRaw)
        }.replace("\${url}", baseUrl)

        return if (replaced.startsWith("http://", true) || replaced.startsWith("https://", true)) {
            replaced
        } else if (replaced.startsWith("?") && "?" in baseUrl) {
            baseUrl + "&" + replaced.removePrefix("?")
        } else {
            baseUrl + replaced
        }
    }

    private fun pickTemplate(iptv: Iptv): String {
        val explicit = iptv.catchupSource.trim()
        if (explicit.isNotEmpty()) return explicit
        if (iptv.catchup.trim().equals("append", ignoreCase = true)) return DefaultAppendTemplate
        if (iptv.urlList.any { isLikelyDvrUrl(it) }) return DefaultAppendTemplate
        return ""
    }

    private fun isLikelyDvrUrl(url: String): Boolean {
        val u = url.trim().lowercase()
        if (u.isBlank()) return false
        return "/dvr/" in u || "playseek=" in u || "timeshift" in u ||
            "pltv" in u || "tvod" in u
    }

    /**
     * 参考 mytv-android `ChannelUtil.urlToCanPlayback`：多数 IPTV 直播流 URL 含 `pltv`，
     * 部分源在回看时需将路径段改为 `tvod`；这里仅生成兼容候选，不影响首选原始模板。
     */
    private fun normalizeCatchupStreamUrl(url: String): String {
        // 仅替换路径段 /PLTV/，并尽量保持原始大小写风格，避免区分大小写的源站 404。
        val pltvSegmentRegex = Regex("""(?i)(?<=/)pltv(?=/)""")
        return pltvSegmentRegex.replace(url) { m ->
            when (m.value) {
                "PLTV" -> "TVOD"
                "pltv" -> "tvod"
                else -> "Tvod"
            }
        }
    }

    private fun addPrimaryAndNormalizedCandidates(out: MutableList<String>, primaryUrl: String) {
        if (primaryUrl.isBlank()) return
        out += primaryUrl
        val normalized = normalizeCatchupStreamUrl(primaryUrl)
        if (!normalized.equals(primaryUrl, ignoreCase = false)) {
            out += normalized
        }
    }

    private fun formatTs(ts: Long, raw: String): String {
        val tzRegex = Regex("""\{(utc|local)}""", RegexOption.IGNORE_CASE)
        val tzMatch = tzRegex.find(raw)
        val timezone = if (tzMatch?.groupValues?.get(1)?.equals("utc", true) == true) {
            TimeZone.getTimeZone("UTC")
        } else {
            TimeZone.getDefault()
        }
        val pattern = raw.replace(tzRegex, "").ifBlank { "yyyyMMddHHmmss" }
        return runCatching {
            SimpleDateFormat(pattern, Locale.getDefault()).apply {
                timeZone = timezone
            }.format(Date(ts))
        }.getOrElse {
            (ts / 1000L).toString()
        }
    }
}
