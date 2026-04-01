package com.vesaa.mytv.ui.screens.leanback.settings.components

import android.content.pm.PackageInfo
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import kotlinx.coroutines.launch
import com.vesaa.mytv.ui.screens.leanback.settings.LeanbackSettingsViewModel
import com.vesaa.mytv.ui.screens.leanback.toast.LeanbackToastState
import com.vesaa.mytv.ui.screens.leanback.update.LeanBackUpdateViewModel
import com.vesaa.mytv.ui.theme.LeanbackTheme
import com.vesaa.mytv.ui.utils.SP

@Composable
fun LeanbackSettingsCategoryApp(
    modifier: Modifier = Modifier,
    settingsViewModel: LeanbackSettingsViewModel = viewModel(),
    updateViewModel: LeanBackUpdateViewModel = viewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val packageInfo: PackageInfo = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val localVer = packageInfo.versionName ?: "0.0.0"

    TvLazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
    ) {
        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "开机自启",
                supportingContent = "请确保当前设备支持该功能",
                trailingContent = {
                    Switch(checked = settingsViewModel.appBootLaunch, onCheckedChange = null)
                },
                onSelected = {
                    settingsViewModel.appBootLaunch = !settingsViewModel.appBootLaunch
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "显示模式",
                supportingContent = "短按切换应用显示模式",
                trailingContent = when (settingsViewModel.appDeviceDisplayType) {
                    SP.AppDeviceDisplayType.LEANBACK -> "TV"
                    SP.AppDeviceDisplayType.PAD -> "Pad"
                    SP.AppDeviceDisplayType.MOBILE -> "手机"
                },
                onSelected = {
                    Toast.makeText(context, "暂未开放", Toast.LENGTH_SHORT).show()
                },
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "应用更新",
                supportingContent = when {
                    updateViewModel.isChecking -> "正在连接 GitHub Releases…"
                    updateViewModel.hasRetrievedRemoteVersion -> {
                        "当前安装 v$localVer；上游最新 v${updateViewModel.latestRelease.version}"
                    }
                    else -> "短按从 GitHub 检查新版本（不再在启动时自动检查）"
                },
                trailingContent = when {
                    updateViewModel.isChecking -> "…"
                    updateViewModel.hasRetrievedRemoteVersion && updateViewModel.isUpdateAvailable -> "可更新"
                    updateViewModel.hasRetrievedRemoteVersion -> "已最新"
                    else -> "检查更新"
                },
                onSelected = {
                    coroutineScope.launch {
                        val ok = updateViewModel.checkUpdate(localVer)
                        if (!ok) {
                            LeanbackToastState.I.showToast(
                                updateViewModel.lastCheckError ?: "检查失败",
                            )
                            return@launch
                        }
                        if (updateViewModel.isUpdateAvailable) {
                            LeanbackToastState.I.showToast(
                                "发现新版本：v${updateViewModel.latestRelease.version}",
                            )
                            updateViewModel.showDialog = true
                        } else {
                            LeanbackToastState.I.showToast("当前已是最新版本")
                        }
                    }
                },
            )
        }
    }
}

@Preview
@Composable
private fun LeanbackSettingsCategoryAppPreview() {
    SP.init(LocalContext.current)
    LeanbackTheme {
        LeanbackSettingsCategoryApp(
            modifier = Modifier.padding(20.dp),
            settingsViewModel = LeanbackSettingsViewModel(),
        )
    }
}
