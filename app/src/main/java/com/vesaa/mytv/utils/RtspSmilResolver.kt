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

    /**
     * 仅当 DESCRIBE 的目标路径以 `.smil` 结尾时，才对 301/302 等响应解析 `Location` 并发起后续 DESCRIBE（见 [rtspDescribeBody]）。
     * 若将来要对非 SMIL 的 RTSP 重定向单独策略，可把「是否跟跳」提升为参数或策略接口，而不是改全局常量。
     */
    private const val SMIL_DESCRIBE_MAX_REDIRECTS = 3

    /** 与 HTTP 常见语义对齐；部分运营商 RTSP 栈会混用 303。扩展时在此集合增减即可。 */
    private val RTSP_REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)

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

        val emitLog: (String) -> Unit = { msg ->
            val safe = msg.replace('\n', ' ').take(500)
            Log.i(TAG, safe)
            if (SP.debugAppLog) historyLogger.i(safe)
            PlaybackTrace.i(original, "smil", detail = safe, channelLabel = playbackLabel)
            stages.add(safe)
        }

        emitLog("smil_resolve start uri=$original")

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
                    emitLog("$step code=$code ct=$ctype bytes=${body.length}")
                    if (code in 200..299 && body.isNotBlank()) {
                        val preview = body.trimStart().take(160)
                        emitLog("body_head=$preview")
                        val chosen = pickStreamFromSmil(original, body, emitLog)
                        if (chosen != null) {
                            emitLog("smil_resolve ok via=http stream=$chosen")
                            return@withContext SmilResolveResult.Ok(chosen, stages)
                        }
                        emitLog("parse_smil_xml no_rtsp_candidate after http_ok")
                    }
                }
            } catch (e: Exception) {
                emitLog("$step err=${e.javaClass.simpleName} msg=${e.message}")
            }
        }

        val describe = rtspDescribeBody(original, userAgent, emitLog)
        when (describe) {
            is DescribeOk -> {
                val chosen = pickStreamFromSmil(original, describe.body, emitLog)
                if (chosen != null) {
                    emitLog("smil_resolve ok via=rtsp_describe stream=$chosen")
                    return@withContext SmilResolveResult.Ok(chosen, stages)
                }
                emitLog("parse_smil_xml no_rtsp_candidate after describe_ok")
            }

            is DescribeFail -> emitLog(describe.detail)
        }

        emitLog("smil_resolve fail no_rtsp_in_smil")
        SmilResolveResult.Fail("无法从 SMIL 中得到 rtsp 流地址（已尝试 HTTP 与 RTSP DESCRIBE）", stages)
    }

    /**
     * 由 RTSP SMIL URI 推导可拉取正文的 HTTP URL 列表。
     * 不包含 https：同 host 的 443 在运营商边缘常未开放，易造成固定连接超时；若将来需 https，可在此恢复或改为可配置。
     */
    private fun buildHttpCandidates(uri: Uri): List<String> {
        val host = uri.host ?: return emptyList()
        val path = uri.path?.ifEmpty { "/" } ?: "/"
        val query = uri.encodedQuery?.let { "?$it" } ?: ""
        val out = linkedSetOf<String>()
        val port = uri.port
        out.add("http://$host$path$query")
        if (port != -1 && port != 80 && port != 443) {
            out.add("http://$host:$port$path$query")
        }
        return out.toList()
    }

    private sealed interface DescribeResult
    private data class DescribeOk(val body: String) : DescribeResult
    private data class DescribeFail(val detail: String) : DescribeResult

    /** 单次 TCP 上的 DESCRIBE 结果；跟跳由 [rtspDescribeBody] 组合多轮。 */
    private sealed interface RtspDescribeSingleOutcome {
        data class Ok(val body: String) : RtspDescribeSingleOutcome
        data class Fail(val detail: String) : RtspDescribeSingleOutcome
        data class Redirect(val target: Uri, val code: Int) : RtspDescribeSingleOutcome
    }

    /**
     * 对 SMIL 播放列表执行 RTSP DESCRIBE；若路径以 `.smil` 结尾且服务端返回重定向，则按 `Location` 继续 DESCRIBE（最多 [SMIL_DESCRIBE_MAX_REDIRECTS] 次跳转）。
     * 非 `.smil` 路径保持单次请求、不跟跳，避免将来若有其它入口误用本函数时产生意外行为。
     */
    private fun rtspDescribeBody(uri: Uri, userAgent: String, onLog: (String) -> Unit): DescribeResult {
        val allowRedirects = (uri.path ?: "").endsWith(".smil", ignoreCase = true)
        var current = uri
        var redirectsUsed = 0
        while (true) {
            when (val step = rtspDescribeSingle(current, userAgent, onLog)) {
                is RtspDescribeSingleOutcome.Ok -> return DescribeOk(step.body)
                is RtspDescribeSingleOutcome.Fail -> return DescribeFail(step.detail)
                is RtspDescribeSingleOutcome.Redirect -> {
                    if (!allowRedirects) {
                        return DescribeFail(
                            "rtsp_describe redirect_ignored non_smil_path code=${step.code} to=${step.target}",
                        )
                    }
                    if (redirectsUsed >= SMIL_DESCRIBE_MAX_REDIRECTS) {
                        return DescribeFail(
                            "rtsp_describe redirect_limit max=$SMIL_DESCRIBE_MAX_REDIRECTS last_code=${step.code} to=${step.target}",
                        )
                    }
                    redirectsUsed++
                    onLog(
                        "rtsp_describe redirect hop=$redirectsUsed/$SMIL_DESCRIBE_MAX_REDIRECTS code=${step.code} to=${step.target}",
                    )
                    current = step.target
                }
            }
        }
    }

    private fun rtspDescribeSingle(
        uri: Uri,
        userAgent: String,
        onLog: (String) -> Unit,
    ): RtspDescribeSingleOutcome {
        val host = uri.host ?: return RtspDescribeSingleOutcome.Fail("describe skip no_host")
        var port = uri.port
        if (port == -1) port = 554
        val requestUri = uri.toString()
        return try {
            Socket().use { sock ->
                sock.soTimeout = 12_000
                sock.connect(InetSocketAddress(host, port), 8_000)
                // 禁止对 getOutputStream().bufferedWriter().use { }：use 会 close Writer，进而关掉整条 Socket，读响应即 Socket is closed
                val sb = StringBuilder()
                sb.append("DESCRIBE ").append(requestUri).append(" RTSP/1.0\r\n")
                sb.append("CSeq: 1\r\n")
                sb.append("User-Agent: ").append(userAgent.take(400)).append("\r\n")
                sb.append("Accept: application/sdp, application/rtsl, application/smil, text/xml, ")
                sb.append("*").append("/").append("*").append("\r\n")
                sb.append("\r\n")
                val reqBytes = sb.toString().toByteArray(StandardCharsets.UTF_8)
                sock.getOutputStream().write(reqBytes)
                sock.getOutputStream().flush()
                onLog("rtsp_describe request_sent bytes=${reqBytes.size} port=$port uri=$requestUri")
                val inp = sock.getInputStream()
                val (headerBlock, initialTail) = readRtspHeaders(inp)
                val statusLine = headerBlock.lineSequence().firstOrNull().orEmpty()
                val headers = parseHeaders(headerBlock)
                onLog("rtsp_describe status=${statusLine.trim()} cseq=${headers["CSeq"] ?: headers["cseq"]}")
                val code = statusLine.split(' ', limit = 4).getOrNull(1)?.toIntOrNull() ?: 0
                if (code in RTSP_REDIRECT_STATUS_CODES) {
                    val locRaw = headerIgnoreCase(headers, "Location") ?: ""
                    val target = resolveRedirectRequestUri(uri, locRaw)
                    if (target == null) {
                        return RtspDescribeSingleOutcome.Fail(
                            "rtsp_describe redirect code=$code missing_or_bad_location loc_len=${locRaw.length} head=${headerBlock.take(280)}",
                        )
                    }
                    return RtspDescribeSingleOutcome.Redirect(target, code)
                }
                if (code !in 200..299) {
                    return RtspDescribeSingleOutcome.Fail(
                        "rtsp_describe http_status=$code head=${headerBlock.take(200)}",
                    )
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
                    return RtspDescribeSingleOutcome.Fail("rtsp_describe empty_body")
                }
                onLog("describe_body bytes=${body.length} head=${body.trimStart().take(160)}")
                if (body.trimStart().startsWith("v=0")) {
                    return RtspDescribeSingleOutcome.Fail("describe_body_is_sdp not_smil_xml")
                }
                RtspDescribeSingleOutcome.Ok(body)
            }
        } catch (e: Exception) {
            RtspDescribeSingleOutcome.Fail("rtsp_describe err=${e.javaClass.simpleName} msg=${e.message}")
        }
    }

    private fun headerIgnoreCase(headers: Map<String, String>, canonicalName: String): String? =
        headers.entries.firstOrNull { it.key.equals(canonicalName, ignoreCase = true) }?.value

    /**
     * 将 `Location` 解析为下一次 DESCRIBE 的 URI；支持绝对 rtsp(s) 与相对引用（相对当前请求 URI）。
     * 扩展：若遇 `Content-Base` 与 Location 组合等特例，可在此集中处理。
     */
    private fun resolveRedirectRequestUri(current: Uri, locationRaw: String): Uri? {
        var loc = locationRaw.trim()
        if (loc.startsWith('<') && loc.endsWith('>')) {
            loc = loc.substring(1, loc.length - 1).trim()
        }
        if (loc.isEmpty()) return null
        return try {
            when {
                loc.startsWith("rtsp://", ignoreCase = true) ||
                    loc.startsWith("rtsps://", ignoreCase = true) -> Uri.parse(loc)
                else -> {
                    val resolved = java.net.URI(current.toString()).resolve(loc).toString()
                    Uri.parse(resolved)
                }
            }
        } catch (_: Exception) {
            null
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
        onLog: (String) -> Unit,
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
                            onLog("smil_meta base=$metaBase")
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
            onLog("xml_parse err=${e.javaClass.simpleName} msg=${e.message} fallback_regex")
        }

        for (c in candidates) {
            val abs = toAbsoluteRtsp(metaBase, original, c) ?: continue
            onLog("smil_candidate $abs")
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
                onLog("smil_regex_candidate $parsed")
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
