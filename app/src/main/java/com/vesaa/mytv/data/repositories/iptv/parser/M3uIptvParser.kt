package com.vesaa.mytv.data.repositories.iptv.parser

import com.vesaa.mytv.data.entities.Iptv
import com.vesaa.mytv.data.entities.IptvGroup
import com.vesaa.mytv.data.entities.IptvGroupList
import com.vesaa.mytv.data.entities.IptvList

class M3uIptvParser : IptvParser {

    override fun isSupport(url: String, data: String): Boolean {
        return data.startsWith("#EXTM3U")
    }

    override suspend fun parse(data: String): IptvGroupList {
        val lines = data.split("\r\n", "\n")
        val iptvList = mutableListOf<IptvResponseItem>()

        lines.forEachIndexed { index, line ->
            if (!line.startsWith("#EXTINF")) return@forEachIndexed

            val name = line.split(",").last()
            val channelName = Regex("""tvg-name="(.+?)"""").find(line)?.groupValues?.get(1) ?: name
            val tvgId = Regex("""tvg-id="(.+?)"""").find(line)?.groupValues?.get(1)?.trim().orEmpty()
            val groupName = Regex("""group-title="(.+?)"""").find(line)?.groupValues?.get(1) ?: "其他"

            iptvList.add(
                IptvResponseItem(
                    name = name.trim(),
                    channelName = channelName.trim(),
                    tvgId = tvgId,
                    groupName = groupName.trim(),
                    url = lines[index + 1].trim(),
                )
            )
        }

        return IptvGroupList(iptvList.groupBy { it.groupName }.map { groupEntry ->
            IptvGroup(
                name = groupEntry.key,
                iptvList = IptvList(groupEntry.value.groupBy { it.name }.map { nameEntry ->
                    Iptv(
                        name = nameEntry.key,
                        channelName = nameEntry.value.first().channelName,
                        tvgId = nameEntry.value.first().tvgId,
                        urlList = nameEntry.value.map { it.url },
                    )
                })
            )
        })
    }

    private data class IptvResponseItem(
        val name: String,
        val channelName: String,
        val tvgId: String,
        val groupName: String,
        val url: String,
    )
}