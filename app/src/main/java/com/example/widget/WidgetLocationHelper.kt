package io.github.tychomagnetic.metterweather.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.Geocoder
import android.location.Address
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
    private val legacyGeocoderBusy = java.util.concurrent.atomic.AtomicBoolean(false)
    private val legacyGeocoderExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "widget-geocoder").apply { isDaemon = true }
    }

    /** Naming is optional: bound the wait, retain coordinates on failure, preserve cancellation. */
    internal suspend fun withPlaceName(
        location: LocationItem,
        lookup: suspend () -> String?
    ): LocationItem {
        val name = try {
            withTimeoutOrNull(2_000L) { lookup() }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Place name unavailable; retaining widget coordinates", error)
            null
        }
        return if (name.isNullOrBlank()) location else location.copy(name = name)
    }

    private suspend fun namedLocation(context: Context, location: LocationItem): LocationItem =
        if (!location.isCurrentLocation) location else withPlaceName(location) {
            reverseGeocode(context, location.latitude, location.longitude)
        }

    private suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, java.util.Locale.getDefault())
        if (Build.VERSION.SDK_INT >= 33) return suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocation(latitude, longitude, 5, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (continuation.isActive) continuation.resume(selectPlaceName(addresses))
                }
                override fun onError(errorMessage: String?) {
                    if (continuation.isActive) continuation.resume(null)
                }
            })
        }
        // Older Android exposes only a blocking API. Isolate it from the worker's
        // coroutine so its two-second timeout still returns promptly. Allow at most
        // one legacy lookup, even if a platform geocoder ignores interruption.
        if (!legacyGeocoderBusy.compareAndSet(false, true)) return null
        return suspendCancellableCoroutine { continuation ->
            legacyGeocoderExecutor.execute {
                try {
                    @Suppress("DEPRECATION")
                    val result = if (continuation.isActive) selectPlaceName(geocoder.getFromLocation(latitude, longitude, 5)) else null
                    if (continuation.isActive) continuation.resume(result)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                } finally {
                    legacyGeocoderBusy.set(false)
                }
            }
        }
    }

    /** Search all results for a settlement before accepting a county/region. */
    internal fun selectPlaceName(addresses: List<Address>?): String? {
        val results = addresses.orEmpty()
        fun firstName(field: (Address) -> String?) = results.firstNotNullOfOrNull {
            field(it)?.trim()?.takeIf(String::isNotEmpty)
        }
        return firstName { it.subLocality }
            ?: firstName { it.locality }
            ?: firstName { ukPostalTown(it) }
            ?: firstName { it.subAdminArea }
            ?: firstName { it.adminArea }
    }

    private val ukPostcode = Regex("\\b(?:GIR\\s*0AA|[A-Z]{1,2}\\d[A-Z\\d]?\\s*\\d[A-Z]{2})\\b", RegexOption.IGNORE_CASE)

    // Some Android geocoder backends omit locality even though the formatted UK
    // address contains "Town POSTCODE". Only interpret that specific format;
    // arbitrary feature names can be house numbers, streets or businesses.
    private fun ukPostalTown(address: Address): String? {
        if (!address.countryCode.equals("GB", ignoreCase = true)) return null
        for (lineIndex in 0..address.maxAddressLineIndex) {
            val parts = address.getAddressLine(lineIndex).orEmpty().split(',').map(String::trim)
            for ((index, part) in parts.withIndex()) {
                val postcode = ukPostcode.find(part) ?: continue
                if (part.substring(postcode.range.last + 1).isNotBlank()) continue
                val prefix = part.substring(0, postcode.range.first).trim()
                val town = prefix.ifEmpty { parts.getOrNull(index - 1).orEmpty() }
                val excluded = listOf(address.thoroughfare, address.subThoroughfare,
                    address.premises, address.featureName, address.subAdminArea,
                    address.adminArea, address.countryName)
                if (town.isNotBlank() && town.none(Char::isDigit) &&
                    excluded.none { it?.trim().equals(town, ignoreCase = true) }) return town
            }
        }
        return null
    }

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
                    return namedLocation(context, currentLocationItem(fix))
                }
            }
        }
        return getWidgetLocation(context, prefs)?.let { namedLocation(context, it) }
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
