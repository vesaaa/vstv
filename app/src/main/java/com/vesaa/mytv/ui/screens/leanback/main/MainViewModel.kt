package com.vesaa.mytv.ui.screens.leanback.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.vesaa.mytv.data.entities.EpgList
import com.vesaa.mytv.data.entities.IptvGroupList
import com.vesaa.mytv.data.entities.IptvGroupList.Companion.iptvList
import com.vesaa.mytv.data.repositories.epg.EpgRepository
import com.vesaa.mytv.data.repositories.iptv.IptvRepository
import com.vesaa.mytv.data.utils.Constants
import com.vesaa.mytv.ui.utils.SP
import com.vesaa.mytv.ui.utils.WebPushConfigNotifier
import com.vesaa.mytv.utils.defaultEpgRequestHeadersAfterUserEmpty
import com.vesaa.mytv.utils.normalizeIptvRequestHeadersInput

class LeanbackMainViewModel : ViewModel() {
    private val iptvRepository = IptvRepository()
    private val epgRepository = EpgRepository()

    private val _uiState = MutableStateFlow<LeanbackMainUiState>(LeanbackMainUiState.Loading())
    val uiState: StateFlow<LeanbackMainUiState> = _uiState.asStateFlow()

    private var externalReloadJob: Job? = null

    init {
        viewModelScope.launch {
            refreshIptv()
            refreshEpg()
        }
        viewModelScope.launch {
            WebPushConfigNotifier.updates.collect {
                externalReloadJob?.cancel()
                externalReloadJob = viewModelScope.launch {
                    refreshIptv()
                    refreshEpg()
                }
            }
        }
    }

    /** 全局请求头为空时回退到按订阅 URL 保存的头（与设置页从历史选择行为一致） */
    private fun iptvRequestHeadersForFetch(): String {
        val global = SP.iptvSourceRequestHeaders
        if (global.isNotBlank()) return global
        return SP.getIptvSourceHeadersForUrl(SP.iptvSourceUrl)
    }

    private fun epgRequestHeadersForFetch(): String {
        val url = effectiveEpgXmlUrl()
        val explicitUrl = SP.epgXmlUrl.trim()
        // 当有效地址来自源内 x-tvg-url 且与设置页地址不同，避免误用旧的全局请求头。
        val user = if (SP.epgXmlRequestHeaders.isNotBlank() && explicitUrl == url) {
            SP.epgXmlRequestHeaders
        } else {
            SP.getEpgHeadersForUrl(url)
        }
        if (user.isNotBlank()) return user
        return defaultEpgRequestHeadersAfterUserEmpty(url, SP.iptvSourceEmbeddedEpgUrl)
    }

    /** 节目单优先级：直播源内声明（x-tvg-url/url-tvg） > 用户设置 > 内置默认。 */
    private fun effectiveEpgXmlUrl(): String {
        return SP.iptvSourceEmbeddedEpgUrl.trim().ifBlank { SP.epgXmlUrl.trim() }
    }

