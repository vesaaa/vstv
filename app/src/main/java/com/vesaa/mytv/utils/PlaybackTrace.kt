package com.vesaa.mytv.utils

import android.net.Uri
import android.util.Log
import com.vesaa.mytv.ui.utils.SP

/**
 * Logcat：`VsTVPlayback` 标签下的可检索播放事件（需 [SP.playbackTraceLogcatEnabled]）。
 * 开启 [SP.debugAppLog] 时同步记入 [Logger] 环形历史。
 */
object PlaybackTrace {
    private const val TAG = "VsTVPlayback"
    private val historyLogger = Logger.create(TAG)

    fun i(
        uri: Uri?,
        event: String,
        detail: String = "",
        channelLabel: String? = null,
    ) {
        if (!SP.playbackTraceLogcatEnabled) return
        val profile = StreamPlaybackProfiler.classify(uri)
        val ep = StreamPlaybackProfiler.redactedEndpoint(uri)
        val tail = detail.trim().take(280)
        val ch = channelLabel?.trim()?.take(48)?.takeIf { it.isNotEmpty() }?.let { raw ->
            val safe = raw.replace('"', '\'').replace('\n', ' ')
            " ch=\"$safe\""
        } ?: ""
        val msg = "[$profile][$event]$ch ep=$ep${if (tail.isNotEmpty()) " $tail" else ""}"
        Log.i(TAG, msg)
        if (SP.debugAppLog) historyLogger.i(msg)
    }
}
