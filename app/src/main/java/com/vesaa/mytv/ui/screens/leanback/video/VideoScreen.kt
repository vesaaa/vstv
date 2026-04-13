package com.vesaa.mytv.ui.screens.leanback.video

import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.vesaa.mytv.ui.rememberLeanbackChildPadding
import com.vesaa.mytv.ui.screens.leanback.video.components.LeanbackVideoPlayerMetadata

@Composable
fun LeanbackVideoScreen(
    modifier: Modifier = Modifier,
    state: LeanbackVideoPlayerState = rememberLeanbackVideoPlayerState(),
    showMetadataProvider: () -> Boolean = { false },
    useTextureView: Boolean = false,
) {
    val context = LocalContext.current
    val childPadding = rememberLeanbackChildPadding()

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .align(Alignment.Center)
                .aspectRatio(state.aspectRatio),
            factory = {
                if (useTextureView) {
                    TextureView(context).apply {
                        isFocusable = false
                        isFocusableInTouchMode = false
                    }
                } else {
                    // 单屏继续使用 SurfaceView，保持现有性能与兼容性
                    SurfaceView(context).apply {
                        // 避免 SurfaceView 抢走窗口焦点，导致外层 Compose 收不到方向键换台
                        isFocusable = false
                        isFocusableInTouchMode = false
                    }
                }
            },
            update = { videoView ->
                videoView.isFocusable = false
                videoView.isFocusableInTouchMode = false
                when (videoView) {
                    is SurfaceView -> state.setVideoSurfaceView(videoView)
                    is TextureView -> state.setVideoTextureView(videoView)
                }
            },
        )

        LeanbackVideoPlayerErrorScreen(
            errorProvider = { state.error },
        )

        if (showMetadataProvider()) {
            LeanbackVideoPlayerMetadata(
                modifier = Modifier.padding(start = childPadding.start, top = childPadding.top),
                metadata = state.metadata,
            )
        }
    }
}