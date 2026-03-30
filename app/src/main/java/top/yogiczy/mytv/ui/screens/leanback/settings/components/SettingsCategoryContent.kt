package top.yogiczy.mytv.ui.screens.leanback.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsCategories
import top.yogiczy.mytv.ui.screens.leanback.settings.LeanbackSettingsMenuItem
import top.yogiczy.mytv.utils.Logger

@Composable
fun LeanbackSettingsCategoryContent(
    modifier: Modifier = Modifier,
    focusedMenuItemProvider: () -> LeanbackSettingsMenuItem = { LeanbackSettingsMenuItem.ReturnLive },
) {
    val item = focusedMenuItemProvider()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (item) {
            LeanbackSettingsMenuItem.ReturnLive -> {
                Text(text = "返回直播", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "按遥控「确认」键关闭设置并回到全屏播放；也可在左侧第一个方块上按确认。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is LeanbackSettingsMenuItem.Category -> {
                val focusedCategory = item.value
                Text(text = focusedCategory.title, style = MaterialTheme.typography.headlineSmall)

                when (focusedCategory) {
                    LeanbackSettingsCategories.ABOUT -> LeanbackSettingsCategoryAbout()
                    LeanbackSettingsCategories.APP -> LeanbackSettingsCategoryApp()
                    LeanbackSettingsCategories.IPTV -> LeanbackSettingsCategoryIptv()
                    LeanbackSettingsCategories.EPG -> LeanbackSettingsCategoryEpg()
                    LeanbackSettingsCategories.UI -> LeanbackSettingsCategoryUI()
                    LeanbackSettingsCategories.FAVORITE -> LeanbackSettingsCategoryFavorite()
                    LeanbackSettingsCategories.VIDEO_PLAYER -> LeanbackSettingsCategoryVideoPlayer()
                    LeanbackSettingsCategories.NETWORK -> LeanbackSettingsCategoryNetwork()
                    LeanbackSettingsCategories.LOG -> LeanbackSettingsCategoryLog(
                        history = Logger.history,
                    )
                    LeanbackSettingsCategories.MORE -> LeanbackSettingsCategoryMore()
                }
            }
        }
    }
}
