package io.github.tychomagnetic.metterweather

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import io.github.tychomagnetic.metterweather.widget.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetRecoveryTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `late recovery and alarm share one attempt but next boundary is eligible`() {
        assertTrue(WidgetRefreshManager.claimHourlyAttempt(app, 8 * 3_600_000L + 300_000))
        assertFalse(WidgetRefreshManager.claimHourlyAttempt(app, 8 * 3_600_000L + 600_000))
        assertTrue(WidgetRefreshManager.claimHourlyAttempt(app, 9 * 3_600_000L))
        assertFalse(WidgetRefreshManager.claimHourlyAttempt(app, 9 * 3_600_000L + 1))
    }

    @Test fun `missing location does not block the next hourly attempt`() = kotlinx.coroutines.runBlocking {
        org.robolectric.Shadows.shadowOf(android.appwidget.AppWidgetManager.getInstance(app))
            .bindAppWidgetId(1, android.content.ComponentName(app, HourlyForecastWidgetReceiver::class.java))
        io.github.tychomagnetic.metterweather.data.local.PreferencesManager(app).setWidgetGpsEnabled(true)
        assertTrue(WidgetRefreshManager.claimHourlyAttempt(app, 3_600_000L))
        assertEquals(WidgetRefreshOutcome.SKIPPED, WidgetRefreshManager.performWidgetRefresh(app))
        assertFalse(WidgetRefreshManager.claimHourlyAttempt(app, 3_600_001L))
        assertTrue(WidgetRefreshManager.claimHourlyAttempt(app, 7_200_000L))
        assertEquals(WidgetRefreshOutcome.SKIPPED, WidgetRefreshManager.performWidgetRefresh(app))
    }

    @Test fun `successful refresh clears pauses without losing credential identity`() {
        val state = app.getSharedPreferences("widget_refresh_state", Context.MODE_PRIVATE)
        state.edit().putBoolean("credentials_paused", true).putLong("quota_until", Long.MAX_VALUE)
            .putString("credentials", "fingerprint").commit()
        WidgetRefreshManager.clearFailurePause(app)
        assertFalse(state.getBoolean("credentials_paused", false))
        assertEquals(0L, state.getLong("quota_until", 0))
        assertEquals("fingerprint", state.getString("credentials", null))
    }

    @Test fun `clock adjustment backwards does not suppress future attempts`() {
        assertTrue(WidgetRefreshManager.claimHourlyAttempt(app, 10 * 3_600_000L))
        assertTrue(WidgetRefreshManager.claimHourlyAttempt(app, 8 * 3_600_000L))
        assertFalse(WidgetRefreshManager.claimHourlyAttempt(app, 8 * 3_600_000L + 1))
    }

    @Test fun `automatic work waits for connectivity and starts at the next hour`() {
        val now = 10 * 3_600_000L + 12_345L
        val request = WidgetRefreshManager.automaticRefreshRequest(now)
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(3_587_655L, request.workSpec.initialDelay)
        assertEquals(3_600_000L, request.workSpec.intervalDuration)
    }

    @Test fun `manual work is connected and marked as user initiated`() {
        val request = WidgetRefreshManager.manualRefreshRequest()
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertTrue(request.workSpec.input.getBoolean(WidgetRefreshManager.MANUAL_INPUT, false))
    }

    @Test fun `manual refresh remains allowed when scheduled refresh is off`() {
        assertTrue(WidgetRefreshManager.allowsRefreshAttempt(
            io.github.tychomagnetic.metterweather.data.model.WidgetRefreshInterval.OFF,
            manual = true
        ))
        assertFalse(WidgetRefreshManager.allowsRefreshAttempt(
            io.github.tychomagnetic.metterweather.data.model.WidgetRefreshInterval.OFF,
            manual = false
        ))
    }
}
