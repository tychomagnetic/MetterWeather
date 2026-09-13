package io.github.tychomagnetic.metterweather

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.tychomagnetic.metterweather.widget.WidgetClock
import io.github.tychomagnetic.metterweather.widget.widgetHourCount
import io.github.tychomagnetic.metterweather.widget.WidgetRefreshManager
import io.github.tychomagnetic.metterweather.widget.shiftedWidgetOffset
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetClockTest {
    @Test fun `responsive pages leave at least 48dp for each forecast tile`() {
        for (width in listOf(180f, 259f, 260f, 300f, 459f, 460f, 600f)) {
            val count = widgetHourCount(width)
            val tileWidth = (width - 8f - count * 2f) / count
            assertTrue(tileWidth >= 48f)
            assertEquals(count, shiftedWidgetOffset(0, 1, 30, count))
            assertEquals(0, shiftedWidgetOffset(count, -1, 30, count))
        }
        assertEquals(2, widgetHourCount(180f))
        assertEquals(5, widgetHourCount(260f))
        assertEquals(8, widgetHourCount(460f))
    }
    @Test fun `clock uses a non-waking inexact window at the next hour`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(android.appwidget.AppWidgetManager.getInstance(app)).bindAppWidgetId(1,
            android.content.ComponentName(app, io.github.tychomagnetic.metterweather.widget.HourlyForecastWidgetReceiver::class.java))
        io.github.tychomagnetic.metterweather.data.local.PreferencesManager(app).setWidgetRefreshInterval(
            io.github.tychomagnetic.metterweather.data.model.WidgetRefreshInterval.OFF)
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))
        WidgetClock.schedule(app)
        WidgetClock.schedule(app)
        assertEquals(1, alarms.scheduledAlarms.size)
        val alarm = requireNotNull(alarms.peekNextScheduledAlarm())
        assertEquals(WidgetClock.nextHour(System.currentTimeMillis()), alarm.triggerAtTime)
        assertEquals(AlarmManager.RTC, alarm.type)
        WidgetClock.cancel(app)
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test fun `clock aligns to next hour instead of an hour after startup`() {
        assertEquals(3_600_000L, WidgetClock.nextHour(3_599_999L))
        assertEquals(3_600_000L, WidgetClock.nextHour(1_800_000L))
        assertEquals(7_200_000L, WidgetClock.nextHour(3_600_000L))
        assertEquals(86_400_000L, WidgetClock.nextHour(86_399_999L))
    }

    @Test fun `turning automatic refresh off retains the cached display clock`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(android.appwidget.AppWidgetManager.getInstance(app)).bindAppWidgetId(2,
            android.content.ComponentName(app, io.github.tychomagnetic.metterweather.widget.HourlyForecastWidgetReceiver::class.java))
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))
        WidgetRefreshManager.scheduleAutoRefresh(
            app, io.github.tychomagnetic.metterweather.data.model.WidgetRefreshInterval.OFF)
        assertNotNull(alarms.peekNextScheduledAlarm())
        WidgetClock.cancel(app)
    }

    @Test fun `arrows move a whole visible page and clamp at forecast ends`() {
        assertEquals(5, shiftedWidgetOffset(0, 1, 30))
        assertEquals(10, shiftedWidgetOffset(5, 1, 30))
        assertEquals(5, shiftedWidgetOffset(10, -1, 30))
        assertEquals(0, shiftedWidgetOffset(0, -1, 30))
        assertEquals(27, shiftedWidgetOffset(25, 1, 27))
        assertEquals(22, shiftedWidgetOffset(30, -1, 27))
        assertEquals(0, shiftedWidgetOffset(0, 1, 0))
    }

    @Test fun `no display alarm is retained without installed widgets`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        WidgetClock.schedule(app)
        assertNull(shadowOf(app.getSystemService(AlarmManager::class.java)).nextScheduledAlarm)
    }
}
