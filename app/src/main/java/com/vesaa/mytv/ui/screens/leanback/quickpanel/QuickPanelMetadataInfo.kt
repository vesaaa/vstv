package com.vesaa.mytv.ui.screens.leanback.quickpanel

import com.vesaa.mytv.ui.screens.leanback.video.player.LeanbackVideoPlayer
import java.util.Locale

/** 底部按钮副标题：分辨率 + 视频格式（简写） */
internal fun formatQuickPanelVideoMenuSubtitle(m: LeanbackVideoPlayer.Metadata): String {
    val res = if (m.videoWidth > 0 && m.videoHeight > 0) {
        "${m.videoWidth}×${m.videoHeight}"
    } else {
        "分辨率未知"
    }
    return "$res · ${shortVideoCodecLabel(m.videoMimeType)}"
}

/** 底部按钮副标题：声道说明 + 音频格式（简写） */
internal fun formatQuickPanelAudioMenuSubtitle(m: LeanbackVideoPlayer.Metadata): String {
    val ch = when {
        m.audioChannels <= 0 -> "声道未知"
        m.audioChannels == 1 -> "单声道"
        m.audioChannels == 2 -> "立体声"
        else -> "${m.audioChannels} 声道"
    }
    return "$ch · ${shortAudioCodecLabel(m.audioMimeType)}"
}

internal fun formatQuickPanelVideoDetailBody(m: LeanbackVideoPlayer.Metadata): String = buildString {
    appendLine(formatQuickPanelVideoLine(m))
    if (m.videoBitrate > 0) {
        appendLine("视频码率约 ${m.videoBitrate / 1024} kbps")
    } else {
        appendLine("视频码率：流中未提供")
    }
    if (m.videoDecoder.isNotBlank()) {
        appendLine("视频解码：${m.videoDecoder}")
    }
    if (m.videoColor.isNotBlank()) {
        appendLine("色彩：${m.videoColor}")
    }
}.trimEnd()

internal fun formatQuickPanelAudioDetailBody(m: LeanbackVideoPlayer.Metadata): String = buildString {
    appendLine(formatQuickPanelAudioLine(m))
    if (m.audioDecoder.isNotBlank()) {
        appendLine("音频解码：${m.audioDecoder}")
    }
}.trimEnd()

internal fun formatQuickPanelStreamDetailBody(m: LeanbackVideoPlayer.Metadata): String = buildString {
    appendLine(formatQuickPanelStreamExtraLine(m))
    appendLine()
    appendLine("视频：${formatQuickPanelVideoMenuSubtitle(m)}")
    appendLine("音频：${formatQuickPanelAudioMenuSubtitle(m)}")
}.trimEnd()

private fun shortVideoCodecLabel(mime: String): String {
    if (mime.isBlank()) return "格式未知"
    val m = mime.lowercase(Locale.ROOT)
    return when {
        "avc" in m || "h264" in m -> "H.264/AVC"
        "hevc" in m || "h265" in m -> "H.265/HEVC"
        "mpeg2" in m || "mp2v" in m -> "MPEG-2"
        "vp9" in m -> "VP9"
        "vp8" in m -> "VP8"
        "av01" in m || "av1" in m -> "AV1"
        else -> mime.removePrefix("video/").take(28).ifBlank { mime }
    }
}

private fun shortAudioCodecLabel(mime: String): String {
    if (mime.isBlank()) return "格式未知"
    val m = mime.lowercase(Locale.ROOT)
    return when {
        "aac" in m || "mp4a" in m -> "AAC"
        "ac-3" in m || "ac3" in m -> "AC-3"
        "eac3" in m || "ec-3" in m -> "E-AC-3"
        "opus" in m -> "Opus"
        "vorbis" in m -> "Vorbis"
        "mp3" in m || "mpeg" in m && "audio" in m -> "MP3"
        else -> mime.removePrefix("audio/").take(28).ifBlank { mime }
    }
}

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
