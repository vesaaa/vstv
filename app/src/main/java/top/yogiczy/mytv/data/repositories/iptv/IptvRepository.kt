package top.yogiczy.mytv.data.repositories.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.entities.IptvGroup
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvList
import top.yogiczy.mytv.data.repositories.FileCacheRepository
import top.yogiczy.mytv.data.repositories.iptv.parser.IptvParser
import top.yogiczy.mytv.utils.AppOkHttp
import top.yogiczy.mytv.utils.Logger
import top.yogiczy.mytv.utils.normalizeIptvRequestHeadersInput
import top.yogiczy.mytv.utils.parseHttpHeaderLines
import top.yogiczy.mytv.utils.toOkHttpHeaders

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
        val headerMap = normalizeIptvRequestHeadersInput(requestHeadersText).parseHttpHeaderLines()
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
     * 简化规则
     */
    private fun simplifyTest(group: IptvGroup, iptv: Iptv): Boolean {
        return iptv.name.lowercase().startsWith("cctv") || iptv.name.endsWith("卫视")
    }

    /**
     * 获取直播源分组列表
     */
    suspend fun getIptvGroupList(
        sourceUrl: String,
        cacheTime: Long,
        simplify: Boolean = false,
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

                if (simplify) {
                    groupList = IptvGroupList(groupList.map { group ->
                        IptvGroup(
                            name = group.name, iptvList = IptvList(group.iptvList.filter { iptv ->
                                simplifyTest(group, iptv)
                            })
                        )
                    }.filter { it.iptvList.isNotEmpty() })
                }

                groupList
            }
        } catch (ex: Exception) {
            log.e("获取直播源失败", ex)
            throw Exception(ex)
        }
    }
}