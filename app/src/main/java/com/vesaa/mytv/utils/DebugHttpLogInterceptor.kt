package com.vesaa.mytv.utils

import okhttp3.Interceptor
import okhttp3.Response
import com.vesaa.mytv.ui.utils.SP

/**
 * 在 [SP.debugAppLog] 开启时记录：请求方法、URL、请求头；响应码、短语、URL、响应体长度（不记录正文）。
 */
class DebugHttpLogInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val logEnabled = runCatching { SP.debugAppLog }.getOrDefault(false)
        val request = chain.request()
        if (logEnabled) {
            val log = Logger.create("HTTP")
            val headers = buildString {
                for (i in 0 until request.headers.size) {
                    append(request.headers.name(i))
                    append(':')
                    append(request.headers.value(i))
                    append('\n')
                }
            }.trimEnd()
            log.i("→ ${request.method} ${request.url}\n$headers")
        }

        val response = chain.proceed(request)

        if (logEnabled) {
            val log = Logger.create("HTTP")
            val body = response.body
            val fromBody = body?.contentLength() ?: -1L
            val sizePart = when {
                fromBody >= 0L -> "bodyBytes=$fromBody"
                else -> {
                    val h = response.header("Content-Length")
                    if (!h.isNullOrBlank()) "Content-Length=$h"
                    else "bodyBytes=unknown"
                }
            }
            log.i("← ${response.code} ${response.message} | ${response.request.url} | $sizePart")
        }

        return response
    }
}
