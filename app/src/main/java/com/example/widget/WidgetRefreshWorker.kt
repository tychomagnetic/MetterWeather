package io.github.tychomagnetic.metterweather.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WidgetRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (io.github.tychomagnetic.metterweather.data.local.PreferencesManager(applicationContext)
                .getWidgetRefreshInterval() == io.github.tychomagnetic.metterweather.data.model.WidgetRefreshInterval.OFF) {
            return Result.success()
        }
        return when (WidgetRefreshManager.performWidgetRefresh(applicationContext)) {
        WidgetRefreshOutcome.SUCCESS,
        WidgetRefreshOutcome.SKIPPED,
        WidgetRefreshOutcome.CREDENTIALS_REQUIRED,
        WidgetRefreshOutcome.QUOTA_EXCEEDED,
        WidgetRefreshOutcome.FAILED -> Result.success()
        WidgetRefreshOutcome.RETRYABLE_FAILURE -> Result.retry()
        }
    }
}
