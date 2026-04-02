package com.vesaa.mytv.ui.screens.leanback.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import com.vesaa.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import com.vesaa.mytv.ui.screens.leanback.toast.LeanbackToastState
import com.vesaa.mytv.ui.theme.LeanbackTheme

@Composable
fun LeanbackSettingsCategoryFavorite(
    modifier: Modifier = Modifier,
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
) {
    TvLazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
    ) {
        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "收藏启用",
                trailingContent = {
                    Switch(
                        checked = settingsViewModel.iptvChannelFavoriteEnable,
                        onCheckedChange = null
                    )
                },
                onSelected = {
                    settingsViewModel.iptvChannelFavoriteEnable =
                        !settingsViewModel.iptvChannelFavoriteEnable
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "只看收藏",
                supportingContent = "启用后，只会显示收藏夹和里面的频道，其他频道都会隐藏",
                trailingContent = {
                    Switch(
                        checked = settingsViewModel.iptvChannelFavoritesOnlyMode,
                        onCheckedChange = null,
                        enabled = settingsViewModel.iptvChannelFavoriteEnable,
                    )
                },
                onSelected = {
                    if (!settingsViewModel.iptvChannelFavoriteEnable) return@LeanbackSettingsCategoryListItem
                    val next = !settingsViewModel.iptvChannelFavoritesOnlyMode
                    if (next && settingsViewModel.iptvChannelFavoriteEntries.isEmpty()) {
                        LeanbackToastState.I.showToast("请先收藏至少一个频道")
                        return@LeanbackSettingsCategoryListItem
                    }
                    settingsViewModel.iptvChannelFavoritesOnlyMode = next
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "当前已收藏",
                supportingContent = "已保存播放地址与请求头，删除订阅后仍可播放",
                trailingContent = "${settingsViewModel.iptvChannelFavoriteEntries.size}个频道",
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "清空全部收藏",
                supportingContent = "短按立即清空全部收藏",
                onSelected = {
                    settingsViewModel.iptvChannelFavoriteEntries = emptyList()
                    settingsViewModel.iptvChannelFavoriteListVisible = false
                }
            )
        }
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryFavoritePreview() {
    LeanbackTheme {
        LeanbackSettingsCategoryFavorite(modifier = Modifier.padding(20.dp))
    }
}
