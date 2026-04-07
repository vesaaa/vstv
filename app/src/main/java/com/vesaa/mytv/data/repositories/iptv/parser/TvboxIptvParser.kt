package com.vesaa.mytv.data.repositories.iptv.parser

import com.vesaa.mytv.data.entities.Iptv
import com.vesaa.mytv.data.entities.IptvGroup
import com.vesaa.mytv.data.entities.IptvGroupList
import com.vesaa.mytv.data.entities.IptvList

class TvboxIptvParser : IptvParser {

    override fun isSupport(url: String, data: String): Boolean {
        return data.contains("#genre#")
    }

    override suspend fun parse(data: String): IptvGroupList {
        val lines = data.split("\r\n", "\n")
        val iptvList = mutableListOf<IptvResponseItem>()

        var groupName: String? = null
        lines.forEach { line ->
            if (line.isBlank() || line.startsWith("#")) return@forEach

            if (line.contains("#genre#")) {
                groupName = line.split(",").first()
            } else {
                val res = line.replace("，", ",").split(",")
                if (res.size < 2) return@forEach

                iptvList.addAll(res[1].split("#").map { url ->
                    val normalizedUrl = normalizeChannelUrl(url)
                    IptvResponseItem(
                        name = res[0].trim(),
                        channelName = res[0].trim(),
                        groupName = groupName?.trim() ?: "其他",
                        url = normalizedUrl,
                    )
                }.filter { it.url.isNotBlank() })
            }
        }

        return IptvGroupList(iptvList.groupBy { it.groupName }.map { groupEntry ->
            IptvGroup(
                name = groupEntry.key,
                iptvList = IptvList(groupEntry.value.groupBy { it.name }.map { nameEntry ->
                    Iptv(
                        name = nameEntry.key,
                        channelName = nameEntry.value.first().channelName,
                        tvgId = "",
                        urlList = nameEntry.value.map { it.url },
                    )
                }),
            )
        })
    }

    private data class IptvResponseItem(
        val name: String,
        val channelName: String,
        val groupName: String,
        val url: String,
    )

    /** 兼容 `url$1920×1080` 这类分辨率后缀，仅保留可请求的真实地址 */
    private fun normalizeChannelUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        return trimmed.substringBefore("$").trim()
    }
}