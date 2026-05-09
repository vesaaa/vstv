package com.vesaa.mytv.data.repositories.iptv

import java.io.File
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import com.vesaa.mytv.AppGlobal
import com.vesaa.mytv.data.entities.IptvGroupList
import com.vesaa.mytv.data.repositories.FileCacheRepository
import com.vesaa.mytv.data.repositories.iptv.parser.IptvParser
import com.vesaa.mytv.utils.AppOkHttp
import com.vesaa.mytv.utils.Logger
import com.vesaa.mytv.utils.IptvOutboundHeaderPolicy
import com.vesaa.mytv.utils.normalizeIptvRequestHeadersInput
import com.vesaa.mytv.utils.parseHttpHeaderLines
import com.vesaa.mytv.utils.toOkHttpHeaders
import com.vesaa.mytv.ui.utils.SP

/**
 * 直播源获取
 */
class IptvRepository : FileCacheRepository("iptv.txt") {
    private val log = Logger.create(javaClass.simpleName)
    private val m3uEpgAttrRegex = Regex("""\b(?:x-tvg-url|url-tvg)\s*=\s*(['"])(.*?)\1""", RegexOption.IGNORE_CASE)

    /**
     * 获取远程直播源数据
     */
    private suspend fun fetchSource(sourceUrl: String, requestHeadersText: String) =
        withContext(Dispatchers.IO) {
        log.d("获取远程直播源: $sourceUrl")

        if (sourceUrl.trim().startsWith(SP.IPTV_LOCAL_SOURCE_URL)) {
            val text = SP.readIptvLocalUploadOrNull()
            if (text.isNullOrBlank()) {
                throw Exception("本地订阅文件不存在或为空，请在网页管理端重新上传")
            }
            return@withContext text
        }

        val client = AppOkHttp.client()
        val norm = normalizeIptvRequestHeadersInput(requestHeadersText)
        val blended =
            IptvOutboundHeaderPolicy.applyToNormalizedHeadersText(norm, sourceUrl)
        val headerMap = blended.parseHttpHeaderLines()
        val reqBuilder = Request.Builder().url(sourceUrl)
        if (headerMap.isNotEmpty()) {
            reqBuilder.headers(headerMap.toOkHttpHeaders())
        }
        val request = reqBuilder.build()

        try {
            with(client.newCall(request).execute()) {
                if (!isSuccessful) {
                    throw Exception("获取远程直播源失败: $code")
                }

                return@with body!!.string()
            }
        } catch (ex: Exception) {
            log.e("获取远程直播源失败", ex)
            throw Exception("获取远程直播源失败，请检查网络连接", ex)
        }
    }

    /**
     * 获取直播源分组列表
     */
    suspend fun getIptvGroupList(
        sourceUrl: String,
        cacheTime: Long,
        requestHeadersText: String = "",
    ): IptvGroupList {
        if (sourceUrl.isBlank()) {
            return IptvGroupList()
        }
        try {
            val sourceData = getOrRefresh(cacheTime) {
                fetchSource(sourceUrl, requestHeadersText)
            }
            SP.iptvSourceEmbeddedEpgUrl = extractEmbeddedEpgUrlFromM3u(sourceData)

            // 大文件解析耗 CPU，避免在 viewModel 主线程上执行导致界面长期停在「加载中」
            return withContext(Dispatchers.Default) {
                val parser = IptvParser.instances.first { it.isSupport(sourceUrl, sourceData) }
                val normalizedData = resolveRelativeStreamUrls(sourceUrl, sourceData)
                var groupList = parser.parse(normalizedData)
                log.i("解析直播源完成：${groupList.size}个分组，${groupList.flatMap { it.iptvList }.size}个频道")

                groupList
            }
        } catch (ex: Exception) {
            log.e("获取直播源失败", ex)
            throw Exception(ex)
        }
    }

    /**
     * 仅读本地 [iptv.txt] 缓存并解析，不发起网络（供后台 EPG 任务按当前订阅频道过滤节目单）。
     * 无缓存或解析失败时返回空分组。
     */
    suspend fun loadCachedIptvGroupListOrEmpty(): IptvGroupList {
        val data = withContext(Dispatchers.IO) {
            val f = File(AppGlobal.cacheDir, "iptv.txt")
            if (!f.isFile) return@withContext ""
            f.readText()
        }
        if (data.isBlank()) return IptvGroupList()
        return withContext(Dispatchers.Default) {
            val url = SP.iptvSourceUrl.ifBlank { "https://local.invalid/playlist.m3u" }
            val parser = IptvParser.instances.firstOrNull { it.isSupport(url, data) }
                ?: return@withContext IptvGroupList()
            try {
                parser.parse(resolveRelativeStreamUrls(url, data))
            } catch (e: Exception) {
                log.e("解析本地 IPTV 缓存失败", e)
                IptvGroupList()
            }
        }
    }

    /**
     * 对 HTTP(S) 订阅中的相对频道地址做 URI.resolve 归一化，兼容 rtp2httpd 等网关输出的
     * `/rtp/...`、`./xx.ts` 形式地址。
     */
    private fun resolveRelativeStreamUrls(sourceUrl: String, sourceData: String): String {
        if (!sourceUrl.startsWith("http://", true) && !sourceUrl.startsWith("https://", true)) {
            return sourceData
        }
        val base = runCatching { URI(sourceUrl) }.getOrNull() ?: return sourceData
        val lines = sourceData.split("\r\n", "\n")
        var changed = false
        val rewritten = lines.map { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("#")) return@map raw
            val hasScheme = Regex("""^[a-zA-Z][a-zA-Z0-9+\-.]*://""").containsMatchIn(line)
            if (hasScheme) return@map raw
            val resolved = runCatching { base.resolve(line).toString() }.getOrElse { line }
            if (resolved != line) changed = true
            raw.replace(line, resolved)
        }
        return if (changed) rewritten.joinToString("\n") else sourceData
    }

    /**
     * 从 M3U 顶部 `#EXTM3U` 行提取节目单地址（`x-tvg-url` / `url-tvg`）。
     */
    private fun extractEmbeddedEpgUrlFromM3u(sourceData: String): String {
        val normalized = sourceData.trimStart('\uFEFF')
        val lines = normalized.split("\r\n", "\n")
        for (line in lines) {
            val t = line.trim()
            if (t.isBlank()) continue
            if (!t.startsWith("#")) break
            if (!t.startsWith("#EXTM3U", ignoreCase = true)) continue
            val match = m3uEpgAttrRegex.find(t) ?: continue
            val url = match.groupValues.getOrNull(2)?.trim().orEmpty()
            if (url.isNotBlank()) return url
        }
        return ""
    }
}