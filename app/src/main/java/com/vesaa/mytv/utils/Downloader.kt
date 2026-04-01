package com.vesaa.mytv.utils

import java.io.File
import java.io.FileOutputStream
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer

object Downloader : Loggable() {
    suspend fun downloadTo(url: String, filePath: String, onProgressCb: ((Int) -> Unit)?) =
        withContext(Dispatchers.IO) {
            log.d("下载文件: $url")
            val interceptor = Interceptor { chain ->
                val originalResponse = chain.proceed(chain.request())
                originalResponse.newBuilder()
                    .body(DownloadResponseBody(originalResponse, onProgressCb)).build()
            }

            val client = AppOkHttp.newBuilder().addNetworkInterceptor(interceptor).build()
            val request = okhttp3.Request.Builder().url(url).build()

            try {
                with(client.newCall(request).execute()) {
                    if (!isSuccessful) {
                        throw Exception("下载失败：HTTP $code")
                    }

                    val file = File(filePath)
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos -> fos.write(body!!.bytes()) }
                }
            } catch (ex: Exception) {
                log.e("下载文件失败", ex)
                val msg = describeDownloadFailure(ex)
                throw Exception(msg, ex)
            }
        }

    private fun describeDownloadFailure(ex: Throwable): String {
        var t: Throwable? = ex
        while (t != null) {
            when (t) {
                is SocketTimeoutException -> return "下载超时，请检查网络后重试"
                is InterruptedIOException -> return "下载已中断"
            }
            t = t.cause
        }
        val raw = ex.message?.trim()?.takeIf { it.isNotBlank() }
        return raw ?: "下载失败，请检查网络连接"
    }

    private class DownloadResponseBody(
        private val originalResponse: okhttp3.Response,
        private val onProgressCb: ((Int) -> Unit)?,
    ) : okhttp3.ResponseBody() {
        override fun contentLength() = originalResponse.body!!.contentLength()

        override fun contentType() = originalResponse.body?.contentType()

        override fun source(): BufferedSource {
            return object : ForwardingSource(originalResponse.body!!.source()) {
                var totalBytesRead = 0L

                override fun read(sink: okio.Buffer, byteCount: Long): Long {
                    val bytesRead = super.read(sink, byteCount)
                    totalBytesRead += if (bytesRead != -1L) bytesRead else 0
                    val len = contentLength()
                    val progress = when {
                        len <= 0L -> 0
                        else -> ((totalBytesRead * 100L) / len).toInt().coerceIn(0, 100)
                    }
                    CoroutineScope(Dispatchers.IO).launch {
                        onProgressCb?.invoke(progress)
                    }
                    return bytesRead
                }
            }.buffer()
        }
    }
}
