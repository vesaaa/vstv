package com.vesaa.mytv.utils

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** 应用内统一 OkHttp：附带调试 HTTP 日志拦截器（仅 [com.vesaa.mytv.ui.utils.SP.debugAppLog] 开启时写历史）。 */
object AppOkHttp {
    fun newBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .addInterceptor(DebugHttpLogInterceptor())

    fun client(): OkHttpClient = newBuilder().build()
}
