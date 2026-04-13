package com.vesaa.mytv.activities

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.vesaa.mytv.ui.utils.SP
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val launchAt = SystemClock.elapsedRealtime()
        installSplashScreen().setKeepOnScreenCondition {
            SystemClock.elapsedRealtime() - launchAt < 1000L
        }
        super.onCreate(savedInstanceState)

        val activityClass = when (SP.appDeviceDisplayType) {
            SP.AppDeviceDisplayType.LEANBACK -> LeanbackActivity::class.java
            SP.AppDeviceDisplayType.MOBILE -> MobileActivity::class.java
            SP.AppDeviceDisplayType.PAD -> PadActivity::class.java
        }

        lifecycleScope.launch {
            delay(1000L)
            startActivity(Intent(this@MainActivity, activityClass).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            finish()
        }
    }
}
