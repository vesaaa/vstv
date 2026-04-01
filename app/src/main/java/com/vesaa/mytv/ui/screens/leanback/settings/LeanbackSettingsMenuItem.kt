package com.vesaa.mytv.ui.screens.leanback.settings

/**
 * 设置侧栏菜单项：首项为返回直播，其余为各设置分类。
 */
sealed class LeanbackSettingsMenuItem {
    data object ReturnLive : LeanbackSettingsMenuItem()

    data class Category(val value: LeanbackSettingsCategories) : LeanbackSettingsMenuItem()

    companion object {
        fun all(): List<LeanbackSettingsMenuItem> = buildList {
            add(ReturnLive)
            addAll(LeanbackSettingsCategories.entries.map { Category(it) })
        }
    }
}
