package com.vesaa.mytv

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import java.io.File
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.vesaa.mytv.BuildConfig
import com.vesaa.mytv.data.work.EpgRefreshWorkScheduler
import com.vesaa.mytv.ui.utils.SP

class MyTVApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        UnsafeTrustManager.enableUnsafeTrustManager()
        AppGlobal.applicationContext = applicationContext
        AppGlobal.cacheDir = applicationContext.cacheDir
        SP.init(applicationContext)

        if (BuildConfig.CHANNEL_LOGOS_ENABLED) {
            initCoilImageLoader()
        }

        EpgRefreshWorkScheduler.schedule(this)
    }

    /**
     * 台标等网络图：显式磁盘缓存目录 + 忽略服务端过短的 Cache-Control，覆盖安装后仍可利用缓存目录（未被系统清缓存时）。
     */
    private fun initCoilImageLoader() {
        val logoDisk = File(cacheDir, "coil_logo_disk").apply { mkdirs() }
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val maxHeap = Runtime.getRuntime().maxMemory()
        // 低内存设备 / 小堆盒子：减小台标内存池，降低与播放、列表同时驻留时的 OOM 概率
        val memFraction = when {
            am.isLowRamDevice -> 0.08
            maxHeap < 256L * 1024 * 1024 -> 0.10
            else -> 0.20
        }
        val loader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(memFraction)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(logoDisk)
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
        Coil.setImageLoader(loader)
    }
}
