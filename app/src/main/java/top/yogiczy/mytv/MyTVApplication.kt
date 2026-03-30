package top.yogiczy.mytv

import android.app.Application
import android.util.Log
import top.yogiczy.mytv.ui.utils.SP

class MyTVApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        UnsafeTrustManager.enableUnsafeTrustManager()
        AppGlobal.applicationContext = applicationContext
        AppGlobal.cacheDir = applicationContext.cacheDir
        SP.init(applicationContext)

        if (BuildConfig.USE_X5) {
            initX5ViaReflection()
        }
    }

    private fun initX5ViaReflection() {
        try {
            Class.forName("top.yogiczy.mytv.x5.X5Initializer")
                .getMethod("init", Application::class.java)
                .invoke(null, this)
        } catch (e: Throwable) {
            Log.e("MyTV", "X5 内核初始化失败（仅 originalX5* 变体应包含 TBS）", e)
        }
    }
}
