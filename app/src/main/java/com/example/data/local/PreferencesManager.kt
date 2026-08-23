package io.github.tychomagnetic.metterweather.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.tychomagnetic.metterweather.data.model.LocationItem
import io.github.tychomagnetic.metterweather.data.model.MapManifestCache
import io.github.tychomagnetic.metterweather.data.model.PressureUnit
import io.github.tychomagnetic.metterweather.data.model.TemperatureUnit
import io.github.tychomagnetic.metterweather.data.model.ThemeMode
import io.github.tychomagnetic.metterweather.data.model.ForecastSource
import io.github.tychomagnetic.metterweather.data.model.WeatherDataSource
import io.github.tychomagnetic.metterweather.data.model.WeatherReport
import io.github.tychomagnetic.metterweather.data.model.WidgetRefreshInterval
import io.github.tychomagnetic.metterweather.data.model.WindSpeedUnit
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val securePrefs: SharedPreferences = context.applicationContext.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
    private val secureValueStore = SecureValueStore(securePrefs, allowPlaintextFallback = isRobolectricRuntime())
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    private val locationListAdapter = moshi.adapter<List<LocationItem>>(
        Types.newParameterizedType(List::class.java, LocationItem::class.java)
    )
    private val locationAdapter = moshi.adapter(LocationItem::class.java)
    private val weatherReportAdapter = moshi.adapter(WeatherReport::class.java)
    private val mapManifestAdapter = moshi.adapter(MapManifestCache::class.java)

    private val _apiKeyFlow = MutableStateFlow(getApiKey())
    val apiKeyFlow: StateFlow<String> = _apiKeyFlow.asStateFlow()

    private val _clientSecretFlow = MutableStateFlow(getClientSecret())
    val clientSecretFlow: StateFlow<String> = _clientSecretFlow.asStateFlow()

    private val _selectedLocationFlow = MutableStateFlow(getSelectedLocation())
    val selectedLocationFlow: StateFlow<LocationItem> = _selectedLocationFlow.asStateFlow()

    private val _favoriteLocationsFlow = MutableStateFlow(getFavoriteLocations())
    val favoriteLocationsFlow: StateFlow<List<LocationItem>> = _favoriteLocationsFlow.asStateFlow()

    private val _tempUnitFlow = MutableStateFlow(getTemperatureUnit())
    val tempUnitFlow: StateFlow<TemperatureUnit> = _tempUnitFlow.asStateFlow()

    private val _windUnitFlow = MutableStateFlow(getWindSpeedUnit())
    val windUnitFlow: StateFlow<WindSpeedUnit> = _windUnitFlow.asStateFlow()

    private val _pressureUnitFlow = MutableStateFlow(getPressureUnit())
    val pressureUnitFlow: StateFlow<PressureUnit> = _pressureUnitFlow.asStateFlow()

    private val _themeModeFlow = MutableStateFlow(getThemeMode())
    val themeModeFlow: StateFlow<ThemeMode> = _themeModeFlow.asStateFlow()

    private val _useMetOfficeSourceFlow = MutableStateFlow(isMetOfficePreferred())
    val useMetOfficeSourceFlow: StateFlow<Boolean> = _useMetOfficeSourceFlow.asStateFlow()

    private val _forecastSourceFlow = MutableStateFlow(getForecastSource())
    val forecastSourceFlow: StateFlow<ForecastSource> = _forecastSourceFlow.asStateFlow()

    private val _bpfApiKeyFlow = MutableStateFlow(getBpfApiKey())
    val bpfApiKeyFlow: StateFlow<String> = _bpfApiKeyFlow.asStateFlow()

    private val _widgetRefreshIntervalFlow = MutableStateFlow(getWidgetRefreshInterval())
    val widgetRefreshIntervalFlow: StateFlow<WidgetRefreshInterval> = _widgetRefreshIntervalFlow.asStateFlow()

    private val _widgetGpsEnabledFlow = MutableStateFlow(isWidgetGpsEnabled())
    val widgetGpsEnabledFlow: StateFlow<Boolean> = _widgetGpsEnabledFlow.asStateFlow()

    private val _widgetFixedLocationFlow = MutableStateFlow(getWidgetFixedLocation())
    val widgetFixedLocationFlow: StateFlow<LocationItem> = _widgetFixedLocationFlow.asStateFlow()

    fun isMetOfficePreferred(): Boolean {
        return prefs.getBoolean(KEY_USE_MET_OFFICE, true)
    }

    fun setMetOfficePreferred(useMetOffice: Boolean) {
        prefs.edit().putBoolean(KEY_USE_MET_OFFICE, useMetOffice).apply()
        _useMetOfficeSourceFlow.value = useMetOffice
        setForecastSource(if (useMetOffice) ForecastSource.MET_OFFICE_SPOT else ForecastSource.OPEN_METEO)
    }

    fun getForecastSource(): ForecastSource {
        val saved = prefs.getString(KEY_FORECAST_SOURCE, null)
        if (saved != null) {
            return try {
                ForecastSource.valueOf(saved)
            } catch (_: Exception) {
                ForecastSource.MET_OFFICE_SPOT
            }
        }
        // Migration for existing installs using the previous two-source switch.
        return if (prefs.getBoolean(KEY_USE_MET_OFFICE, true)) ForecastSource.MET_OFFICE_SPOT else ForecastSource.OPEN_METEO
    }

    fun shouldShowApiOnboarding(): Boolean {
        return getApiKey().isBlank() &&
            !prefs.getBoolean(KEY_API_ONBOARDING_DISMISSED, false)
    }

    fun markApiOnboardingDismissed() {
        prefs.edit().putBoolean(KEY_API_ONBOARDING_DISMISSED, true).apply()
    }

    fun setForecastSource(source: ForecastSource) {
        prefs.edit()
            .putString(KEY_FORECAST_SOURCE, source.name)
            .putBoolean(KEY_USE_MET_OFFICE, source != ForecastSource.OPEN_METEO)
            .apply()
        _forecastSourceFlow.value = source
        _useMetOfficeSourceFlow.value = source != ForecastSource.OPEN_METEO
    }

    fun getApiKey(): String {
        return getSecureValue(KEY_MET_OFFICE_API_KEY)
    }

    fun setApiKey(key: String) {
        setSecureValue(KEY_MET_OFFICE_API_KEY, key.trim())
        _apiKeyFlow.value = key.trim()
    }

    fun getClientSecret(): String {
        return getSecureValue(KEY_MET_OFFICE_SECRET)
    }

    fun setClientSecret(secret: String) {
        setSecureValue(KEY_MET_OFFICE_SECRET, secret.trim())
        _clientSecretFlow.value = secret.trim()
    }

    fun getBpfApiKey(): String = getSecureValue(KEY_MET_OFFICE_BPF_API_KEY)

    fun setBpfApiKey(key: String) {
        setSecureValue(KEY_MET_OFFICE_BPF_API_KEY, key.trim())
        _bpfApiKeyFlow.value = key.trim()
    }

    fun getMapImagesApiKey(): String = getSecureValue(KEY_MET_OFFICE_MAP_IMAGES_API_KEY)

    fun setMapImagesApiKey(key: String) {
        setSecureValue(KEY_MET_OFFICE_MAP_IMAGES_API_KEY, key.trim())
    }

    fun getMapManifestCache(): MapManifestCache? {
        val json = prefs.getString(KEY_MAP_IMAGES_MANIFEST, null) ?: return null
        return runCatching { mapManifestAdapter.fromJson(json) }.getOrNull()
    }

    fun setMapManifestCache(cache: MapManifestCache) {
        prefs.edit().putString(KEY_MAP_IMAGES_MANIFEST, mapManifestAdapter.toJson(cache)).apply()
    }

    fun clearMapManifestCache() {
        prefs.edit().remove(KEY_MAP_IMAGES_MANIFEST).apply()
    }

    fun getSelectedMapOrderId(): String = prefs.getString(KEY_MAP_IMAGES_ORDER_ID, "") ?: ""

    fun setSelectedMapOrderId(orderId: String) {
        prefs.edit().putString(KEY_MAP_IMAGES_ORDER_ID, orderId).apply()
    }

    fun getSelectedMapLayerId(): String =
        prefs.getString(KEY_MAP_IMAGES_LAYER_ID, "total_precipitation_rate") ?: "total_precipitation_rate"

    fun setSelectedMapLayerId(layerId: String) {
        prefs.edit().putString(KEY_MAP_IMAGES_LAYER_ID, layerId).apply()
    }

    fun getSelectedLocation(): LocationItem {
        val json = prefs.getString(KEY_SELECTED_LOCATION, null)
        if (json != null) {
            try {
                val item = locationAdapter.fromJson(json)
                if (item != null) return item
            } catch (_: Exception) {
            }
        }
        return LocationItem.DEFAULT_LOCATIONS.first()
    }

    fun setSelectedLocation(location: LocationItem) {
        val json = locationAdapter.toJson(location)
        prefs.edit().putString(KEY_SELECTED_LOCATION, json).apply()
        _selectedLocationFlow.value = location
    }

    fun getFavoriteLocations(): List<LocationItem> {
        val json = prefs.getString(KEY_FAVORITE_LOCATIONS, null)
        if (json != null) {
            try {
                val list = locationListAdapter.fromJson(json)
                if (!list.isNullOrEmpty()) return list
            } catch (_: Exception) {
            }
        }
        return LocationItem.DEFAULT_LOCATIONS.take(5)
    }

    fun saveFavoriteLocations(list: List<LocationItem>) {
        val json = locationListAdapter.toJson(list)
        prefs.edit().putString(KEY_FAVORITE_LOCATIONS, json).apply()
        _favoriteLocationsFlow.value = list
    }

    fun addOrToggleFavorite(location: LocationItem) {
        val current = getFavoriteLocations().toMutableList()
        val index = current.indexOfFirst {
            it.id == location.id || (Math.abs(it.latitude - location.latitude) < 0.01 && Math.abs(it.longitude - location.longitude) < 0.01)
        }
        if (index >= 0) {
            current.removeAt(index)
        } else {
            current.add(0, location.copy(isFavorite = true))
        }
        saveFavoriteLocations(current)
    }

    fun getTemperatureUnit(): TemperatureUnit {
        val name = prefs.getString(KEY_TEMP_UNIT, TemperatureUnit.CELSIUS.name)
        return try {
            TemperatureUnit.valueOf(name ?: TemperatureUnit.CELSIUS.name)
        } catch (_: Exception) {
            TemperatureUnit.CELSIUS
        }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        prefs.edit().putString(KEY_TEMP_UNIT, unit.name).apply()
        _tempUnitFlow.value = unit
    }

    fun getThemeMode(): ThemeMode {
        val name = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(name ?: ThemeMode.SYSTEM.name)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeModeFlow.value = mode
    }

    fun getWindSpeedUnit(): WindSpeedUnit {
        val name = prefs.getString(KEY_WIND_UNIT, WindSpeedUnit.MPH.name)
        return try {
            WindSpeedUnit.valueOf(name ?: WindSpeedUnit.MPH.name)
        } catch (_: Exception) {
            WindSpeedUnit.MPH
        }
    }

    fun setWindSpeedUnit(unit: WindSpeedUnit) {
        prefs.edit().putString(KEY_WIND_UNIT, unit.name).apply()
        _windUnitFlow.value = unit
    }

    fun getPressureUnit(): PressureUnit {
        val name = prefs.getString(KEY_PRESSURE_UNIT, PressureUnit.HPA.name)
        return try {
            PressureUnit.valueOf(name ?: PressureUnit.HPA.name)
        } catch (_: Exception) {
            PressureUnit.HPA
        }
    }

    fun setPressureUnit(unit: PressureUnit) {
        prefs.edit().putString(KEY_PRESSURE_UNIT, unit.name).apply()
        _pressureUnitFlow.value = unit
    }

    fun getCachedWeatherReport(): WeatherReport? {
        val json = prefs.getString(KEY_CACHED_WEATHER_REPORT, null) ?: return null
        return try {
            weatherReportAdapter.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }

    fun setCachedWeatherReport(report: WeatherReport) {
        try {
            val json = weatherReportAdapter.toJson(report)
            prefs.edit().putString(KEY_CACHED_WEATHER_REPORT, json).apply()
        } catch (_: Exception) {
        }
    }

    fun getFreshCachedBpfWeatherReport(
        location: LocationItem,
        maxAgeMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): WeatherReport? {
        val report = getCachedBpfWeatherReport(location) ?: return null
        val ageMillis = nowMillis - report.fetchedAtMillis
        return report.takeIf { ageMillis in 0..maxAgeMillis }
    }

    fun getCachedBpfWeatherReport(location: LocationItem): WeatherReport? {
        val locationCacheKey = bpfCacheKey(location)
        // Only entries written into the current verified, per-location cache are
        // eligible. Older reports did not retain enough server-coordinate data
        // to prove they belong to the requested location.
        val json = prefs.getString(locationCacheKey, null) ?: return null
        val report = try {
            weatherReportAdapter.fromJson(json)
        } catch (_: Exception) {
            null
        } ?: return null

        val sameLocation = kotlin.math.abs(report.location.latitude - location.latitude) < 0.0001 &&
            kotlin.math.abs(report.location.longitude - location.longitude) < 0.0001
        return report.takeIf {
            it.dataSource == WeatherDataSource.MET_OFFICE_BPF &&
                sameLocation
        }
    }

    fun setCachedBpfWeatherReport(report: WeatherReport) {
        if (report.dataSource != WeatherDataSource.MET_OFFICE_BPF) return
        try {
            val json = weatherReportAdapter.toJson(report)
            prefs.edit().putString(bpfCacheKey(report.location), json).apply()
        } catch (_: Exception) {
        }
    }

    private fun bpfCacheKey(location: LocationItem): String =
        KEY_CACHED_BPF_LOCATION_PREFIX + String.format(
            Locale.US,
            "%.4f_%.4f",
            location.latitude,
            location.longitude
        )

    fun getCachedWidgetWeatherReport(): WeatherReport? {
        val json = prefs.getString(KEY_CACHED_WIDGET_WEATHER_REPORT, null) ?: return null
        return try {
            weatherReportAdapter.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }

    fun setCachedWidgetWeatherReport(report: WeatherReport) {
        try {
            val json = weatherReportAdapter.toJson(report)
            prefs.edit().putString(KEY_CACHED_WIDGET_WEATHER_REPORT, json).apply()
        } catch (_: Exception) {
        }
    }

    fun getCachedWidgetGpsLocation(): LocationItem? {
        val json = prefs.getString(KEY_CACHED_WIDGET_GPS_LOCATION, null) ?: return null
        return try {
            locationAdapter.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }

    fun setCachedWidgetGpsLocation(location: LocationItem) {
        if (!location.isCurrentLocation) return
        try {
            prefs.edit()
                .putString(KEY_CACHED_WIDGET_GPS_LOCATION, locationAdapter.toJson(location))
                .apply()
        } catch (_: Exception) {
        }
    }

    fun getWidgetPageOffset(): Int {
        return prefs.getInt(KEY_WIDGET_PAGE_OFFSET, 0)
    }

    fun setWidgetPageOffset(offset: Int) {
        prefs.edit().putInt(KEY_WIDGET_PAGE_OFFSET, offset.coerceAtLeast(0)).apply()
    }

    fun getWidgetRefreshInterval(): WidgetRefreshInterval {
        val name = prefs.getString(KEY_WIDGET_REFRESH_INTERVAL, WidgetRefreshInterval.ONE_HOUR.name)
        return try {
            WidgetRefreshInterval.valueOf(name ?: WidgetRefreshInterval.ONE_HOUR.name)
        } catch (_: Exception) {
            WidgetRefreshInterval.ONE_HOUR
        }
    }

    fun setWidgetRefreshInterval(interval: WidgetRefreshInterval) {
        prefs.edit().putString(KEY_WIDGET_REFRESH_INTERVAL, interval.name).apply()
        _widgetRefreshIntervalFlow.value = interval
    }

    fun isWidgetGpsEnabled(): Boolean {
        // Keep first-run setup permission-free. Users can opt into approximate
        // GPS explicitly from widget settings or the location picker.
        return prefs.getBoolean(KEY_WIDGET_USE_GPS, false)
    }

    fun setWidgetGpsEnabled(useGps: Boolean) {
        prefs.edit().putBoolean(KEY_WIDGET_USE_GPS, useGps).apply()
        _widgetGpsEnabledFlow.value = useGps
    }

    fun getWidgetFixedLocation(): LocationItem {
        val json = prefs.getString(KEY_WIDGET_FIXED_LOCATION, null)
        if (json != null) {
            try {
                val item = locationAdapter.fromJson(json)
                if (item != null) return item
            } catch (_: Exception) {
            }
        }
        // Migration for installs that pre-date the dedicated widget location.
        // Freeze the current app location once instead of dynamically following
        // every later location selected in the main app.
        val initialFixedLocation = getSelectedLocation()
        prefs.edit()
            .putString(KEY_WIDGET_FIXED_LOCATION, locationAdapter.toJson(initialFixedLocation))
            .apply()
        return initialFixedLocation
    }

    fun setWidgetFixedLocation(location: LocationItem) {
        val json = locationAdapter.toJson(location)
        prefs.edit().putString(KEY_WIDGET_FIXED_LOCATION, json).apply()
        _widgetFixedLocationFlow.value = location
    }

    private fun getSecureValue(key: String): String {
        secureValueStore.get(key)?.let { return it }

        // Migrate keys saved by older versions out of the ordinary preferences
        // file. The legacy value is removed immediately so a subsequent backup
        // cannot carry it forward.
        val legacyValue = prefs.getString(key, null)
        if (legacyValue != null) {
            prefs.edit().remove(key).apply()
            if (legacyValue.isBlank()) return ""
            return runCatching {
                secureValueStore.put(key, legacyValue)
                secureValueStore.get(key)
            }.getOrNull().orEmpty()
        }
        return ""
    }

    private fun setSecureValue(key: String, value: String) {
        if (value.isBlank()) secureValueStore.remove(key) else secureValueStore.put(key, value)
        prefs.edit().remove(key).apply()
    }

    companion object {
        private const val PREFS_NAME = "met_office_weather_prefs"
        private const val SECURE_PREFS_NAME = "met_office_weather_secure"
        private const val KEY_MET_OFFICE_API_KEY = "met_office_api_key"
        private const val KEY_MET_OFFICE_SECRET = "met_office_secret"
        private const val KEY_MET_OFFICE_BPF_API_KEY = "met_office_bpf_api_key"
        private const val KEY_MET_OFFICE_MAP_IMAGES_API_KEY = "met_office_map_images_api_key"
        private const val KEY_MAP_IMAGES_MANIFEST = "map_images_manifest_v1"
        private const val KEY_MAP_IMAGES_ORDER_ID = "map_images_order_id"
        private const val KEY_MAP_IMAGES_LAYER_ID = "map_images_layer_id"
        private const val KEY_SELECTED_LOCATION = "selected_location"
        private const val KEY_FAVORITE_LOCATIONS = "favorite_locations"
        private const val KEY_TEMP_UNIT = "temp_unit"
        private const val KEY_WIND_UNIT = "wind_unit"
        private const val KEY_PRESSURE_UNIT = "pressure_unit"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_USE_MET_OFFICE = "use_met_office_source"
        private const val KEY_FORECAST_SOURCE = "forecast_source"
        private const val KEY_API_ONBOARDING_DISMISSED = "api_onboarding_dismissed"
        private const val KEY_CACHED_WEATHER_REPORT = "cached_weather_report"
        private const val KEY_CACHED_WIDGET_WEATHER_REPORT = "cached_widget_weather_report"
        private const val KEY_CACHED_WIDGET_GPS_LOCATION = "cached_widget_gps_location"
        // v4 invalidates reports written before BPF probability completeness was enforced.
        private const val KEY_CACHED_BPF_LOCATION_PREFIX = "cached_bpf_location_v4_"
        private const val KEY_WIDGET_PAGE_OFFSET = "widget_page_offset"
        private const val KEY_WIDGET_REFRESH_INTERVAL = "widget_refresh_interval"
        private const val KEY_WIDGET_USE_GPS = "widget_use_gps"
        private const val KEY_WIDGET_FIXED_LOCATION = "widget_fixed_location"
    }
}

/**
 * Encrypts API credentials with an AES/GCM key held in Android Keystore. A
 * plaintext fallback is retained only for environments without an Android
 * Keystore (for example JVM/Robolectric tests); the containing preferences file
 * is excluded from Android backup regardless.
 */
private class SecureValueStore(
    private val prefs: SharedPreferences,
    private val allowPlaintextFallback: Boolean
) {

    fun get(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        if (!stored.startsWith(ENCRYPTED_PREFIX)) {
            // Upgrade any temporary plaintext fallback when Keystore becomes
            // available again.
            return runCatching {
                put(key, stored)
                stored
            }.fold(
                onSuccess = { it },
                onFailure = {
                    // Never leave a plaintext credential behind on a real device.
                    prefs.edit().remove(key).apply()
                    null
                }
            )
        }
        return runCatching { decrypt(stored.removePrefix(ENCRYPTED_PREFIX)) }.getOrElse {
            prefs.edit().remove(key).apply()
            null
        }
    }

    fun put(key: String, value: String) {
        val stored = runCatching { ENCRYPTED_PREFIX + encrypt(value) }.getOrElse {
            if (allowPlaintextFallback) {
                value
            } else {
                throw IllegalStateException("Android Keystore is unavailable; refusing to store an API credential in plaintext", it)
            }
        }
        prefs.edit().putString(key, stored).apply()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(byteArrayOf(iv.size.toByte()) + iv + ciphertext, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val encoded = Base64.decode(value, Base64.NO_WRAP)
        require(encoded.isNotEmpty())
        val ivSize = encoded[0].toInt() and 0xFF
        require(ivSize in 12..16 && encoded.size > ivSize + 1)
        val iv = encoded.copyOfRange(1, ivSize + 1)
        val ciphertext = encoded.copyOfRange(ivSize + 1, encoded.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "met_office_weather_api_credentials"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ENCRYPTED_PREFIX = "enc:v1:"
        private const val TAG_LENGTH_BITS = 128
    }
}

private fun isRobolectricRuntime(): Boolean =
    Build.FINGERPRINT.equals("robolectric", ignoreCase = true) ||
        Build.MODEL.equals("robolectric", ignoreCase = true)
