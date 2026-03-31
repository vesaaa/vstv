package top.yogiczy.mytv.data.entities

import kotlinx.serialization.Serializable

/**
 * 频道节目单
 */
@Serializable
data class Epg(
    /**
     * 频道名称（XML display-name）
     */
    val channel: String = "",

    /**
     * 节目列表
     */
    val programmes: EpgProgrammeList = EpgProgrammeList(),

    /**
     * XMLTV channel 的 id，与 M3U 的 tvg-id 对应
     */
    val channelId: String = "",
) {
    /** 是否与该直播频道为同一套节目单（名称忽略大小写，或 tvg-id 与 channel id 一致） */
    fun matchesIptv(iptv: Iptv): Boolean {
        val id = channelId.trim()
        val t = iptv.tvgId.trim()
        if (id.isNotEmpty() && t.isNotEmpty() && id == t) return true
        val cn = iptv.channelName.trim()
        if (channel.isNotEmpty() && cn.isNotEmpty() && channel.equals(cn, ignoreCase = true)) return true
        val nm = iptv.name.trim()
        if (channel.isNotEmpty() && nm.isNotEmpty() && channel.equals(nm, ignoreCase = true)) return true
        return false
    }

    companion object {
        /**
         * 当前节目/下一个节目
         */
        fun Epg.currentProgrammes(): EpgProgrammeCurrent? {
            if (programmes.isEmpty()) return null
            val nowMs = System.currentTimeMillis()

            val timed = programmes
                .filter { it.endAt > it.startAt && it.startAt > 0L }
                .sortedBy { it.startAt }

            if (timed.isEmpty()) {
                val fallback = programmes.filter { it.title.isNotBlank() }.ifEmpty { programmes.toList() }
                if (fallback.isEmpty()) return null
                return EpgProgrammeCurrent(now = fallback.first(), next = fallback.getOrNull(1))
            }

            val liveIdx = timed.indexOfFirst { nowMs >= it.startAt && nowMs < it.endAt }
            if (liveIdx >= 0) {
                return EpgProgrammeCurrent(
                    now = timed[liveIdx],
                    next = timed.getOrNull(liveIdx + 1),
                )
            }

            if (nowMs < timed.first().startAt) {
                return EpgProgrammeCurrent(now = null, next = timed.first())
            }

            if (nowMs >= timed.last().endAt) {
                return EpgProgrammeCurrent(now = timed.last(), next = null)
            }

            val nextStart = timed.firstOrNull { nowMs < it.startAt }
            return EpgProgrammeCurrent(now = null, next = nextStart)
        }
    }
}