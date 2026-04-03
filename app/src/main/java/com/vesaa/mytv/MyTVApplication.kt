package com.vesaa.mytv

import android.app.Application
import com.vesaa.mytv.data.work.EpgRefreshWorkScheduler
import com.vesaa.mytv.ui.utils.SP

class MyTVApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        UnsafeTrustManager.enableUnsafeTrustManager()
        AppGlobal.applicationContext = applicationContext
        AppGlobal.cacheDir = applicationContext.cacheDir
        SP.init(applicationContext)
        EpgRefreshWorkScheduler.schedule(this)
    }
}
