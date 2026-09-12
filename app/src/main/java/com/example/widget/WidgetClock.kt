package io.github.tychomagnetic.metterweather.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Non-waking hour-boundary trigger for cached display rollover. */
object WidgetClock {
    internal const val ACTION_TICK = "io.github.tychomagnetic.metterweather.WIDGET_HOUR"
    internal const val WINDOW_LENGTH_MILLIS = 10 * 60_000L
    internal fun nextHour(now: Long): Long = (now / 3_600_000L + 1) * 3_600_000L

    private fun operation(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, WidgetClockReceiver::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun cancel(context: Context) = context.getSystemService(AlarmManager::class.java).cancel(operation(context))

    fun schedule(context: Context) {
        if (!WidgetRefreshManager.hasInstalledWidgets(context)) {
            cancel(context)
            return
        }
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = nextHour(System.currentTimeMillis())
        val pending = operation(context)
        // The display rollover is best-effort and must not wake a sleeping device.
        // WorkManager separately handles network refreshes when conditions permit.
        alarms.setWindow(AlarmManager.RTC, at, WINDOW_LENGTH_MILLIS, pending)
    }
}

class WidgetClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in setOf(WidgetClock.ACTION_TICK, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED)) return
        WidgetClock.schedule(context)
        if (!WidgetRefreshManager.hasInstalledWidgets(context)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(8_000L) { HourlyForecastWidget.updateAllWidgets(context) }
            } catch (error: Exception) {
                android.util.Log.w("WidgetClock", "Unable to finish widget clock redraw", error)
            } finally {
                pending.finish()
            }
        }
    }
}
