package top.yogiczy.mytv.ui.screens.leanback.main

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
import top.yogiczy.mytv.data.entities.EpgList
import top.yogiczy.mytv.data.entities.IptvGroupList
import top.yogiczy.mytv.data.entities.IptvGroupList.Companion.iptvList
import top.yogiczy.mytv.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.ui.utils.WebPushConfigNotifier
import top.yogiczy.mytv.utils.normalizeIptvRequestHeadersInput

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
                    simplify = SP.iptvSourceSimplify,
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
                _uiState.value = LeanbackMainUiState.Error(it.message)
                if (SP.iptvSourceUrl.isNotBlank()) {
                    SP.iptvSourceUrlHistoryList -= SP.iptvSourceUrl
                }
            }
            .map {
                _uiState.value = LeanbackMainUiState.Ready(iptvGroupList = it)
                if (SP.iptvSourceUrl.isNotBlank()) {
                    SP.iptvSourceUrlHistoryList += SP.iptvSourceUrl
                    val headersNorm = normalizeIptvRequestHeadersInput(headersForFetch)
                    if (SP.iptvSourceRequestHeaders.isNotBlank()) {
                        val gNorm = normalizeIptvRequestHeadersInput(SP.iptvSourceRequestHeaders)
                        if (gNorm != SP.iptvSourceRequestHeaders) {
                            SP.iptvSourceRequestHeaders = gNorm
                        }
                    }
                    SP.putIptvSourceHeadersForUrl(SP.iptvSourceUrl, headersNorm)
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
        val iptvGroupList = readyState.iptvGroupList

        flow {
            emit(
                epgRepository.getEpgList(
                    xmlUrl = SP.epgXmlUrl,
                    filteredChannels = iptvGroupList.iptvList.map { it.channelName },
                    refreshTimeThreshold = SP.epgRefreshTimeThreshold,
                )
            )
        }
            .retry(Constants.HTTP_RETRY_COUNT) { delay(Constants.HTTP_RETRY_INTERVAL); true }
            .catch {
                emit(EpgList())
                SP.epgXmlUrlHistoryList -= SP.epgXmlUrl
            }
            .map { epgList ->
                val r = _uiState.value as? LeanbackMainUiState.Ready ?: return@map
                _uiState.value = r.copy(epgList = epgList)
                SP.epgXmlUrlHistoryList += SP.epgXmlUrl
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