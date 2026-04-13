package com.vesaa.mytv.ui.screens.leanback.quickpanel

import com.vesaa.mytv.ui.screens.leanback.video.player.LeanbackVideoPlayer
import java.util.Locale

/**
 * 与 [com.vesaa.mytv.ui.screens.leanback.panel.components.LeanbackPanelPlayerInfo] 内
 * PanelPlayerInfoFps 一致：流里声明的帧率优先，缺失时用渲染侧估算帧率。
 */
internal fun effectiveVideoFpsForDisplay(m: LeanbackVideoPlayer.Metadata): Float {
    val declared = m.videoFrameRate
    val rendered = m.videoRenderedFps
    return when {
        declared > 0.05f -> declared
        rendered > 0.05f -> rendered
        else -> 0f
    }
}

private fun deviceRefreshRateLabel(activeRefreshRate: Float, maxSupportedRefreshRate: Float): String {
    if (activeRefreshRate <= 1f) return ""
    val activeFpsText = activeRefreshRate.toInt().toString()
    val maxFpsText = if (maxSupportedRefreshRate > 1f) maxSupportedRefreshRate.toInt().toString() else ""
    return if (maxFpsText.isNotEmpty() && maxSupportedRefreshRate - activeRefreshRate > 1f) {
        "$activeFpsText($maxFpsText)"
    } else {
        activeFpsText
    }
}

/** 与底部栏「帧率：A/B FPS」同一套数字（单行展示用）。 */
internal fun formatVideoFpsForPanelStyle(
    m: LeanbackVideoPlayer.Metadata,
    displayRefreshHz: Float = 0f,
    maxSupportedRefreshHz: Float = 0f,
): String {
    val effective = effectiveVideoFpsForDisplay(m)
    val videoFpsText = if (effective > 0.05f) effective.toInt().toString() else "N/A"
    val deviceFpsText = if (displayRefreshHz > 1f) {
        deviceRefreshRateLabel(displayRefreshHz, maxSupportedRefreshHz)
    } else {
        ""
    }
    return if (deviceFpsText.isNotEmpty()) {
        "$videoFpsText/$deviceFpsText FPS"
    } else if (effective > 0.05f) {
        String.format(Locale.getDefault(), "%.2f FPS", effective)
    } else {
        "N/A（部分直播流不提供）"
    }
}

/**
 * 顶部状态徽标分辨率档位：
 * <=720 -> 720p，<=1080 -> 1080p，<=1440 -> 2K，<=4320 -> 4K，>4320 -> 8K。
 */
internal fun formatQuickPanelResolutionBadge(m: LeanbackVideoPlayer.Metadata): String {
    val width = m.videoWidth
    val height = m.videoHeight
    val shortSide = when {
        width > 0 && height > 0 -> minOf(width, height)
        height > 0 -> height
        width > 0 -> width
        else -> return ""
    }
    return when {
        shortSide <= 720 -> "720p"
        shortSide <= 1080 -> "1080p"
        shortSide <= 1440 -> "2K"
        shortSide <= 4320 -> "4K"
        else -> "8K"
    }
}

/** 底部按钮副标题：分辨率 + 视频格式（简写） */
internal fun formatQuickPanelVideoMenuSubtitle(m: LeanbackVideoPlayer.Metadata): String {
    val res = if (m.videoWidth > 0 && m.videoHeight > 0) {
        "${m.videoWidth}×${m.videoHeight}"
    } else {
        "分辨率未知"
    }
    val dr = videoDynamicRangeShortTag(m)
    val codec = shortVideoCodecLabel(m.videoMimeType)
    return if (dr != null) "$res · $codec · $dr" else "$res · $codec"
}

/** 底部按钮单行：分辨率,AVC（与旧版 Toast 展示习惯一致，尽量短） */
internal fun formatQuickPanelVideoButtonLabel(m: LeanbackVideoPlayer.Metadata): String {
    val res = if (m.videoWidth > 0 && m.videoHeight > 0) {
        "${m.videoWidth}×${m.videoHeight}"
    } else {
        "—"
    }
    return "$res,${videoCodecButtonAbbrev(m.videoMimeType)}"
}

/** 底部按钮副标题：声道说明 + 音频格式（简写） */
internal fun formatQuickPanelAudioMenuSubtitle(m: LeanbackVideoPlayer.Metadata): String {
    val ch = when {
        m.audioChannels <= 0 -> "声道未知"
        m.audioChannels == 1 -> "单声道"
        m.audioChannels == 2 -> "立体声"
        else -> "${m.audioChannels} 声道"
    }
    val codec = shortAudioCodecLabel(m.audioMimeType)
    val dolby = audioDolbyShortTag(m)
    return if (dolby != null) "$ch · $codec · $dolby" else "$ch · $codec"
}

/** 立体声,mp4a-latm 形式：声道说明 + 原始 mime 尾缀（去掉 audio/） */
internal fun formatQuickPanelAudioButtonLabel(m: LeanbackVideoPlayer.Metadata): String {
    val ch = when {
        m.audioChannels <= 0 -> "?"
        m.audioChannels == 1 -> "单声道"
        m.audioChannels == 2 -> "立体声"
        else -> "${m.audioChannels}声道"
    }
    val mime = m.audioMimeType.trim()
    val tail = if (mime.isNotEmpty()) {
        mime.removePrefix("audio/").substringBefore(";").trim()
            .ifBlank { shortAudioCodecLabel(mime) }
    } else {
        shortAudioCodecLabel(mime)
    }
    return "$ch,$tail"
}

