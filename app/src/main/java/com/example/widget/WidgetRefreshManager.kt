package io.github.tychomagnetic.metterweather.widget

import android.content.Context
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.util.Log
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.BackoffPolicy
import androidx.work.Data
import io.github.tychomagnetic.metterweather.data.local.PreferencesManager
import io.github.tychomagnetic.metterweather.data.model.WidgetRefreshInterval
import io.github.tychomagnetic.metterweather.data.repository.WeatherRepository
import io.github.tychomagnetic.metterweather.data.repository.WidgetForecastException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

enum class WidgetRefreshOutcome {
    SUCCESS,
    SKIPPED,
    CREDENTIALS_REQUIRED,
    QUOTA_EXCEEDED,
    RETRYABLE_FAILURE,
    FAILED
}

object WidgetRefreshManager {

    private const val TAG = "WidgetRefreshManager"
    private const val WORK_NAME = "hourly_widget_refresh"
    private const val RECOVERY_WORK = "widget_hourly_recovery"
    private const val MANUAL_WORK = "widget_manual_refresh"
    internal const val MANUAL_INPUT = "manual_refresh"

    internal fun connectedNetworkConstraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    internal fun allowsRefreshAttempt(interval: WidgetRefreshInterval, manual: Boolean): Boolean =
        manual || interval != WidgetRefreshInterval.OFF

