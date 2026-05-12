package com.vesaa.mytv.ui.screens.leanback.video.player

import android.content.Context
import android.os.Handler
import androidx.media3.common.text.Cue
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.vesaa.mytv.utils.SeiCaptionParser

/**
 * 扩展 [MediaCodecVideoRenderer]，在视频样本送入解码器之前拦截 H.264 原始数据，
 * 提取 SEI 中的 CEA-608 字幕并转换为 [Cue]。
 *
 * 原理：高通 AVC 硬解器不会透传 SEI caption 数据，因此通过
 * [onQueueInputBuffer] 在解码前从 Annex B 码流中手动提取。
 */
class SeiVideoRenderer(
    context: Context,
    mediaCodecSelector: MediaCodecSelector,
    allowedJoiningTimeMs: Long,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: VideoRendererEventListener,
    maxDroppedFramesToReport: Int,
    private val onSeiCuesReady: (List<Cue>) -> Unit
) : MediaCodecVideoRenderer(
    context,
    mediaCodecSelector,
    allowedJoiningTimeMs,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    maxDroppedFramesToReport
) {

    private var currentMime: String? = null
    private var seiSampleCounter = 0
    private var seiCueCounter = 0

    override fun onInputFormatChanged(format: androidx.media3.common.Format) {
        currentMime = format.sampleMimeType
        super.onInputFormatChanged(format)
    }

    override fun onQueueInputBuffer(inputBuffer: DecoderInputBuffer) {
        interceptSei(inputBuffer)
        super.onQueueInputBuffer(inputBuffer)
    }

    private fun interceptSei(inputBuffer: DecoderInputBuffer) {
        if (currentMime !in MIME_AVC_SET) return

        val data = inputBuffer.data ?: return
        if (data.remaining() == 0) return

        seiSampleCounter++
        val pairs = try {
            SeiCaptionParser.extractCcData(data)
        } catch (_: Exception) {
            return
        }

        if (pairs.isEmpty()) return

        val cues = buildCues(pairs)
        if (cues.isNotEmpty()) {
            seiCueCounter++
            onSeiCuesReady(cues)
        }
    }

    private fun buildCues(pairs: List<SeiCaptionParser.Cea608Pair>): List<Cue> {
        val sb = StringBuilder()
        val cues = mutableListOf<Cue>()

        for (pair in pairs) {
            val d1 = pair.ccData1 and 0x7F
            val d2 = pair.ccData2 and 0x7F

            // 跳过控制码
            if (d1 <= 0x0F && d2 <= 0x0F) continue

            // 基础 CEA-608 字符映射
            val c1 = cea608Char(d1)
            val c2 = cea608Char(d2)

            if (c1 != null) sb.append(c1)
            if (c2 != null) sb.append(c2)
        }

        val text = sb.toString().trim()
        if (text.isNotEmpty()) {
            cues.add(Cue.Builder().setText(text).build())
        }
        return cues
    }

    private fun cea608Char(code7bit: Int): Char? {
        if (code7bit < 0x20) return null // 控制码
        if (code7bit <= 0x7E) {
            return when (code7bit) {
                0x2A -> '\u00E1' // á
                0x5C -> '\u00E9' // é
                0x5E -> '\u00ED' // í
                0x5F -> '\u00F3' // ó
                0x60 -> '\u00FA' // ú
                0x7C -> '\u00F1' // ñ
                0x7D -> '\u00D1' // Ñ
                0x7E -> '\u266A' // ♪
                else -> code7bit.toChar()
            }
        }
        return null
    }

    companion object {
        private val MIME_AVC_SET = setOf("video/avc", "video/h264")
    }
}