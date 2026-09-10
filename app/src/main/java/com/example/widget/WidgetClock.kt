package io.github.tychomagnetic.metterweather.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Hour-boundary trigger for cached display rollover and optional forecast refresh. */
object WidgetClock {
    internal const val ACTION_TICK = "io.github.tychomagnetic.metterweather.WIDGET_HOUR"
    internal fun nextHour(now: Long): Long = (now / 3_600_000L + 1) * 3_600_000L

    fun canSchedulePrecisely(context: Context): Boolean = Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

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
        try {
            if (canSchedulePrecisely(context)) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                return
            }
        } catch (_: SecurityException) {
            // Access can be revoked between the check and scheduling.
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }
}

class WidgetClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in setOf(WidgetClock.ACTION_TICK, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        WidgetClock.schedule(context)
        if (!WidgetRefreshManager.hasInstalledWidgets(context)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent?.action == WidgetClock.ACTION_TICK) {
                    WidgetRefreshManager.enqueueHourlyRefresh(context)
                }
                withTimeout(8_000L) { HourlyForecastWidget.updateAllWidgets(context) }
            } catch (error: Exception) {
                android.util.Log.w("WidgetClock", "Unable to finish widget clock redraw", error)
            } finally {
                pending.finish()
            }
        }
    }
}