    private suspend fun refreshIptv() {
        if (SP.iptvSourceUrl.isBlank()) {
            _uiState.value = LeanbackMainUiState.Ready(iptvGroupList = IptvGroupList())
            return
        }

        val headersForFetch = iptvRequestHeadersForFetch()
        flow {
            emit(
                iptvRepository.getIptvGroupList(
                    sourceUrl = SP.iptvSourceUrl,
                    cacheTime = SP.iptvSourceCacheTime,
                    requestHeadersText = headersForFetch,
                )
            )
        }
            .retryWhen { _, attempt ->
                if (attempt >= Constants.HTTP_RETRY_COUNT) return@retryWhen false

                _uiState.value =
                    LeanbackMainUiState.Loading("获取远程直播源(${attempt + 1}/${Constants.HTTP_RETRY_COUNT})...")
                delay(Constants.HTTP_RETRY_INTERVAL)
                true
            }
            .catch {
                val src = SP.iptvSourceUrl.trim()
                val msg = it.message.orEmpty()
                // 本地上传源丢失时，自动回退为「未设置默认订阅」，避免持续失败循环干扰设置操作。
                if (src.startsWith(SP.IPTV_LOCAL_SOURCE_URL) &&
                    msg.contains("本地订阅文件不存在", ignoreCase = false)
                ) {
                    SP.iptvSourceUrl = ""
                    _uiState.value = LeanbackMainUiState.Ready(iptvGroupList = IptvGroupList())
                    return@catch
                }
                _uiState.value = LeanbackMainUiState.Error(it.message)
                if (SP.iptvSourceUrl.isNotBlank() &&
                    !SP.iptvSourceUrl.trim().startsWith(SP.IPTV_LOCAL_SOURCE_URL)
                ) {
                    SP.iptvSourceUrlHistoryList -= SP.iptvSourceUrl
                }
            }
            .map {
                _uiState.value = LeanbackMainUiState.Ready(iptvGroupList = it)
                if (SP.iptvSourceUrl.isNotBlank()) {
                    SP.iptvSourceUrlHistoryList += SP.iptvSourceUrl
                    val headersNorm = normalizeIptvRequestHeadersInput(headersForFetch)
                    val channelHeadersNorm = normalizeIptvRequestHeadersInput(
                        SP.iptvChannelRequestHeaders.ifBlank { headersNorm },
                    )
                    if (SP.iptvSourceRequestHeaders.isNotBlank()) {
                        val gNorm = normalizeIptvRequestHeadersInput(SP.iptvSourceRequestHeaders)
                        if (gNorm != SP.iptvSourceRequestHeaders) {
                            SP.iptvSourceRequestHeaders = gNorm
                        }
                    }
                    SP.putIptvSourceHeadersForUrl(SP.iptvSourceUrl, headersNorm)
                    SP.putIptvChannelHeadersForUrl(SP.iptvSourceUrl, channelHeadersNorm)
                }
                it
            }
            .collect()
    }

    private suspend fun refreshEpg() {
        val readyState = _uiState.value as? LeanbackMainUiState.Ready ?: return
        if (!SP.epgEnable) {
            _uiState.value = readyState.copy(epgList = EpgList())
            return
        }
        val xmlUrl = effectiveEpgXmlUrl()
        if (xmlUrl.isBlank()) {
            _uiState.value = readyState.copy(epgList = EpgList())
            return
        }
        val iptvGroupList = readyState.iptvGroupList
        val headersForEpg = epgRequestHeadersForFetch()

        flow {
            emit(
                epgRepository.getEpgList(
                    xmlUrl = xmlUrl,
                    iptvChannels = iptvGroupList.iptvList,
                    refreshTimeThreshold = SP.epgRefreshTimeThreshold,
                    requestHeadersText = headersForEpg,
                )
            )
        }
            .retry(Constants.HTTP_RETRY_COUNT) { delay(Constants.HTTP_RETRY_INTERVAL); true }
            .catch {
                emit(EpgList())
                SP.epgXmlUrlHistoryList -= xmlUrl
            }
            .map { epgList ->
                val r = _uiState.value as? LeanbackMainUiState.Ready ?: return@map
                _uiState.value = r.copy(epgList = epgList)
                SP.epgXmlUrlHistoryList += xmlUrl
                val headersNorm = normalizeIptvRequestHeadersInput(headersForEpg)
                if (SP.epgXmlRequestHeaders.isNotBlank()) {
                    val gNorm = normalizeIptvRequestHeadersInput(SP.epgXmlRequestHeaders)
                    if (gNorm != SP.epgXmlRequestHeaders) {
                        SP.epgXmlRequestHeaders = gNorm
                    }
                }
                SP.putEpgHeadersForUrl(xmlUrl, headersNorm)
            }
            .collect()
    }
}

sealed interface LeanbackMainUiState {
    data class Loading(val message: String? = null) : LeanbackMainUiState
    data class Error(val message: String? = null) : LeanbackMainUiState
    data class Ready(
        val iptvGroupList: IptvGroupList = IptvGroupList(),
        val epgList: EpgList = EpgList(),
    ) : LeanbackMainUiState
}