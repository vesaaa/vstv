package com.vesaa.mytv.data.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vesaa.mytv.data.repositories.epg.EpgRepository
import com.vesaa.mytv.data.repositories.iptv.IptvRepository
import com.vesaa.mytv.ui.utils.SP
import com.vesaa.mytv.utils.Logger
import com.vesaa.mytv.utils.builtinEpgDefaultRequestHeaders
import com.vesaa.mytv.utils.normalizeIptvRequestHeadersInput

/**
 * 在后台拉取 EPG XML、解析并写入 [EpgRepository] 的 JSON 缓存，供下次打开应用或前台刷新时直接使用。
 */
class EpgRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val log = Logger.create("EpgRefreshWorker")

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        if (!SP.epgEnable) {
            log.d("EPG 已关闭，跳过")
            return@withContext Result.success()
        }
        val xmlUrl = SP.epgXmlUrl.trim()
        if (xmlUrl.isBlank()) {
            log.d("EPG 地址为空，跳过")
            return@withContext Result.success()
        }

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < SP.epgRefreshTimeThreshold) {
            log.d("当前 ${hour} 点 < 阈值 ${SP.epgRefreshTimeThreshold}，跳过（与前台逻辑一致）")
            return@withContext Result.success()
        }

        val channels =
            IptvRepository().loadCachedIptvGroupListOrEmpty().flatMap { it.iptvList }
        val headers = epgRequestHeadersForWork()

        return@withContext try {
            EpgRepository().fetchAndPersistEpgCache(
                xmlUrl = xmlUrl,
                iptvChannels = channels,
                requestHeadersText = headers,
            )
            val headersNorm = normalizeIptvRequestHeadersInput(headers)
            SP.epgXmlUrlHistoryList += xmlUrl
            if (SP.epgXmlRequestHeaders.isNotBlank()) {
                val gNorm = normalizeIptvRequestHeadersInput(SP.epgXmlRequestHeaders)
                if (gNorm != SP.epgXmlRequestHeaders) {
                    SP.epgXmlRequestHeaders = gNorm
                }
            }
            SP.putEpgHeadersForUrl(xmlUrl, headersNorm)
            log.i("后台 EPG 刷新成功，频道数=${channels.size}")
            Result.success()
        } catch (e: Exception) {
            val network = e is IOException || e.cause is IOException
            if (network) {
                log.e("后台 EPG 网络失败，将重试", e)
                Result.retry()
            } else {
                log.e("后台 EPG 失败", e)
                Result.failure()
            }
        }
    }

    private fun epgRequestHeadersForWork(): String {
        val url = SP.epgXmlUrl
        val user = SP.epgXmlRequestHeaders.ifBlank { SP.getEpgHeadersForUrl(url) }
        if (user.isNotBlank()) return user
        return builtinEpgDefaultRequestHeaders(url)
    }
}
