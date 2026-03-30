package top.yogiczy.mytv.utils

import okhttp3.OkHttpClient

/** 应用内统一 OkHttp：附带调试 HTTP 日志拦截器（仅 [top.yogiczy.mytv.ui.utils.SP.debugAppLog] 开启时写历史）。 */
object AppOkHttp {
    fun newBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder().addInterceptor(DebugHttpLogInterceptor())

    fun client(): OkHttpClient = newBuilder().build()
}
