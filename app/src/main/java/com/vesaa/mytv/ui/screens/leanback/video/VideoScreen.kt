package com.vesaa.mytv.ui.screens.leanback.video

import android.graphics.Color as AndroidColor
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import coil.compose.AsyncImage
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

        if (state.error == null && state.metadata.imageSequenceModeHint && state.metadata.imageSequenceImageUrl.isNotBlank()) {
            AsyncImage(
                model = state.metadata.imageSequenceImageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        if (state.error == null && !state.metadata.imageSequenceModeHint && (state.holdBlackScreen || state.metadata.audioOnlyModeHint)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            )
        }

        if (state.error == null && !state.metadata.imageSequenceModeHint && state.metadata.audioOnlyModeHint) {
            AudioOnlyVisualizer(
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // 在组合作用域读 subtitleCues，确保变化能触发重组并刷新 SubtitleView。
        val currentCues = state.subtitleCues.toList()
        if (state.error == null) {
            AndroidView(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxSize(),
                factory = {
                    SubtitleView(context).apply {
                        setApplyEmbeddedStyles(true)
                        setApplyEmbeddedFontSizes(true)
                        setBottomPaddingFraction(0.08f)
                        setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.05f)
                        setStyle(
                            CaptionStyleCompat(
                                AndroidColor.WHITE,
                                AndroidColor.TRANSPARENT,
                                AndroidColor.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                                AndroidColor.BLACK,
                                null,
                            ),
                        )
                    }
                },
                update = { subtitleView ->
                    subtitleView.setCues(currentCues)
                },
            )
        }

        if (showMetadataProvider()) {
            LeanbackVideoPlayerMetadata(
                modifier = Modifier.padding(start = childPadding.start, top = childPadding.top),
                metadata = state.metadata,
            )
        }
    }
}

@Composable
private fun AudioOnlyVisualizer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "audioOnly")
    val pulse = transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val ripple = transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ripple",
    )

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .size(180.dp),
        ) {
            val base = size.minDimension * 0.22f
            val r1 = base * (1f + ripple.value * 1.1f)
            val r2 = base * (1f + ((ripple.value + 0.35f) % 1f) * 1.3f)
            drawCircle(
                color = Color.White.copy(alpha = 0.28f * (1f - ripple.value)),
                radius = r1,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.20f * (1f - ((ripple.value + 0.35f) % 1f))),
                radius = r2,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.18f + 0.22f * pulse.value),
                radius = base * (1f + 0.08f * pulse.value),
            )
        }
        Text(
            text = "\u266B",
            modifier = Modifier.align(Alignment.Center),
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}