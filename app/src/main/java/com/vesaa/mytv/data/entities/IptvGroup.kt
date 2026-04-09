package com.vesaa.mytv.data.entities

/**
 * 直播源分组
 */
data class IptvGroup(
    /**
     * 分组名称
     */
    val name: String = "",

    /**
     * 直播源列表
     */
    val iptvList: IptvList = IptvList(),
) {
    companion object {
        /** 经典选台左侧「精选频道」分组名，勿与 M3U `group-title` 重名 */
        const val FAVORITE_GROUP_NAME = "精选频道"
        /** 多源合并后的全局分组：固定放在普通分组末尾（精选仍在首部）。 */
        const val EXPANDED_GROUP_NAME = "扩展频道"
    }
}