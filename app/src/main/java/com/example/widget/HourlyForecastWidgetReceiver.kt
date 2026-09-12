package io.github.tychomagnetic.metterweather.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class HourlyForecastWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HourlyForecastWidget()

    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        // Launcher updates recover missed downloads as well as the display clock.
        WidgetRefreshManager.scheduleAutoRefresh(context.applicationContext)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshManager.scheduleAutoRefresh(context.applicationContext)
    }

    override fun onDisabled(context: Context) {
        WidgetClock.cancel(context)
        WidgetRefreshManager.cancelAutoRefresh(context.applicationContext)
        super.onDisabled(context)
    }
}
