package top.yogiczy.mytv.ui.utils

import android.content.Context
import android.widget.Toast
import com.koushikdutta.async.AsyncServer
import com.koushikdutta.async.http.body.JSONObjectBody
import com.koushikdutta.async.http.server.AsyncHttpServer
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.yogiczy.mytv.AppGlobal
import top.yogiczy.mytv.R
import top.yogiczy.mytv.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.utils.LanIpResolver
import top.yogiczy.mytv.utils.Loggable
import top.yogiczy.mytv.utils.normalizeIptvRequestHeadersInput

object HttpServer : Loggable() {
    const val SERVER_PORT = 1616

    private var showToast: (String) -> Unit = { }

    /** 设置页完整 URL（二维码与文案）；优先手动指定的 [SP.httpServerAdvertiseIp]，否则取当前网络 IPv4。 */
    fun serverUrl(): String {
        val ctx = AppGlobal.applicationContext
        val explicit = SP.httpServerAdvertiseIp.trim()
        val ip = when {
            explicit.isNotBlank() -> explicit
            else -> LanIpResolver.lanIPv4Candidates(ctx).firstOrNull() ?: "127.0.0.1"
        }
        return "http://$ip:$SERVER_PORT"
    }

    /** 在「自动」与本机各 IPv4 之间循环，用于解决二维码 IP 不准的问题。 */
    fun cycleHttpServerAdvertiseIp(): String {
        val ctx = AppGlobal.applicationContext
        val candidates = LanIpResolver.lanIPv4Candidates(ctx)
        val options = listOf("") + candidates
        val cur = SP.httpServerAdvertiseIp
        val idx = options.indexOf(cur).let { if (it >= 0) it else 0 }
        SP.httpServerAdvertiseIp = options[(idx + 1) % options.size]
        return serverUrl()
    }

    fun start(context: Context, showToast: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val server = AsyncHttpServer()
                server.listen(AsyncServer.getDefault(), SERVER_PORT)

                server.get("/") { _, response ->
                    handleRawResource(response, context, "text/html", R.raw.index)
                }
                server.get("/index_css.css") { _, response ->
                    handleRawResource(response, context, "text/css", R.raw.index_css)
                }
                server.get("/index_js.js") { _, response ->
                    handleRawResource(response, context, "text/javascript", R.raw.index_js)
                }

                server.get("/api/settings") { _, response ->
                    handleGetSettings(response)
                }

                server.get("/api/server-info") { _, response ->
                    handleGetServerInfo(response)
                }

                server.post("/api/settings") { request, response ->
                    handleSetSettings(request, response)
                }

                HttpServer.showToast = showToast
                log.i("服务已启动: 0.0.0.0:${SERVER_PORT}")
            } catch (ex: Exception) {
                log.e("服务启动失败: ${ex.message}", ex)
                launch(Dispatchers.Main) {
                    Toast.makeText(context, "设置服务启动失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun wrapResponse(response: AsyncHttpServerResponse) = response.apply {
        headers.set(
            "Access-Control-Allow-Methods", "POST, GET, DELETE, PUT, OPTIONS"
        )
        headers.set("Access-Control-Allow-Origin", "*")
        headers.set(
            "Access-Control-Allow-Headers", "Origin, Content-Type, X-Auth-Token"
        )
    }

    private fun handleRawResource(
        response: AsyncHttpServerResponse,
        context: Context,
        contentType: String,
        id: Int,
    ) {
        wrapResponse(response).apply {
            setContentType(contentType)
            send(context.resources.openRawResource(id).readBytes().decodeToString())
        }
    }

    private fun handleGetSettings(response: AsyncHttpServerResponse) {
        val ctx = AppGlobal.applicationContext
        wrapResponse(response).apply {
            setContentType("application/json")
            send(
                Json.encodeToString(
                    AllSettings(
                        appTitle = Constants.APP_TITLE,
                        appRepo = Constants.APP_REPO,
                        serverPort = SERVER_PORT,
                        iptvSourceUrl = SP.iptvSourceUrl,
                        iptvSourceRequestHeaders = SP.iptvSourceRequestHeaders,
                        httpServerAdvertiseIp = SP.httpServerAdvertiseIp,
                        lanIPv4Candidates = LanIpResolver.lanIPv4Candidates(ctx),
                        settingsPageUrl = serverUrl(),
                        videoPlayerUserAgent = SP.videoPlayerUserAgent,
                    )
                )
            )
        }
    }

    private fun handleGetServerInfo(response: AsyncHttpServerResponse) {
        val ctx = AppGlobal.applicationContext
        val candidates = LanIpResolver.lanIPv4Candidates(ctx)
        wrapResponse(response).apply {
            setContentType("application/json")
            send(
                Json.encodeToString(
                    ServerInfo(
                        port = SERVER_PORT,
                        httpServerAdvertiseIp = SP.httpServerAdvertiseIp,
                        lanIPv4Candidates = candidates,
                        settingsPageUrl = serverUrl(),
                    )
                )
            )
        }
    }

    private fun handleSetSettings(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        val body = request.getBody<JSONObjectBody>().get()

        if (body.has("iptvSourceUrl") || body.has("iptvSourceRequestHeaders")) {
            val iptvSourceUrl =
                if (body.has("iptvSourceUrl")) body.optString("iptvSourceUrl", "") else SP.iptvSourceUrl
            val iptvSourceRequestHeaders =
                if (body.has("iptvSourceRequestHeaders")) {
                    normalizeIptvRequestHeadersInput(body.optString("iptvSourceRequestHeaders", ""))
                } else {
                    SP.iptvSourceRequestHeaders
                }
            val iptvChanged = SP.iptvSourceUrl != iptvSourceUrl ||
                SP.iptvSourceRequestHeaders != iptvSourceRequestHeaders
            if (iptvChanged) {
                SP.iptvSourceUrl = iptvSourceUrl
                SP.iptvSourceRequestHeaders = iptvSourceRequestHeaders
                if (iptvSourceUrl.isNotBlank()) {
                    SP.putIptvSourceHeadersForUrl(iptvSourceUrl, iptvSourceRequestHeaders)
                }
                IptvRepository().clearCache()
            }
        }

        if (body.has("httpServerAdvertiseIp")) {
            val httpServerAdvertiseIp = body.optString("httpServerAdvertiseIp", "")
            if (SP.httpServerAdvertiseIp != httpServerAdvertiseIp) {
                SP.httpServerAdvertiseIp = httpServerAdvertiseIp
            }
        }

        if (body.has("epgXmlUrl")) {
            val epgXmlUrl = body.optString("epgXmlUrl", "")
            if (SP.epgXmlUrl != epgXmlUrl) {
                SP.epgXmlUrl = epgXmlUrl
                EpgRepository().clearCache()
            }
        }

        if (body.has("videoPlayerUserAgent")) {
            SP.videoPlayerUserAgent = body.optString("videoPlayerUserAgent", "")
        }

        wrapResponse(response).send("success")
    }

}

@Serializable
private data class AllSettings(
    val appTitle: String,
    val appRepo: String,
    val serverPort: Int = HttpServer.SERVER_PORT,
    val iptvSourceUrl: String,
    val iptvSourceRequestHeaders: String = "",
    val httpServerAdvertiseIp: String = "",
    val lanIPv4Candidates: List<String> = emptyList(),
    val settingsPageUrl: String = "",
    val videoPlayerUserAgent: String,
)

@Serializable
private data class ServerInfo(
    val port: Int,
    val httpServerAdvertiseIp: String,
    val lanIPv4Candidates: List<String>,
    val settingsPageUrl: String,
)