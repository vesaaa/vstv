package com.vesaa.mytv.utils

import android.net.Uri
import android.util.Log
import com.vesaa.mytv.ui.utils.SP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.min

/**
 * 将 `rtsp` 且路径以 `.smil` 结尾的地址拉取正文并解析出内嵌的真实 RTSP 流地址（简易 SMIL，常见于 Wowza）。
 * 日志统一打 [TAG]，便于用户抓 logcat 反馈；开启 [SP.debugAppLog] 时写入 [Logger] 历史。
 */
object RtspSmilResolver {
    private const val TAG = "VsTVSmil"
    private val historyLogger = Logger.create(TAG)

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val rtspUrlPattern = Pattern.compile(
        "rtsp://[^\\s\"'<>]+",
        Pattern.CASE_INSENSITIVE,
    )

    sealed class SmilResolveResult {
        data class Ok(val streamUri: Uri, val stages: List<String>) : SmilResolveResult()
        data class Fail(val reason: String, val stages: List<String>) : SmilResolveResult()
    }

    suspend fun resolve(
        original: Uri,
        userAgent: String,
        playbackLabel: String?,
    ): SmilResolveResult = withContext(Dispatchers.IO) {
        val stages = mutableListOf<String>()
        if (!original.scheme.equals("rtsp", ignoreCase = true)) {
            return@withContext SmilResolveResult.Fail("not rtsp", stages)
        }
        val path = original.path ?: ""
        if (!path.endsWith(".smil", ignoreCase = true)) {
            return@withContext SmilResolveResult.Fail("not smil path", stages)
        }

        fun trace(msg: String) {
            val safe = msg.replace('\n', ' ').take(500)
            Log.i(TAG, safe)
            if (SP.debugAppLog) historyLogger.i(safe)
            PlaybackTrace.i(original, "smil", detail = safe, channelLabel = playbackLabel)
            stages.add(safe)
        }

        trace("smil_resolve start uri=$original")

        val httpCandidates = buildHttpCandidates(original)
        for (httpUrl in httpCandidates) {
            val step = "http_get try=$httpUrl"
            try {
                val req = Request.Builder()
                    .url(httpUrl)
                    .header("User-Agent", userAgent.take(400))
                    .header("Accept", "*" + "/" + "*")
                    .get()
                    .build()
                okHttp.newCall(req).execute().use { resp ->
                    val code = resp.code
                    val ctype = resp.header("Content-Type") ?: ""
                    val body = resp.body?.string().orEmpty()
                    trace("$step code=$code ct=$ctype bytes=${body.length}")
                    if (code in 200..299 && body.isNotBlank()) {
                        val preview = body.trimStart().take(160)
                        trace("body_head=$preview")
                        val chosen = pickStreamFromSmil(original, body, trace)
                        if (chosen != null) {
                            trace("smil_resolve ok via=http stream=$chosen")
                            return@withContext SmilResolveResult.Ok(chosen, stages)
                        }
                        trace("parse_smil_xml no_rtsp_candidate after http_ok")
                    }
                }
            } catch (e: Exception) {
                trace("$step err=${e.javaClass.simpleName} msg=${e.message}")
            }
        }

        val describe = rtspDescribeBody(original, userAgent, trace)
        when (describe) {
            is DescribeOk -> {
                val chosen = pickStreamFromSmil(original, describe.body, trace)
                if (chosen != null) {
                    trace("smil_resolve ok via=rtsp_describe stream=$chosen")
                    return@withContext SmilResolveResult.Ok(chosen, stages)
                }
                trace("parse_smil_xml no_rtsp_candidate after describe_ok")
            }

            is DescribeFail -> trace(describe.detail)
        }

        trace("smil_resolve fail no_rtsp_in_smil")
        SmilResolveResult.Fail("无法从 SMIL 中得到 rtsp 流地址（已尝试 HTTP 与 RTSP DESCRIBE）", stages)
    }

