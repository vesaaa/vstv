package com.vesaa.mytv.data.repositories.iptv.parser

import com.vesaa.mytv.data.entities.Iptv
import com.vesaa.mytv.data.entities.IptvGroup
import com.vesaa.mytv.data.entities.IptvGroupList
import com.vesaa.mytv.data.entities.IptvList

class M3uIptvParser : IptvParser {

    /** 在「空格 + #EXTINF」处切开，保留每条以 #EXTINF 开头 */
    private val extinfSplitRegex = Regex("""\s+(?=#EXTINF)""")

    private val streamUrlPrefix =
        Regex("""^(rtsp|https?|udp)://""", RegexOption.IGNORE_CASE)

    private val streamUrlInExtinf =
        Regex("""\b(rtsp|https?|udp)://[^\s#]+""", RegexOption.IGNORE_CASE)

    override fun isSupport(url: String, data: String): Boolean {
        val normalized = data.trimStart('\uFEFF')
        val first = normalized.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return first.startsWith("#EXTM3U") || first.startsWith("#EXTINF")
    }

    override suspend fun parse(data: String): IptvGroupList {
        val rawLines = data.trimStart('\uFEFF').split("\r\n", "\n").map { it.trimEnd() }
        val lines = expandLinesWithMultipleExtinf(rawLines)
        val iptvList = mutableListOf<IptvResponseItem>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!line.startsWith("#EXTINF")) {
                i++
                continue
            }

            val next = lines.getOrNull(i + 1).orEmpty().trim()
            val nextLooksLikeUrl =
                next.isNotBlank() &&
                    !next.startsWith("#") &&
                    streamUrlPrefix.containsMatchIn(next)

            var url = if (nextLooksLikeUrl) next else ""
            if (url.isBlank()) {
                url = extractStreamUrlFromExtinfLine(line)
            }
            if (url.isBlank() || url.startsWith("#")) {
                i++
                continue
            }

            var name = line.split(",").last().trim()
            if (name.contains(url)) {
                name = name.replace(url, "").trim()
            }
            val channelName = Regex("""tvg-name="(.+?)"""").find(line)?.groupValues?.get(1) ?: name
            val tvgId = Regex("""tvg-id="(.+?)"""").find(line)?.groupValues?.get(1)?.trim().orEmpty()
            val logoUrl =
                Regex("""tvg-logo="(.+?)"""").find(line)?.groupValues?.get(1)?.trim().orEmpty()
            val groupName = Regex("""group-title="(.+?)"""").find(line)?.groupValues?.get(1) ?: "其他"

            iptvList.add(
                IptvResponseItem(
                    name = name.trim(),
                    channelName = channelName.trim(),
                    tvgId = tvgId,
                    logoUrl = logoUrl,
                    groupName = groupName.trim(),
                    url = url,
                )
            )

            i += if (nextLooksLikeUrl) 2 else 1
        }

        return IptvGroupList(iptvList.groupBy { it.groupName }.map { groupEntry ->
            IptvGroup(
                name = groupEntry.key,
                iptvList = IptvList(groupEntry.value.groupBy { it.name }.map { nameEntry ->
                    val rows = nameEntry.value
                    Iptv(
                        name = nameEntry.key,
                        channelName = rows.first().channelName,
                        tvgId = rows.first().tvgId,
                        logoUrl = rows.firstOrNull { it.logoUrl.isNotBlank() }?.logoUrl?.trim().orEmpty(),
                        urlList = rows.map { it.url },
                    )
                })
            )
        })
    }

    /**
     * 将「同一物理行内多个 #EXTINF … url … #EXTINF …」拆成多行（常见于联通等内网源整行粘贴）。
     */
    private fun expandLinesWithMultipleExtinf(rawLines: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (line in rawLines) {
            if (line.isBlank()) continue
            if (!line.contains("#EXTINF")) {
                out.add(line.trim())
                continue
            }
            val parts = line.split(extinfSplitRegex).map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size > 1) {
                out.addAll(parts)
            } else {
                out.add(line.trim())
            }
        }
        return out
    }

    private data class IptvResponseItem(
        val name: String,
        val channelName: String,
        val tvgId: String,
        val logoUrl: String,
        val groupName: String,
        val url: String,
    )

    /**
     * 从 #EXTINF 行内提取流地址（rtsp / http(s) / udp）。
     * 说明：播放器侧目前对 **udp://** 组播未做专门支持，解析出来也可能无法播放。
     */
    private fun extractStreamUrlFromExtinfLine(extinfLine: String): String {
        val m = streamUrlInExtinf.find(extinfLine) ?: return ""
        return m.value.trim()
    }
}
