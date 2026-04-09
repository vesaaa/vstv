package com.vesaa.mytv.data.entities

import kotlinx.serialization.Serializable

/**
 * 持久化收藏：含播放地址与拉流时使用的 HTTP 头快照（与设置里订阅请求头格式相同），
 * 删除订阅后仍可播放；多源同名频道以「首条 URL + channelName」区分。
 */
@Serializable
data class IptvFavoriteEntry(
    val name: String = "",
    val channelName: String = "",
    val tvgId: String = "",
    /** 与 [Iptv.logoUrl] 一致，收藏时快照 */
    val logoUrl: String = "",
    val urlList: List<String> = emptyList(),
    /** 来源直播源 key（用于按源区分精选/多源合并）；历史数据可能为空。 */
    val sourceKey: String = "",
    /** 多行 `Name: Value`，与 [com.vesaa.mytv.ui.utils.SP.iptvSourceRequestHeaders] 格式一致 */
    val playbackRequestHeaders: String = "",
) {
    /** 与 [fromIptv] 无头时的 stableKey 一致，用于与当前列表中的 [Iptv] 比对 */
    fun stableKey(): String = stableKeyFrom(urlList, channelName)

    fun toIptv(): Iptv = Iptv(
        name = name,
        channelName = channelName,
        tvgId = tvgId,
        logoUrl = logoUrl,
        urlList = urlList,
    )

    companion object {
        fun stableKeyFrom(urlList: List<String>, channelName: String): String =
            "${urlList.firstOrNull().orEmpty()}\u0001$channelName"

        fun fromIptv(
            iptv: Iptv,
            playbackRequestHeaders: String,
            sourceKey: String = "",
        ): IptvFavoriteEntry =
            IptvFavoriteEntry(
                name = iptv.name,
                channelName = iptv.channelName,
                tvgId = iptv.tvgId,
                logoUrl = iptv.logoUrl.trim(),
                urlList = iptv.urlList,
                sourceKey = sourceKey.trim(),
                playbackRequestHeaders = playbackRequestHeaders.trim(),
            )
    }
}
