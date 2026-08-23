package io.github.tychomagnetic.metterweather.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WidgetRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = when (WidgetRefreshManager.performWidgetRefresh(applicationContext)) {
        WidgetRefreshOutcome.SUCCESS,
        WidgetRefreshOutcome.SKIPPED -> Result.success()
        WidgetRefreshOutcome.FAILED -> Result.retry()
    }
}
