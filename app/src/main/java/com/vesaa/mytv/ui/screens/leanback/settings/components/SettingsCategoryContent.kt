package com.vesaa.mytv.ui.screens.leanback.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vesaa.mytv.ui.screens.leanback.settings.LeanbackSettingsCategories
import com.vesaa.mytv.utils.Logger

/**
 * 某一设置分类下的具体操作列表（用于弹窗内展示，不含外层标题）。
 */
@Composable
fun LeanbackSettingsCategoryDetail(
    category: LeanbackSettingsCategories,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (category) {
            LeanbackSettingsCategories.ABOUT -> LeanbackSettingsCategoryAbout(Modifier.fillMaxSize())
            LeanbackSettingsCategories.APP -> LeanbackSettingsCategoryApp(Modifier.fillMaxSize())
            LeanbackSettingsCategories.IPTV -> LeanbackSettingsCategoryIptv(Modifier.fillMaxSize())
            LeanbackSettingsCategories.EPG -> LeanbackSettingsCategoryEpg(Modifier.fillMaxSize())
            LeanbackSettingsCategories.UI -> LeanbackSettingsCategoryUI(Modifier.fillMaxSize())
            LeanbackSettingsCategories.FAVORITE -> LeanbackSettingsCategoryFavorite(Modifier.fillMaxSize())
            LeanbackSettingsCategories.VIDEO_PLAYER -> LeanbackSettingsCategoryVideoPlayer(Modifier.fillMaxSize())
            LeanbackSettingsCategories.NETWORK -> LeanbackSettingsCategoryNetwork(Modifier.fillMaxSize())
            LeanbackSettingsCategories.LOG -> LeanbackSettingsCategoryLog(
                modifier = Modifier.fillMaxSize(),
                history = Logger.history,
            )
        }
    }
}
