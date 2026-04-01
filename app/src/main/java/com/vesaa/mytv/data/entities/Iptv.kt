package com.vesaa.mytv.data.entities

import androidx.compose.runtime.Immutable

/**
 * 直播源
 */
@Immutable
data class Iptv(
    /**
     * 直播源名称
     */
    val name: String = "",

    /**
     * 频道名称，用于查询节目单
     */
    val channelName: String = "",

    /**
     * M3U 中 tvg-id，常与 XMLTV 的 channel id 一致；用于与节目单匹配
     */
    val tvgId: String = "",

    /**
     * 播放地址
     */
    val urlList: List<String> = emptyList(),
) {
    companion object {
        val EXAMPLE = Iptv(
            name = "CCTV-1",
            channelName = "cctv1",
            tvgId = "1",
            urlList = listOf(
                "http://dbiptv.sn.chinamobile.com/PLTV/88888890/224/3221226231/index.m3u8",
                "http://[2409:8087:5e01:34::20]:6610/ZTE_CMS/00000001000000060000000000000131/index.m3u8?IAS",
            ),
        )
    }
}