package com.vesaa.mytv

import android.app.ActivityManager
import android.app.Application
import android.content.Context
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

        // WorkManager 在部分低端 ROM（魔百盒/S905 等）上初始化可能失败，不应阻塞启动
        try {
            EpgRefreshWorkScheduler.schedule(this)
        } catch (_: Exception) {
            // 静默降级：后台 EPG 定时刷新不可用，前台手动刷新仍正常
        }
    }

    /**
     * 台标等网络图：显式磁盘缓存目录 + 忽略服务端过短的 Cache-Control，覆盖安装后仍可利用缓存目录（未被系统清缓存时）。
     *
     * S905/魔百盒等低端设备注意：[ActivityManager.isLowRamDevice] 在许多运营商定制 ROM 上
     * 返回 false（未配置 ro.config.low_ram），因此改用设备实际总 RAM 与堆大小综合判断。
     */
    private fun initCoilImageLoader() {
        val logoDisk = File(cacheDir, "coil_logo_disk").apply { mkdirs() }
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val maxHeap = Runtime.getRuntime().maxMemory()
        val totalRam = try {
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.totalMem
        } catch (_: Exception) {
            0L
        }
        // 综合判断低端设备：系统标记 / 总 RAM < 1.5GB / 堆 < 128MB
        val isReallyLowEnd = am.isLowRamDevice ||
            (totalRam in 1..1536L * 1024 * 1024) ||
            maxHeap < 128L * 1024 * 1024
        val memFraction = if (isReallyLowEnd) 0.05 else 0.15
        val diskMaxBytes = if (isReallyLowEnd) 20L * 1024 * 1024 else 50L * 1024 * 1024
        val loader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(memFraction)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(logoDisk)
                    .maxSizeBytes(diskMaxBytes)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
        Coil.setImageLoader(loader)
    }
}
