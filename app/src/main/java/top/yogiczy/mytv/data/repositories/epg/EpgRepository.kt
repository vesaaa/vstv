package top.yogiczy.mytv.data.repositories.epg

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import top.yogiczy.mytv.data.entities.Epg
import top.yogiczy.mytv.data.entities.EpgList
import top.yogiczy.mytv.data.entities.EpgProgramme
import top.yogiczy.mytv.data.entities.EpgProgrammeList
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.data.repositories.FileCacheRepository
import top.yogiczy.mytv.data.repositories.epg.fetcher.EpgFetcher
import top.yogiczy.mytv.utils.AppOkHttp
import top.yogiczy.mytv.utils.Logger
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 节目单获取
 */
class EpgRepository : FileCacheRepository("epg.json") {
    private val log = Logger.create(javaClass.simpleName)
    private val epgXmlRepository = EpgXmlRepository()

    /**
     * 解析节目单xml
     */
    private suspend fun parseFromXml(
        xmlString: String,
        iptvChannels: List<Iptv> = emptyList(),
    ) = withContext(Dispatchers.Default) {
        val nameCandidates = iptvChannels.map { it.channelName.trim() }.filter { it.isNotEmpty() }.toSet()
        val idCandidates = iptvChannels.map { it.tvgId.trim() }.filter { it.isNotEmpty() }.toSet()
        val restrict = nameCandidates.isNotEmpty() || idCandidates.isNotEmpty()

        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xmlString))

        val epgMap = mutableMapOf<String, Epg>()

        fun parseTime(raw: String): Long {
            val time = raw.trim()
            if (time.isEmpty()) return 0
            if (time.length >= 14) {
                val normalized = if (time.length > 14 && time[14] != ' ') {
                    time.take(14) + " " + time.drop(14).trim()
                } else {
                    time
                }
                SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).parse(normalized)?.time?.let { return it }
                // 无时区后缀时按中国时区解析本地节目单时间
                val core = time.take(14)
                val local = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Shanghai")
                }
                local.parse(core)?.time?.let { return it }
            }
            return 0
        }

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "channel") {
                        val channelId = parser.getAttributeValue(null, "id")?.trim().orEmpty()
                        parser.nextTag()
                        val channelName = parser.nextText().trim()

                        val include = !restrict ||
                            nameCandidates.any { it.equals(channelName, ignoreCase = true) } ||
                            (channelId.isNotEmpty() && idCandidates.contains(channelId))

                        if (include) {
                            epgMap[channelId] = Epg(
                                channel = channelName,
                                programmes = EpgProgrammeList(),
                                channelId = channelId,
                            )
                        }
                    } else if (parser.name == "programme") {
                        val channelId = parser.getAttributeValue(null, "channel")?.trim().orEmpty()
                        val startTime = parser.getAttributeValue(null, "start").orEmpty()
                        val stopTime = parser.getAttributeValue(null, "stop").orEmpty()
                        parser.nextTag()
                        val title = parser.nextText()

                        if (channelId.isNotEmpty() && epgMap.containsKey(channelId)) {
                            var startAt = parseTime(startTime)
                            var endAt = parseTime(stopTime)
                            if (startAt > 0L && endAt <= startAt) {
                                endAt = startAt + 3600_000L
                            }
                            epgMap[channelId] = epgMap[channelId]!!.copy(
                                programmes = EpgProgrammeList(
                                    epgMap[channelId]!!.programmes + listOf(
                                        EpgProgramme(
                                            startAt = startAt,
                                            endAt = endAt,
                                            title = title,
                                        )
                                    )
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        log.i("解析节目单完成，共${epgMap.size}个频道")
        return@withContext EpgList(epgMap.values.toList())
    }

    suspend fun getEpgList(
        xmlUrl: String,
        iptvChannels: List<Iptv> = emptyList(),
        refreshTimeThreshold: Int,
    ) = withContext(Dispatchers.Default) {
        try {
            // 原逻辑在「未到刷新钟点」时直接返回空列表，既不读缓存也不拉网，导致 0～(阈值-1) 点整晚无节目单。
            if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < refreshTimeThreshold) {
                val cachedJson = readCacheDataOrNull()
                if (!cachedJson.isNullOrBlank()) {
                    log.d("未到刷新时间点(${refreshTimeThreshold}点)，使用本地节目单缓存")
                    return@withContext EpgList(Json.decodeFromString<List<Epg>>(cachedJson))
                }
                log.d("未到刷新时间点且无缓存，继续尝试拉取节目单")
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val xmlJson = getOrRefresh({ lastModified, _ ->
                dateFormat.format(System.currentTimeMillis()) != dateFormat.format(lastModified)
            }) {
                val xmlString = epgXmlRepository.getEpgXml(xmlUrl)
                Json.encodeToString(parseFromXml(xmlString, iptvChannels).value)
            }

            EpgList(Json.decodeFromString<List<Epg>>(xmlJson))
        } catch (ex: Exception) {
            log.e("获取节目单失败", ex)
            throw Exception(ex)
        }
    }
}

/**
 * 节目单xml获取
 */
private class EpgXmlRepository : FileCacheRepository("epg.xml") {
    private val log = Logger.create(javaClass.simpleName)

    /**
     * 获取远程xml
     */
    private suspend fun fetchXml(url: String): String = withContext(Dispatchers.IO) {
        log.d("获取远程节目单xml: $url")

        val client = AppOkHttp.client()
        val request = Request.Builder().url(url).build()

        try {
            with(client.newCall(request).execute()) {
                if (!isSuccessful) {
                    throw Exception("获取远程节目单xml失败: $code")
                }

                val fetcher = EpgFetcher.instances.first { it.isSupport(url) }

                return@with fetcher.fetch(this)
            }
        } catch (ex: Exception) {
            throw Exception("获取远程节目单xml失败，请检查网络连接", ex)
        }
    }

    /**
     * 获取xml
     */
    suspend fun getEpgXml(url: String): String {
        return getOrRefresh(0) {
            fetchXml(url)
        }
    }
}
