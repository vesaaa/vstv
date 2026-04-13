package com.vesaa.mytv.ui.screens.leanback.video

import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
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
        key(useTextureView) {
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
                        SurfaceView(context).apply {
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
        }

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