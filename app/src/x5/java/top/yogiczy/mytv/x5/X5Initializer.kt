package top.yogiczy.mytv.x5

import android.app.Application
import com.tencent.smtt.sdk.QbSdk

/**
 * 仅在 originalX5* 变体中编译；用于初始化腾讯 X5（TBS）内核。
 */
object X5Initializer {
    @JvmStatic
    fun init(app: Application) {
        QbSdk.initX5Environment(
            app,
            object : QbSdk.PreInitCallback {
                override fun onCoreInitFinished() {}

                override fun onViewInitFinished(finished: Boolean) {}
            },
        )
    }
}
