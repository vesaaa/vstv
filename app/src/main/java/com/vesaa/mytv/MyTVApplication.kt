package com.vesaa.mytv

import android.app.Application
import java.io.File
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.vesaa.mytv.data.work.EpgRefreshWorkScheduler
import com.vesaa.mytv.ui.utils.SP

class MyTVApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        UnsafeTrustManager.enableUnsafeTrustManager()
        AppGlobal.applicationContext = applicationContext
        AppGlobal.cacheDir = applicationContext.cacheDir
        SP.init(applicationContext)

        initCoilImageLoader()

        EpgRefreshWorkScheduler.schedule(this)
    }

    /**
     * 台标等网络图：显式磁盘缓存目录 + 忽略服务端过短的 Cache-Control，覆盖安装后仍可利用缓存目录（未被系统清缓存时）。
     */
    private fun initCoilImageLoader() {
        val logoDisk = File(cacheDir, "coil_logo_disk").apply { mkdirs() }
        val loader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.2)
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