internal fun formatQuickPanelVideoDetailBody(
    m: LeanbackVideoPlayer.Metadata,
    displayRefreshHz: Float = 0f,
    maxSupportedRefreshHz: Float = 0f,
): String = buildString {
    appendLine(formatQuickPanelVideoLine(m, displayRefreshHz, maxSupportedRefreshHz))
    val dr = videoDynamicRangeLabel(m)
    if (dr != null) appendLine("动态范围：$dr")
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
    val dolby = audioDolbyLabel(m)
    if (dolby != null) appendLine("杜比：$dolby")
    if (m.audioDecoder.isNotBlank()) {
        appendLine("音频解码：${m.audioDecoder}")
    }
}.trimEnd()

internal fun formatQuickPanelStreamDetailBody(
    m: LeanbackVideoPlayer.Metadata,
    playbackUrl: String = "",
): String = buildString {
    appendLine(formatQuickPanelStreamExtraLine(m))
    appendLine()
    appendLine("视频：${formatQuickPanelVideoMenuSubtitle(m)}")
    appendLine("音频：${formatQuickPanelAudioMenuSubtitle(m)}")
    val u = playbackUrl.trim()
    if (u.isNotEmpty()) {
        appendLine()
        appendLine("播放地址")
        appendLine(u)
    }
}.trimEnd()

/** 按钮用短后缀，如 H.264/AVC → AVC */
private fun videoCodecButtonAbbrev(mime: String): String {
    if (mime.isBlank()) return "?"
    val full = shortVideoCodecLabel(mime)
    val afterSlash = full.substringAfterLast('/', "")
    return if (afterSlash.isNotEmpty() && afterSlash != full) afterSlash else full.take(14)
}

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
internal fun formatQuickPanelVideoLine(
    m: LeanbackVideoPlayer.Metadata,
    displayRefreshHz: Float = 0f,
    maxSupportedRefreshHz: Float = 0f,
): String {
    val res = if (m.videoWidth > 0 && m.videoHeight > 0) {
        "${m.videoWidth}×${m.videoHeight}"
    } else {
        "未知"
    }
    val effective = effectiveVideoFpsForDisplay(m)
    val fps = when {
        effective > 0.05f && displayRefreshHz > 1f -> {
            val streamInt = effective.toInt()
            val dev = deviceRefreshRateLabel(displayRefreshHz, maxSupportedRefreshHz)
            "$streamInt/$dev fps"
        }
        effective > 0.05f ->
            String.format(Locale.getDefault(), "%.2f fps", effective)
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
    videoDynamicRangeLabel(m)?.let { lines += "动态范围 $it" }
    audioDolbyLabel(m)?.let { lines += "杜比 $it" }
    return lines.joinToString("\n")
}

private fun videoDynamicRangeLabel(m: LeanbackVideoPlayer.Metadata): String? {
    val src = "${m.videoMimeType} ${m.videoCodecs} ${m.videoColor}".lowercase(Locale.ROOT)
    return when {
        "dolby" in src || "dovi" in src || "dvhe" in src || "dvh1" in src -> "Dolby Vision (HDR)"
        "hlg" in src -> "HLG (HDR)"
        "st2084" in src || "pq" in src || "hdr10" in src -> "HDR10/PQ"
        m.videoColor.isNotBlank() -> "SDR"
        else -> null
    }
}

private fun audioDolbyLabel(m: LeanbackVideoPlayer.Metadata): String? {
    val src = "${m.audioMimeType} ${m.audioCodecs}".lowercase(Locale.ROOT)
    return when {
        "joc" in src || "atmos" in src || "ec+3" in src -> "Dolby Atmos"
        "eac3" in src || "ec-3" in src -> "Dolby Digital Plus (E-AC-3)"
        "ac-3" in src || "ac3" in src -> "Dolby Digital (AC-3)"
        else -> null
    }
}

private fun videoDynamicRangeShortTag(m: LeanbackVideoPlayer.Metadata): String? {
    val src = "${m.videoMimeType} ${m.videoCodecs} ${m.videoColor}".lowercase(Locale.ROOT)
    return when {
        "dolby" in src || "dovi" in src || "dvhe" in src || "dvh1" in src -> "DV"
        "hlg" in src -> "HLG"
        "st2084" in src || "pq" in src || "hdr10" in src -> "HDR"
        m.videoColor.isNotBlank() -> "SDR"
        else -> null
    }
}

private fun audioDolbyShortTag(m: LeanbackVideoPlayer.Metadata): String? {
    val src = "${m.audioMimeType} ${m.audioCodecs}".lowercase(Locale.ROOT)
    return when {
        "joc" in src || "atmos" in src || "ec+3" in src -> "Atmos"
        "eac3" in src || "ec-3" in src -> "DD+"
        "ac-3" in src || "ac3" in src -> "DD"
        else -> null
    }
}
