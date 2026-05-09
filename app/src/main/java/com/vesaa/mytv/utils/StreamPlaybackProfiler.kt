package com.vesaa.mytv.utils

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.Util

/**
 * 拉流地址画像（不写完整 URL，仅用于 Logcat 归类）。
 * SDP 等媒体细节在 Media3 RTSP 栈内解析，App 侧可通过超时/TCP/UA/重试间接提高运营商源的兼容面。
 */
object StreamPlaybackProfiler {

    fun classify(uri: Uri?): String {
        if (uri == null || uri == Uri.EMPTY) return "none"
        val scheme = uri.scheme?.lowercase().orEmpty().ifBlank { "?" }
        return when (scheme) {
            "rtsp" -> "live_rtsp"
            "rtmp" -> "live_rtmp"
            "udp", "rtp" -> "live_udp"
            "http", "https" -> when (Util.inferContentType(uri)) {
                C.CONTENT_TYPE_HLS -> "vod_hls"
                C.CONTENT_TYPE_DASH -> "vod_dash"
                C.CONTENT_TYPE_SS -> "vod_ss"
                else -> "vod_http_prog"
            }

            else -> "other_$scheme"
        }
    }

    /** host + 截断 path/query 指纹，便于对照服务器而不泄露完整串流路径 */
    fun redactedEndpoint(uri: Uri?): String {
        if (uri == null || uri == Uri.EMPTY) return "-"
        val host = uri.host?.take(48)?.ifBlank { "-" } ?: "-"
        val pq = uri.encodedQuery
        val path = uri.path?.take(24).orEmpty()
        val suffix = if (pq.isNullOrBlank()) "" else "?…"
        return "$host$path$suffix".ifBlank { host }
    }
}
