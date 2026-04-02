package com.vesaa.mytv.data

import com.vesaa.mytv.data.entities.IptvFavoriteEntry
import com.vesaa.mytv.data.entities.IptvGroupList
import com.vesaa.mytv.data.entities.IptvGroupList.Companion.iptvList
import com.vesaa.mytv.ui.utils.SP

/**
 * 将旧版「仅 channelName 集合」迁移为带 URL/请求头的条目；在拿到当前频道列表后调用一次。
 */
object IptvFavoriteMigration {
    @Volatile
    private var ran = false

    @Synchronized
    fun runOnceIfNeeded(iptvGroupList: IptvGroupList) {
        if (ran) return
        val legacyNames = SP.iptvChannelFavoriteList
        if (legacyNames.isEmpty()) {
            ran = true
            return
        }
        if (SP.loadFavoriteEntries().isNotEmpty()) {
            SP.iptvChannelFavoriteList = emptySet()
            ran = true
            return
        }
        val headers = SP.currentIptvSourceRequestHeadersSnapshot()
        val flat = iptvGroupList.iptvList
        val entries = legacyNames.mapNotNull { name ->
            flat.firstOrNull { it.channelName == name }
                ?.let { IptvFavoriteEntry.fromIptv(it, headers) }
        }.distinctBy { it.stableKey() }
        SP.saveFavoriteEntries(entries)
        SP.iptvChannelFavoriteList = emptySet()
        ran = true
    }
}
