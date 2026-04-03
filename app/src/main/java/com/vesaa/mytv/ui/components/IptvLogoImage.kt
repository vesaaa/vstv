package com.vesaa.mytv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
 * 使用 Coil 做内存/磁盘缓存与采样；与 IPTV 拉流请求头无绑定（多数 CDN 台标为直连图）。
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
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp)),
        contentScale = ContentScale.Fit,
        loading = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            )
        },
        error = {
            Box(Modifier.fillMaxSize())
        },
    )
}
