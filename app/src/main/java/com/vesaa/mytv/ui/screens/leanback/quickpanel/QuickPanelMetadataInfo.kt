package com.vesaa.mytv.ui.screens.leanback.quickpanel

import com.vesaa.mytv.ui.screens.leanback.video.player.LeanbackVideoPlayer
import java.util.Locale

/** 底部快捷菜单里「视频 / 音频 / 码流」Toast 文案（数据来自 ExoPlayer Format 回调）。 */
internal fun formatQuickPanelVideoLine(m: LeanbackVideoPlayer.Metadata): String {
    val res = if (m.videoWidth > 0 && m.videoHeight > 0) {
        "${m.videoWidth}×${m.videoHeight}"
    } else {
        "未知"
    }
    val fps = when {
        m.videoFrameRate > 0.05f ->
            String.format(Locale.getDefault(), "%.2f fps", m.videoFrameRate)
        else -> "帧率未知（部分直播流不提供）"
    }
    val mime = m.videoMimeType.ifBlank { "视频编码未知" }
    return "分辨率 $res · $fps\n编码 $mime"
}

internal fun formatQuickPanelAudioLine(m: LeanbackVideoPlayer.Metadata): String {
    val mime = m.audioMimeType.ifBlank { "音频编码未知" }
    val sr = if (m.audioSampleRate > 0) "${m.audioSampleRate} Hz" else "采样率未知"
    val ch = when {
        m.audioChannels <= 0 -> null
        m.audioChannels == 1 -> "单声道"
        m.audioChannels == 2 -> "双声道"
        else -> "${m.audioChannels} 声道"
    }
    return buildString {
        append(mime)
        append(" · ")
        append(sr)
        if (ch != null) {
            append('\n')
            append(ch)
        }
    }
}

/** 解码器、码率、色彩等（直播流常缺比特率字段） */
internal fun formatQuickPanelStreamExtraLine(m: LeanbackVideoPlayer.Metadata): String {
    val lines = mutableListOf<String>()
    if (m.videoBitrate > 0) {
        lines += "视频码率约 ${m.videoBitrate / 1024} kbps"
    } else {
        lines += "视频码率：流中未提供"
    }
    lines += if (m.videoDecoder.isNotBlank()) {
        "视频解码 ${m.videoDecoder}"
    } else {
        "视频解码：未知"
    }
    lines += if (m.audioDecoder.isNotBlank()) {
        "音频解码 ${m.audioDecoder}"
    } else {
        "音频解码：未知"
    }
    if (m.videoColor.isNotBlank()) {
        lines += "色彩 ${m.videoColor}"
    }
    return lines.joinToString("\n")
}
