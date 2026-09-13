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
    @Test fun `UK town is recovered when device geocoder only supplies county fields`() {
        val address = android.location.Address(java.util.Locale.UK).apply {
            countryCode = "GB"
            subAdminArea = "Example County"
            adminArea = "England"
            featureName = "12"
            thoroughfare = "Example Road"
            setAddressLine(0, "12 Example Road, Exampleton AB12 3CD, UK")
        }
        assertEquals("Exampleton", WidgetLocationHelper.selectPlaceName(listOf(address)))
        address.setAddressLine(0, "12 Example Road, Exampleton, AB12 3CD, UK")
        assertEquals("Exampleton", WidgetLocationHelper.selectPlaceName(listOf(address)))
        address.setAddressLine(0, "12 Example Road, AB12 3CD, UK")
        assertEquals("Example County", WidgetLocationHelper.selectPlaceName(listOf(address)))
        address.setAddressLine(0, "Example Road, AB12 3CD, UK")
        assertEquals("Example County", WidgetLocationHelper.selectPlaceName(listOf(address)))
        address.setAddressLine(0, "12 Example Road, Exampleton AB12 3CD, UK")
        address.countryCode = "US"
        assertEquals("Example County", WidgetLocationHelper.selectPlaceName(listOf(address)))
    }

    @Test fun `settlement fields across all results take priority over county and formatted addresses`() {
        val county = android.location.Address(java.util.Locale.UK).apply {
            subAdminArea = "Example County"
            locality = " "
        }
        val town = android.location.Address(java.util.Locale.UK).apply { locality = " Exampleton " }
        assertEquals("Exampleton", WidgetLocationHelper.selectPlaceName(listOf(county, town)))
        town.subLocality = "Neighbourhood"
        assertEquals("Neighbourhood", WidgetLocationHelper.selectPlaceName(listOf(county, town)))
        assertEquals("Example County", WidgetLocationHelper.selectPlaceName(listOf(county)))
        assertNull(WidgetLocationHelper.selectPlaceName(emptyList()))
        assertNull(WidgetLocationHelper.selectPlaceName(null))
    }

    @Test fun `place naming preserves coordinates and survives errors and timeout`() = kotlinx.coroutines.test.runTest {
        val location = LocationItem.DEFAULT_LOCATIONS.first().copy(name = "Current Location", isCurrentLocation = true)
        val named = WidgetLocationHelper.withPlaceName(location) { "Exampleton" }
        assertEquals("Exampleton", named.name)
        assertEquals(location.latitude, named.latitude, 0.0)
        assertEquals(location.longitude, named.longitude, 0.0)
        assertEquals(location, WidgetLocationHelper.withPlaceName(location) { throw java.io.IOException("Offline") })
        assertEquals(location, WidgetLocationHelper.withPlaceName(location) { kotlinx.coroutines.delay(60_000); "Late name" })
        try {
            WidgetLocationHelper.withPlaceName(location) { throw kotlinx.coroutines.CancellationException() }
            fail("Cancellation must propagate")
        } catch (_: kotlinx.coroutines.CancellationException) { }
    }
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

    @Test fun `missing precise or background access falls back and fixed mode needs no permission`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        val prefs = PreferencesManager(app)
        val gpsLocation = LocationItem.DEFAULT_LOCATIONS.first().copy(isCurrentLocation = true)
        prefs.setWidgetGpsEnabled(true)
        prefs.setCachedWidgetGpsLocation(gpsLocation)
        assertEquals(gpsLocation, WidgetLocationHelper.getWidgetRefreshLocation(app, prefs) { null })
        assertEquals(gpsLocation, prefs.getCachedWidgetGpsLocation())
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        assertEquals(gpsLocation, WidgetLocationHelper.getWidgetRefreshLocation(app, prefs) { null })
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertNull(WidgetLocationHelper.getWidgetRefreshLocation(app, prefs) { null })
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

    @Test fun `coarse grant requests approximate fix and precise failure falls back`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = PreferencesManager(app).apply { setWidgetGpsEnabled(true) }
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        shadowOf(app).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val requests = mutableListOf<Int>()
        val fix = android.location.Location("network").apply { latitude = 52.0; longitude = -1.0 }
        val approximate = WidgetLocationHelper.getWidgetRefreshLocation(app, prefs) { request ->
            requests.add(request.granularity)
            assertEquals(com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY, request.priority)
            fix
        }
        assertEquals(listOf(com.google.android.gms.location.Granularity.GRANULARITY_COARSE), requests)
        assertEquals(52.0, requireNotNull(approximate).latitude, 0.0)
        requests.clear()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val fallback = WidgetLocationHelper.getWidgetRefreshLocation(app, prefs) { request ->
            requests.add(request.granularity)
            if (request.granularity == com.google.android.gms.location.Granularity.GRANULARITY_FINE) {
                throw SecurityException("Permission changed")
            }
            fix
        }
        assertEquals(listOf(com.google.android.gms.location.Granularity.GRANULARITY_FINE,
            com.google.android.gms.location.Granularity.GRANULARITY_COARSE), requests)
        assertEquals(approximate, fallback)
    }

    @Test fun `successful precise fix avoids fallback and cancellation propagates`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = PreferencesManager(app).apply { setWidgetGpsEnabled(true) }
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        var calls = 0
        WidgetLocationHelper.getWidgetRefreshLocation(app, prefs) {
            calls++
            android.location.Location("gps").apply { latitude = 52.0; longitude = -1.0 }
        }
        assertEquals(1, calls)
        try {
            WidgetLocationHelper.getWidgetRefreshLocation(app, prefs) { throw kotlinx.coroutines.CancellationException() }
            fail("Cancellation must propagate")
        } catch (_: kotlinx.coroutines.CancellationException) { }
    }
}
