package io.github.tychomagnetic.metterweather.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.tychomagnetic.metterweather.data.local.PreferencesManager
import io.github.tychomagnetic.metterweather.data.model.LocationItem
import java.util.TimeZone
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

object WidgetLocationHelper {

    private const val TAG = "WidgetLocationHelper"

    fun hasBackgroundLocationPermission(context: Context): Boolean =
        hasLocationPermission(context) && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED)

    fun hasPreciseLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    internal fun preciseLocationRequest() = com.google.android.gms.location.CurrentLocationRequest.Builder()
        .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
        .setGranularity(com.google.android.gms.location.Granularity.GRANULARITY_FINE)
        .setMaxUpdateAgeMillis(0L)
        .setDurationMillis(30_000L)
        .build()

    internal fun approximateLocationRequest() = com.google.android.gms.location.CurrentLocationRequest.Builder()
        .setPriority(com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY)
        .setGranularity(com.google.android.gms.location.Granularity.GRANULARITY_COARSE)
        .setMaxUpdateAgeMillis(0L)
        .setDurationMillis(30_000L)
        .build()

    /** Prefer precise, then approximate, then the last known widget location. */
    suspend fun getWidgetRefreshLocation(
        context: Context,
        prefs: PreferencesManager,
        requestFix: suspend (com.google.android.gms.location.CurrentLocationRequest) -> Location? = {
            requestCurrentLocation(context, it)
        }
    ): LocationItem? {
        if (!prefs.isWidgetGpsEnabled()) return prefs.getWidgetFixedLocation()
        if (!hasLocationPermission(context)) return null
        if (hasBackgroundLocationPermission(context)) {
            val requests = buildList {
                if (hasPreciseLocationPermission(context)) add(preciseLocationRequest())
                add(approximateLocationRequest())
            }
            for (request in requests) {
                val fix = try {
                    kotlinx.coroutines.withTimeoutOrNull(30_000L) { requestFix(request) }
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w(TAG, "Fresh widget location unavailable; trying fallback", error)
                    null
                }
                if (fix != null) {
                    // Reverse geocoding can use the network and block for an
                    // unbounded time on older Android versions. Forecast data is
                    // more useful than a locality label in a background widget.
                    return currentLocationItem(fix)
                }
            }
        }
        return getWidgetLocation(context, prefs)
    }

    private suspend fun requestCurrentLocation(
        context: Context,
        request: com.google.android.gms.location.CurrentLocationRequest
    ): Location? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) return null

        return try {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                val cancellation = com.google.android.gms.tasks.CancellationTokenSource()
                continuation.invokeOnCancellation { cancellation.cancel() }
                com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(request, cancellation.token)
                    .addOnSuccessListener { continuation.resumeWith(Result.success(it)) }
                    .addOnFailureListener { continuation.resumeWith(Result.failure(it)) }
                    .addOnCanceledListener { continuation.cancel() }
            }
        } catch (error: SecurityException) {
            // Permission can be revoked after the check while a background
            // refresh is starting. Treat that race as an unavailable fix.
            Log.w(TAG, "Location permission was revoked before the widget fix", error)
            null
        }
    }
    private fun currentLocationItem(location: Location): LocationItem = LocationItem(
        id = "widget_gps_current",
        name = "Current Location",
        latitude = location.latitude,
        longitude = location.longitude,
        timezone = TimeZone.getDefault().id,
        isCurrentLocation = true
    )

    /**
     * Resolves the target location for the widget based on user preferences.
     * Returns null when GPS mode is selected but permission/a location fix is unavailable.
     * It must not silently substitute a fixed or app-selected location.
     */
    fun getWidgetLocation(context: Context, prefs: PreferencesManager): LocationItem? {
        val useGps = prefs.isWidgetGpsEnabled()
        if (!useGps) {
            return prefs.getWidgetFixedLocation()
        }

        if (!hasLocationPermission(context)) {
            Log.d(TAG, "Location permissions not granted for widget GPS refresh")
            return null
        }

        // Prefer a newly available fix for the next forecast fetch. If Android's
        // while-in-use restriction prevents that background lookup, retain the
        // last GPS location whose widget forecast completed successfully.
        val gpsLoc = getImpreciseLocation(context)
        if (gpsLoc != null) {
            Log.d(TAG, "Using imprecise GPS location for widget: ${gpsLoc.name} (${gpsLoc.latitude}, ${gpsLoc.longitude})")
            return gpsLoc
        }

        val cachedGpsLocation = getLastSuccessfulGpsLocation(prefs)
        if (cachedGpsLocation != null) {
            Log.d(TAG, "Live GPS unavailable; refreshing the last successful widget GPS location")
            return cachedGpsLocation
        }

        Log.d(TAG, "Imprecise GPS location unavailable and no successful GPS widget location is cached")
        return null
    }

    /**
     * Returns the stable location associated with the currently displayed GPS
     * forecast. A fresh coordinate is not exposed here until its fetch succeeds,
     * preventing a widget redraw from pairing a new place with old weather.
     */
    fun getWidgetDisplayLocation(context: Context, prefs: PreferencesManager): LocationItem? {
        if (!prefs.isWidgetGpsEnabled()) return prefs.getWidgetFixedLocation()
        if (!hasLocationPermission(context)) return null
        return getLastSuccessfulGpsLocation(prefs) ?: getImpreciseLocation(context)
    }

    fun commitSuccessfulGpsLocation(prefs: PreferencesManager, location: LocationItem) {
        if (prefs.isWidgetGpsEnabled() && location.isCurrentLocation) {
            prefs.setCachedWidgetGpsLocation(location)
        }
    }

    private fun getLastSuccessfulGpsLocation(prefs: PreferencesManager): LocationItem? =
        prefs.getCachedWidgetGpsLocation()
            ?: prefs.getCachedWidgetWeatherReport()?.location?.takeIf { it.isCurrentLocation }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Obtains the last known coarse/imprecise location (Network or Passive provider preferred for battery & privacy).
     */
    fun getImpreciseLocation(context: Context): LocationItem? {
        try {
            if (!hasLocationPermission(context)) {
                Log.d(TAG, "Location permissions not granted for widget GPS refresh")
                return null
            }

            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

            // Coarse / Imprecise providers first
            val providers = listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
                LocationManager.GPS_PROVIDER
            )

            var bestLocation: Location? = null
            for (provider in providers) {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = try {
                        locationManager.getLastKnownLocation(provider)
                    } catch (_: SecurityException) {
                        null
                    }
                    if (loc != null) {
                        if (bestLocation == null || loc.time > bestLocation.time) {
                            bestLocation = loc
                        }
                    }
                }
            }

            if (bestLocation != null) {
                return LocationItem(
                    id = "widget_gps_current",
                    // Last-known coordinates should never block a widget redraw
                    // on a potentially network-backed reverse-geocode lookup.
                    name = "Current Location",
                    region = null,
                    country = null,
                    latitude = bestLocation.latitude,
                    longitude = bestLocation.longitude,
                    timezone = TimeZone.getDefault().id,
                    isCurrentLocation = true
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting imprecise location for widget", e)
        }
        return null
    }

}