    private fun buildHttpCandidates(uri: Uri): List<String> {
        val host = uri.host ?: return emptyList()
        val path = uri.path?.ifEmpty { "/" } ?: "/"
        val query = uri.encodedQuery?.let { "?$it" } ?: ""
        val out = linkedSetOf<String>()
        val port = uri.port
        out.add("http://$host$path$query")
        out.add("https://$host$path$query")
        if (port != -1 && port != 80 && port != 443) {
            out.add("http://$host:$port$path$query")
            out.add("https://$host:$port$path$query")
        }
        return out.toList()
    }

    private sealed interface DescribeResult
    private data class DescribeOk(val body: String) : DescribeResult
    private data class DescribeFail(val detail: String) : DescribeResult

    private fun rtspDescribeBody(uri: Uri, userAgent: String, trace: (String) -> Unit): DescribeResult {
        val host = uri.host ?: return DescribeFail("describe skip no_host")
        var port = uri.port
        if (port == -1) port = 554
        val requestUri = uri.toString()
        return try {
            Socket().use { sock ->
                sock.soTimeout = 12_000
                sock.connect(InetSocketAddress(host, port), 8_000)
                sock.getOutputStream().bufferedWriter(Charsets.UTF_8).use { w ->
                    val sb = StringBuilder()
                    sb.append("DESCRIBE ").append(requestUri).append(" RTSP/1.0\r\n")
                    sb.append("CSeq: 1\r\n")
                    sb.append("User-Agent: ").append(userAgent.take(400)).append("\r\n")
                    sb.append("Accept: application/sdp, application/rtsl, application/smil, text/xml, ")
                    sb.append("*").append("/").append("*").append("\r\n")
                    sb.append("\r\n")
                    w.write(sb.toString())
                    w.flush()
                }
                val inp = sock.getInputStream()
                val (headerBlock, initialTail) = readRtspHeaders(inp)
                val statusLine = headerBlock.lineSequence().firstOrNull().orEmpty()
                val headers = parseHeaders(headerBlock)
                trace("rtsp_describe status=${statusLine.trim()} cseq=${headers["CSeq"] ?: headers["cseq"]}")
                val code = statusLine.split(' ', limit = 4).getOrNull(1)?.toIntOrNull() ?: 0
                if (code !in 200..299) {
                    return DescribeFail("rtsp_describe http_status=$code head=${headerBlock.take(200)}")
                }
                val cl = headers["Content-Length"]?.toIntOrNull()
                    ?: headers["content-length"]?.toIntOrNull()
                val body = if (cl != null && cl in 0..2 * 1024 * 1024) {
                    readBodyFixed(inp, initialTail, cl)
                } else {
                    val merged = initialTail + readUpTo(inp, 2 * 1024 * 1024)
                    merged.decodeToString()
                }
                if (body.isBlank()) {
                    return DescribeFail("rtsp_describe empty_body")
                }
                trace("describe_body bytes=${body.length} head=${body.trimStart().take(160)}")
                if (body.trimStart().startsWith("v=0")) {
                    return DescribeFail("describe_body_is_sdp not_smil_xml")
                }
                DescribeOk(body)
            }
        } catch (e: Exception) {
            DescribeFail("rtsp_describe err=${e.javaClass.simpleName} msg=${e.message}")
        }
    }

    private fun readRtspHeaders(inp: java.io.InputStream): Pair<String, ByteArray> {
        val buf = ByteArrayOutputStream()
        val tmp = ByteArray(4096)
        while (buf.size() < 256 * 1024) {
            val n = inp.read(tmp)
            if (n <= 0) break
            buf.write(tmp, 0, n)
            val all = buf.toByteArray()
            val s = all.decodeToString()
            val idx = s.indexOf("\r\n\r\n")
            if (idx >= 0) {
                val headerEnd = idx + 4
                val headStr = s.substring(0, headerEnd)
                val tail = if (headerEnd < all.size) {
                    all.copyOfRange(headerEnd, all.size)
                } else {
                    byteArrayOf()
                }
                return headStr to tail
            }
        }
        return buf.toByteArray().decodeToString() to byteArrayOf()
    }

