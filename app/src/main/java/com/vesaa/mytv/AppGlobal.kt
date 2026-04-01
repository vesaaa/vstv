package com.vesaa.mytv

import android.content.Context
import java.io.File

/**
 * 应用全局变量
 */
object AppGlobal {
    /**
     * 缓存目录
     */
    lateinit var cacheDir: File

    /**
     * Application Context（用于网络、IP 解析等）
     */
    lateinit var applicationContext: Context
}