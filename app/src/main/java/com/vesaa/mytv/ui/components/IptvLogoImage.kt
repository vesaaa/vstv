package com.vesaa.mytv.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

/**
 * 频道台标：依赖 M3U `tvg-logo` 写入 [com.vesaa.mytv.data.entities.Iptv.logoUrl]。
 * Coil **异步**解码与绘制；全局 [coil.Coil] 在 [com.vesaa.mytv.MyTVApplication] 中配置了内存 + 磁盘缓存（见 `coil_logo_disk`）。
 * 与 IPTV 拉流请求头无绑定（多数 CDN 台标为直连图）。
 */
@Composable
fun IptvLogoImage(
    logoUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    if (logoUrl.isBlank()) return
    val context = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(logoUrl)
            // 磁盘/内存命中时直接显示，避免占位灰块长时间可见
            .crossfade(false)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp)),
        contentScale = ContentScale.Fit,
        // 加载中不占色块（空区域）；失败则保持空白，避免误导为「无台标」的色块
        loading = { Box(Modifier.fillMaxSize()) },
        error = { Box(Modifier.fillMaxSize()) },
    )
}
