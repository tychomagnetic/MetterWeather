package io.github.tychomagnetic.metterweather

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.tychomagnetic.metterweather.data.local.PreferencesManager
import io.github.tychomagnetic.metterweather.data.model.LocationItem
import io.github.tychomagnetic.metterweather.widget.WidgetLocationHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetBackgroundLocationTest {
    @Test fun `background access needs both foreground and background grants`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        assertFalse(WidgetLocationHelper.hasBackgroundLocationPermission(app))
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertFalse(WidgetLocationHelper.hasBackgroundLocationPermission(app))
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        assertTrue(WidgetLocationHelper.hasBackgroundLocationPermission(app))
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertFalse(WidgetLocationHelper.hasBackgroundLocationPermission(app))
    }

    @Test @Config(sdk = [28])
    fun `older Android only needs coarse permission`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertTrue(WidgetLocationHelper.hasBackgroundLocationPermission(app))
    }

    @Test fun `denied precise or background access skips GPS refresh and fixed mode needs no permission`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        val prefs = PreferencesManager(app)
        val gpsLocation = LocationItem.DEFAULT_LOCATIONS.first().copy(isCurrentLocation = true)
        prefs.setWidgetGpsEnabled(true)
        prefs.setCachedWidgetGpsLocation(gpsLocation)
        assertNull(WidgetLocationHelper.getWidgetRefreshLocation(app, prefs))
        assertEquals(gpsLocation, prefs.getCachedWidgetGpsLocation())
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        assertNull(WidgetLocationHelper.getWidgetRefreshLocation(app, prefs))
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertNull(WidgetLocationHelper.getWidgetRefreshLocation(app, prefs))
        prefs.setWidgetGpsEnabled(false)
        assertEquals(prefs.getWidgetFixedLocation(), WidgetLocationHelper.getWidgetRefreshLocation(app, prefs))
    }

    @Test fun `precise request requires a new high accuracy fix`() {
        val request = WidgetLocationHelper.preciseLocationRequest()
        assertEquals(0L, request.maxUpdateAgeMillis)
        assertEquals(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, request.priority)
        assertEquals(com.google.android.gms.location.Granularity.GRANULARITY_FINE, request.granularity)
        assertEquals(30_000L, request.durationMillis)
    }
}