    private fun readBodyFixed(inp: java.io.InputStream, initial: ByteArray, totalLen: Int): String {
        val out = ByteArrayOutputStream()
        out.write(initial, 0, min(initial.size, totalLen))
        var got = out.size()
        val buf = ByteArray(8192)
        while (got < totalLen) {
            val need = totalLen - got
            val r = inp.read(buf, 0, min(buf.size, need))
            if (r <= 0) break
            out.write(buf, 0, r)
            got += r
        }
        return out.toByteArray().decodeToString()
    }

    private fun readUpTo(inp: java.io.InputStream, maxLen: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var left = maxLen
        while (left > 0) {
            val n = inp.read(buf, 0, min(buf.size, left))
            if (n <= 0) break
            out.write(buf, 0, n)
            left -= n
        }
        return out.toByteArray()
    }

    private fun parseHeaders(raw: String): Map<String, String> {
        val lines = raw.split("\r\n")
        val m = mutableMapOf<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) break
            val idx = line.indexOf(':')
            if (idx > 0) {
                val k = line.substring(0, idx).trim()
                val v = line.substring(idx + 1).trim()
                m[k] = v
            }
        }
        return m
    }

    private fun pickStreamFromSmil(
        original: Uri,
        xml: String,
        trace: (String) -> Unit,
    ): Uri? {
        var metaBase: String? = null
        val candidates = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val p = factory.newPullParser()
            p.setInput(ByteArrayInputStream(xml.toByteArray(StandardCharsets.UTF_8)), "UTF-8")
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val name = p.name?.lowercase().orEmpty()
                    if (name == "meta") {
                        val base = p.getAttributeValue(null, "base")
                            ?: p.getAttributeValue(XmlPullParser.NO_NAMESPACE, "base")
                        if (!base.isNullOrBlank()) {
                            metaBase = base.trim()
                            trace("smil_meta base=$metaBase")
                        }
                    }
                    if (name in setOf("audio", "video", "ref", "stream")) {
                        for (attr in listOf("src", "href")) {
                            val v = p.getAttributeValue(null, attr)
                            if (!v.isNullOrBlank()) candidates.add(v.trim())
                        }
                    }
                }
                event = p.next()
            }
        } catch (e: Exception) {
            trace("xml_parse err=${e.javaClass.simpleName} msg=${e.message} fallback_regex")
        }

        for (c in candidates) {
            val abs = toAbsoluteRtsp(metaBase, original, c) ?: continue
            trace("smil_candidate $abs")
            if (abs.scheme.equals("rtsp", ignoreCase = true)) return abs
        }

        val m = rtspUrlPattern.matcher(xml)
        while (m.find()) {
            val u = m.group().trimEnd { it in "/?\"'" }
            val parsed = try {
                Uri.parse(u)
            } catch (_: Throwable) {
                null
            } ?: continue
            if (parsed.scheme.equals("rtsp", ignoreCase = true)) {
                trace("smil_regex_candidate $parsed")
                return parsed
            }
        }
        return null
    }

    private fun toAbsoluteRtsp(metaBase: String?, original: Uri, ref: String): Uri? {
        val r = ref.trim()
        if (r.startsWith("rtsp://", ignoreCase = true)) {
            return Uri.parse(r)
        }
        val baseStr = when {
            !metaBase.isNullOrBlank() -> metaBase!!
            else -> {
                val auth = original.encodedAuthority ?: return null
                original.scheme + "://" + auth + "/"
            }
        }
        return try {
            val resolved = java.net.URI(baseStr).resolve(r).toString()
            Uri.parse(resolved)
        } catch (_: Exception) {
            null
        }
    }
}
