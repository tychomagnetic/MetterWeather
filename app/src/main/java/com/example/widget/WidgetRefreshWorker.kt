package io.github.tychomagnetic.metterweather.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class WidgetRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Recovery work also repairs the display clock after a lost alarm.
        WidgetClock.schedule(applicationContext)
        if (io.github.tychomagnetic.metterweather.data.local.PreferencesManager(applicationContext)
                .getWidgetRefreshInterval() == io.github.tychomagnetic.metterweather.data.model.WidgetRefreshInterval.OFF) {
            return Result.success()
        }
        if (!WidgetRefreshManager.hasInstalledWidgets(applicationContext) ||
            !WidgetRefreshManager.claimHourlyAttempt(applicationContext)) return Result.success()
        try {
            WidgetRefreshManager.performWidgetRefresh(applicationContext)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w("WidgetRefreshWorker", "Hourly attempt failed; next hour remains eligible", error)
        }
        return Result.success()
    }
}
