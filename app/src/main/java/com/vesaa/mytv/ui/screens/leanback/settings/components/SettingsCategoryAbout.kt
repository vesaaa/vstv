package com.vesaa.mytv.ui.screens.leanback.settings.components

import android.content.Context
import android.content.pm.PackageInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import com.vesaa.mytv.data.utils.Constants
import com.vesaa.mytv.ui.theme.LeanbackTheme
import com.vesaa.mytv.ui.utils.HttpServer

@Composable
fun LeanbackSettingsCategoryAbout(
    modifier: Modifier = Modifier,
    packageInfo: PackageInfo = rememberPackageInfo(),
) {
    var serverUrl by remember { mutableStateOf(HttpServer.serverUrl()) }

    TvLazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 10.dp),
    ) {
        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "应用名称",
                trailingContent = Constants.APP_TITLE,
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "应用版本",
                trailingContent = packageInfo.versionName ?: "0.0.0",
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "设置页面地址",
                supportingContent = "服务监听所有网卡的 1616 端口；此处与扫码仅影响展示的 IP。若手机打不开，请确认与电视同一局域网、关闭访客/AP 隔离，或在电视自带浏览器试 http://127.0.0.1:1616",
                trailingContent = serverUrl,
            )
        }

        item {
            LeanbackSettingsCategoryListItem(
                headlineContent = "切换扫码用的本机 IP",
                supportingContent = "在「自动」与检测到的 IPv4 间循环，便于二维码对准真实局域网地址",
                trailingContent = "短按切换",
                onSelected = {
                    serverUrl = HttpServer.cycleHttpServerAdvertiseIp()
                },
            )
        }
    }
}

@Composable
private fun rememberPackageInfo(context: Context = LocalContext.current): PackageInfo =
    context.packageManager.getPackageInfo(context.packageName, 0)

@Preview
@Composable
private fun LeanbackSettingsAboutPreview() {
    LeanbackTheme {
        LeanbackSettingsCategoryAbout(
            packageInfo = PackageInfo().apply {
                versionName = "0.0.16"
            }
        )
    }
}
