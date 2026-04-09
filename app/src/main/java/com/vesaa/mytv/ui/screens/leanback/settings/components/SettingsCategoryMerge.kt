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
fun LeanbackSettingsCategoryMerge(
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
                headlineContent = "启用扩展频道",
                supportingContent = "可以将不同直播源内精选频道的节目，固化到扩展频道，实现多个直播源合并的效果。",
                trailingContent = {
                    Switch(
                        checked = settingsViewModel.iptvExpandedChannelEnable,
                        onCheckedChange = null,
                    )
                },
                onSelected = {
                    settingsViewModel.iptvExpandedChannelEnable = !settingsViewModel.iptvExpandedChannelEnable
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "更新扩展频道",
                supportingContent = "用当前精选覆盖更新当前直播源对应的扩展频道条目；不影响其他直播源条目。",
                trailingContent = "${settingsViewModel.iptvExpandedChannelSourceCount}个源 / ${settingsViewModel.iptvExpandedChannelEntries.size}个频道",
                onSelected = {
                    settingsViewModel.updateExpandedChannelsFromFavoritesOfCurrentSource()
                    LeanbackToastState.I.showToast("已更新扩展频道")
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "清空扩展频道",
                supportingContent = "短按立即删除扩展频道内所有直播源条目",
                onSelected = {
                    settingsViewModel.clearExpandedChannels()
                    LeanbackToastState.I.showToast("已清空扩展频道")
                },
            )
        }
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryMergePreview() {
    LeanbackTheme {
        LeanbackSettingsCategoryMerge(modifier = Modifier.padding(20.dp))
    }
}
