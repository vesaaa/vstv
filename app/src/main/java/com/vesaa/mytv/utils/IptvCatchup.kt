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

    fun maxCatchupHours(iptv: Iptv): Int {
        val bySource = if (iptv.catchupDays > 0) iptv.catchupDays * 24 else MaxCatchupHours
        return max(1, minOf(MaxCatchupHours, bySource))
    }

    fun supportCatchup(iptv: Iptv): Boolean {
        return iptv.catchupSource.isNotBlank() || iptv.catchup.isNotBlank()
    }

    fun buildCatchupUrl(
        iptv: Iptv,
        baseUrl: String,
        window: CatchupWindow,
    ): String? {
        val source = iptv.catchupSource.trim()
        if (source.isBlank()) return null
        return renderCatchupTemplate(source, window.startMs, window.endMs, baseUrl)
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
        } else {
            baseUrl + replaced
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
