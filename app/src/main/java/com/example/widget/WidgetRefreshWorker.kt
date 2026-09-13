package io.github.tychomagnetic.metterweather.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WidgetRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val manual = inputData.getBoolean(WidgetRefreshManager.MANUAL_INPUT, false)
        val interval = io.github.tychomagnetic.metterweather.data.local.PreferencesManager(applicationContext)
            .getWidgetRefreshInterval()
        if (!WidgetRefreshManager.allowsRefreshAttempt(interval, manual)) return Result.success()
        // Keep the display alarm alive when the periodic job is the first
        // component to run after process death or a missed alarm.
        WidgetClock.schedule(applicationContext)
        if (!manual && (!WidgetRefreshManager.hasInstalledWidgets(applicationContext) ||
            !WidgetRefreshManager.claimHourlyAttempt(applicationContext))) return Result.success()
        try {
            // Start a Glance session observing WorkManager while this request runs,
            // including requests that were queued while the device was offline.
            if (manual) HourlyForecastWidget.updateAllWidgets(applicationContext)
            val outcome = WidgetRefreshManager.performWidgetRefresh(applicationContext, ignoreFailurePauses = manual)
            if (manual && outcome == WidgetRefreshOutcome.RETRYABLE_FAILURE) return Result.retry()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("WidgetRefreshWorker", "Hourly attempt failed; next hour remains eligible", error)
        }
        return Result.success()
    }
}
