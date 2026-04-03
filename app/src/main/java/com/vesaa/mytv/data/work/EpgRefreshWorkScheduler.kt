package com.vesaa.mytv.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.vesaa.mytv.data.utils.Constants
import com.vesaa.mytv.ui.utils.SP

/**
 * 注册 / 取消「周期拉取 EPG」的 WorkManager 任务。
 */
object EpgRefreshWorkScheduler {

    private const val UNIQUE_NAME = "epg_refresh_periodic"

    fun schedule(context: Context) {
        val appCtx = context.applicationContext
        if (!SP.epgEnable || SP.epgXmlUrl.isBlank()) {
            cancel(appCtx)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<EpgRefreshWorker>(
            Constants.EPG_BACKGROUND_REFRESH_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.MINUTES,
            )
            .build()

        WorkManager.getInstance(appCtx).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NAME)
    }
}