    internal fun automaticRefreshRequest(now: Long = System.currentTimeMillis()) =
        androidx.work.PeriodicWorkRequestBuilder<WidgetRefreshWorker>(1, TimeUnit.HOURS)
            .setInitialDelay((WidgetClock.nextHour(now) - now).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setConstraints(connectedNetworkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15L, TimeUnit.MINUTES)
            .build()

    internal fun manualRefreshRequest() =
        androidx.work.OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInputData(Data.Builder().putBoolean(MANUAL_INPUT, true).build())
            .setConstraints(connectedNetworkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15L, TimeUnit.MINUTES)
            .build()

    internal fun hasInstalledWidgets(context: Context): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, HourlyForecastWidgetReceiver::class.java)
        ).isNotEmpty()

    fun scheduleAutoRefresh(
        context: Context,
        interval: WidgetRefreshInterval = PreferencesManager(context).getWidgetRefreshInterval()
    ) {
        val installed = hasInstalledWidgets(context)
        if (!installed) {
            WidgetClock.cancel(context)
            cancelAllRefreshWork(context)
            Log.d(TAG, "Widget schedules cancelled (no widgets)")
            return
        }
        // Cached weather still needs a display rollover when automatic downloads
        // are off. This alarm never starts network work.
        WidgetClock.schedule(context)
        if (interval == WidgetRefreshInterval.OFF) {
            cancelAutoRefresh(context)
            Log.d(TAG, "Widget auto-refresh cancelled (OFF)")
            return
        }
        val workManager = runCatching {
            WorkManager.getInstance(context.applicationContext)
        }.getOrElse {
            // Robolectric/unit-test processes do not install WorkManager's
            // startup provider. A real app process always has it available.
            Log.d(TAG, "WorkManager is not available; skipping widget scheduling")
            return
        }
        // WorkManager owns automatic network downloads. The clock alarm only
        // advances the cached display and remains independently optional.
        workManager.cancelUniqueWork(RECOVERY_WORK)
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME, androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            automaticRefreshRequest())
    }

    fun enqueueManualRefresh(context: Context) {
        if (!hasInstalledWidgets(context)) return
        val request = androidx.work.OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInputData(Data.Builder().putBoolean(MANUAL_INPUT, true).build())
            .setConstraints(connectedNetworkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15L, TimeUnit.MINUTES)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            request.setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_WORK, androidx.work.ExistingWorkPolicy.REPLACE, request.build())
    }

    @Synchronized
    internal fun claimHourlyAttempt(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val state = context.getSharedPreferences("widget_attempt_state", Context.MODE_PRIVATE)
        val hour = now / 3_600_000L
        if (state.contains("hour") && state.getLong("hour", -1) == hour) return false
        // Record before GPS/network work so failures and overlapping triggers
        // cannot cause a retry storm. A new clock hour is always eligible.
        return state.edit().putLong("hour", hour).commit()
    }

    internal fun clearFailurePause(context: Context) {
        context.getSharedPreferences("widget_refresh_state", Context.MODE_PRIVATE)
            .edit().remove("credentials_paused").remove("quota_until").apply()
    }

    fun cancelAutoRefresh(context: Context) {
        runCatching {
            WorkManager.getInstance(context.applicationContext).apply {
                cancelUniqueWork(WORK_NAME)
                cancelUniqueWork(RECOVERY_WORK)
            }
        }.onFailure {
            Log.d(TAG, "WorkManager is not available; widget cancellation skipped")
        }
    }

    fun cancelAllRefreshWork(context: Context) {
        cancelAutoRefresh(context)
        runCatching { WorkManager.getInstance(context.applicationContext).cancelUniqueWork(MANUAL_WORK) }
            .onFailure { Log.d(TAG, "WorkManager is not available; manual widget cancellation skipped") }
    }
    suspend fun performWidgetRefresh(context: Context, ignoreFailurePauses: Boolean = false): WidgetRefreshOutcome {
        if (!hasInstalledWidgets(context)) return WidgetRefreshOutcome.SKIPPED
        var outcome = WidgetRefreshOutcome.FAILED
        withContext(Dispatchers.IO) {
            try {
                val prefs = PreferencesManager(context)
                val refreshState = context.getSharedPreferences("widget_refresh_state", Context.MODE_PRIVATE)
                val credentials = MessageDigest.getInstance("SHA-256").digest(
                    (prefs.getApiKey() + "\u0000" + prefs.getClientSecret()).toByteArray(Charsets.UTF_8)
                ).joinToString("") { "%02x".format(it) }
                if (refreshState.getString("credentials", null) != credentials) {
                    refreshState.edit().clear().putString("credentials", credentials).apply()
                }
                if (!ignoreFailurePauses && refreshState.getBoolean("credentials_paused", false)) {
                    outcome = WidgetRefreshOutcome.CREDENTIALS_REQUIRED
                    return@withContext
                }
                if (!ignoreFailurePauses && System.currentTimeMillis() < refreshState.getLong("quota_until", 0L)) {
                    outcome = WidgetRefreshOutcome.QUOTA_EXCEEDED
                    return@withContext
                }
                val location = WidgetLocationHelper.getWidgetRefreshLocation(context, prefs)
                if (location == null) {
                    Log.w(TAG, "Widget refresh skipped: permission or current location unavailable")
                    outcome = WidgetRefreshOutcome.SKIPPED
                    return@withContext
                }

                val repository = WeatherRepository(prefs)
                val result = repository.getSpotWidgetReport(location)
                result.onSuccess { report ->
                    if (prefs.setCachedWidgetWeatherReport(report)) {
                        clearFailurePause(context)
                        WidgetLocationHelper.commitSuccessfulGpsLocation(prefs, location)
                        prefs.setWidgetPageOffset(0)
                        outcome = WidgetRefreshOutcome.SUCCESS
                        Log.d(TAG, "Widget background refresh succeeded for ${location.name}")
                    } else {
                        outcome = WidgetRefreshOutcome.FAILED
                        Log.w(TAG, "Widget forecast was fetched but could not be cached")
                    }
                }.onFailure { error ->
                    outcome = classifyWidgetFailure(error)
                    when (outcome) {
                        WidgetRefreshOutcome.CREDENTIALS_REQUIRED ->
                            refreshState.edit().putBoolean("credentials_paused", true).apply()
                        WidgetRefreshOutcome.QUOTA_EXCEEDED ->
                            refreshState.edit().putLong("quota_until", quotaResumeTime(
                                (error as? WidgetForecastException)?.retryAfter, System.currentTimeMillis()
                            )).apply()
                        else -> Unit
                    }
                    Log.w(TAG, "Widget background refresh failed: ${error.message}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                outcome = classifyWidgetFailure(e)
                Log.e(TAG, "Error performing background widget refresh", e)
            }
        }
        HourlyForecastWidget.updateAllWidgets(context, resetPage = true)
        return outcome
    }
}
