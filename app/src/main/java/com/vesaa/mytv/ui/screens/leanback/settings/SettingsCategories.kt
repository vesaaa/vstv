package com.vesaa.mytv.ui.screens.leanback.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

/** 顺序即设置主页网格展示顺序；[ABOUT] 固定最后一项。 */
enum class LeanbackSettingsCategories(
    val icon: ImageVector,
    val title: String
) {
    APP(Icons.Default.Settings, "应用"),
    IPTV(Icons.Default.LiveTv, "直播源"),
    EPG(Icons.Default.Menu, "节目单"),
    UI(Icons.Default.DisplaySettings, "界面"),
    FAVORITE(Icons.Default.Star, "精选设置"),
    MERGE(Icons.Default.Menu, "多源合并"),
    VIDEO_PLAYER(Icons.Default.SmartDisplay, "播放器"),
    NETWORK(Icons.Default.Wifi, "网络"),
    LOG(Icons.Default.FormatListNumbered, "日志"),
    ABOUT(Icons.Default.Info, "关于"),
}
