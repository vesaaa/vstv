package com.vesaa.mytv.data.repositories.iptv

import java.io.File
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

    /**
     * 获取远程直播源数据
     */
    private suspend fun fetchSource(sourceUrl: String, requestHeadersText: String) =
        withContext(Dispatchers.IO) {
        log.d("获取远程直播源: $sourceUrl")

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

            // 大文件解析耗 CPU，避免在 viewModel 主线程上执行导致界面长期停在「加载中」
            return withContext(Dispatchers.Default) {
                val parser = IptvParser.instances.first { it.isSupport(sourceUrl, sourceData) }
                var groupList = parser.parse(sourceData)
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
                parser.parse(data)
            } catch (e: Exception) {
                log.e("解析本地 IPTV 缓存失败", e)
                IptvGroupList()
            }
        }
    }
}