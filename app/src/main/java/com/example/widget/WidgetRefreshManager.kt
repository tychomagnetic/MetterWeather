package io.github.tychomagnetic.metterweather.widget

import android.content.Context
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.WorkManager
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

    internal fun hasInstalledWidgets(context: Context): Boolean =
        AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, HourlyForecastWidgetReceiver::class.java)
        ).isNotEmpty()

    fun scheduleAutoRefresh(
        context: Context,
        interval: WidgetRefreshInterval = PreferencesManager(context).getWidgetRefreshInterval()
    ) {
        WidgetClock.schedule(context)
        val workManager = runCatching {
            WorkManager.getInstance(context.applicationContext)
        }.getOrElse {
            // Robolectric/unit-test processes do not install WorkManager's
            // startup provider. A real app process always has it available.
            Log.d(TAG, "WorkManager is not available; skipping widget scheduling")
            return
        }
        if (interval == WidgetRefreshInterval.OFF || !hasInstalledWidgets(context)) {
            cancelAutoRefresh(context)
            Log.d(TAG, "Widget auto-refresh cancelled (OFF)")
            return
        }

        // Migrate away from the drifting periodic schedule. WidgetClock owns each hour.
        workManager.cancelUniqueWork(WORK_NAME)
    }

    private const val HOURLY_REQUEST = "widget_clock_refresh"

    fun enqueueHourlyRefresh(context: Context) {
        if (!hasInstalledWidgets(context) ||
            PreferencesManager(context).getWidgetRefreshInterval() == WidgetRefreshInterval.OFF) return
        val request = androidx.work.OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15L, TimeUnit.MINUTES)
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            request.setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        WorkManager.getInstance(context).enqueueUniqueWork(
            HOURLY_REQUEST, androidx.work.ExistingWorkPolicy.REPLACE, request.build())
    }

    fun cancelAutoRefresh(context: Context) {
        runCatching {
            WorkManager.getInstance(context.applicationContext).apply {
                cancelUniqueWork(WORK_NAME)
                cancelUniqueWork(HOURLY_REQUEST)
            }
        }.onFailure {
            Log.d(TAG, "WorkManager is not available; widget cancellation skipped")
        }
    }
    suspend fun performWidgetRefresh(context: Context): WidgetRefreshOutcome {
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
                if (refreshState.getBoolean("credentials_paused", false)) {
                    outcome = WidgetRefreshOutcome.CREDENTIALS_REQUIRED
                    return@withContext
                }
                if (System.currentTimeMillis() < refreshState.getLong("quota_until", 0L)) {
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
