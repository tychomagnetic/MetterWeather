package io.github.tychomagnetic.metterweather.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.tychomagnetic.metterweather.data.local.PreferencesManager
import io.github.tychomagnetic.metterweather.data.model.ApiDebugInfo
import io.github.tychomagnetic.metterweather.data.model.ApiDiagnosticSource
import io.github.tychomagnetic.metterweather.data.model.CoordinateTestResult
import io.github.tychomagnetic.metterweather.data.model.DailyForecastItem
import io.github.tychomagnetic.metterweather.data.model.ForecastSource
import io.github.tychomagnetic.metterweather.data.model.LocationItem
import io.github.tychomagnetic.metterweather.data.model.PressureUnit
import io.github.tychomagnetic.metterweather.data.model.TemperatureUnit
import io.github.tychomagnetic.metterweather.data.model.ThemeMode
import io.github.tychomagnetic.metterweather.data.model.WeatherDataSource
import io.github.tychomagnetic.metterweather.data.model.WeatherReport
import io.github.tychomagnetic.metterweather.data.model.WidgetRefreshInterval
import io.github.tychomagnetic.metterweather.data.model.WindSpeedUnit
import io.github.tychomagnetic.metterweather.data.util.WeatherClockUtils
import io.github.tychomagnetic.metterweather.data.repository.ApiKeyTestResult
import io.github.tychomagnetic.metterweather.data.repository.MapImagesRepository
import io.github.tychomagnetic.metterweather.data.repository.WeatherRepository
import io.github.tychomagnetic.metterweather.widget.HourlyForecastWidget
import io.github.tychomagnetic.metterweather.widget.WidgetRefreshManager
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.TimeZone

data class WeatherUiState(
    val weatherReport: WeatherReport? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val selectedLocation: LocationItem = LocationItem.DEFAULT_LOCATIONS.first(),
    val favoriteLocations: List<LocationItem> = emptyList(),
    val searchResults: List<LocationItem> = emptyList(),
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val apiKey: String = "",
    val bpfApiKey: String = "",
    val mapImagesApiKey: String = "",
    val clientSecret: String = "",
    val useMetOfficeSource: Boolean = true,
    val forecastSource: ForecastSource = ForecastSource.MET_OFFICE_SPOT,
    val tempUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windUnit: WindSpeedUnit = WindSpeedUnit.MPH,
    val pressureUnit: PressureUnit = PressureUnit.HPA,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val widgetRefreshInterval: WidgetRefreshInterval = WidgetRefreshInterval.ONE_HOUR,
    val widgetUseGps: Boolean = false,
    val widgetFixedLocation: LocationItem = LocationItem.DEFAULT_LOCATIONS.first(),
    val isWidgetLocationPickerOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val openApiSettingsOnOpen: Boolean = false,
    val isMapImagesOpen: Boolean = false,
    val isApiKeyDialogOpen: Boolean = false,
    val isFirstRunApiPromptVisible: Boolean = false,
    val isLocationSheetOpen: Boolean = false,
    val isUnitsDialogOpen: Boolean = false,
    val apiKeyTestStatus: ApiKeyTestResult? = null,
    val isTestingApiKey: Boolean = false,
    val bpfApiKeyTestStatus: ApiKeyTestResult? = null,
    val isTestingBpfApiKey: Boolean = false,
    val mapImagesApiKeyTestStatus: ApiKeyTestResult? = null,
    val isTestingMapImagesApiKey: Boolean = false,
    val selectedDayIndex: Int = 0,
    val selectedDayForDetail: DailyForecastItem? = null,
    val isDayDetailSheetOpen: Boolean = false,
    val debugInfo: ApiDebugInfo? = null,
    val selectedApiDiagnosticSource: ApiDiagnosticSource = ApiDiagnosticSource.MET_OFFICE_SPOT,
    val apiDiagnosticResults: Map<ApiDiagnosticSource, ApiDebugInfo> = emptyMap(),
    val isRunningApiDiagnostic: Boolean = false,
    val isDebugSheetOpen: Boolean = false,
    val customLatInput: String = "51.5074",
    val customLonInput: String = "-0.1278",
    val coordinateTestResult: CoordinateTestResult? = null,
    val isTestingCoordinates: Boolean = false,
    val rawGeocodingQueryInput: String = "",
    val rawGeocodingResultJson: String? = null,
    val rawGeocodingLocations: List<LocationItem> = emptyList(),
    val isTestingGeocoding: Boolean = false,
    val clockTickMillis: Long = System.currentTimeMillis()
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application.applicationContext)
    private val repository = WeatherRepository(preferencesManager)
    private val mapImagesRepository = MapImagesRepository(application.applicationContext, preferencesManager)
    private val bpfCacheMaxAgeMillis = 2L * 60L * 60L * 1000L
    private val refreshDisplayMaxAgeMillis = 24L * 60L * 60L * 1000L
    private val automaticRefreshMaxAgeMillis = 2L * 60L * 60L * 1000L
    private val automaticRefreshRetryMillis = 15L * 60L * 1000L

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var geocodeTestJob: Job? = null
    private var weatherLoadJob: Job? = null
    private var lastAutomaticRefreshAttemptMillis: Long = 0L

    init {
        val initialLocation = preferencesManager.getSelectedLocation()
        val initialFavorites = preferencesManager.getFavoriteLocations()
        val isFav = isFavoriteLocation(initialLocation, initialFavorites)
        val initialSelectedLocation = initialLocation.copy(isFavorite = isFav)
        val initialKey = preferencesManager.getApiKey()
        val initialBpfKey = preferencesManager.getBpfApiKey()
        val initialMapImagesKey = preferencesManager.getMapImagesApiKey()
        val initialSecret = preferencesManager.getClientSecret()
        val initialForecastSource = preferencesManager.getForecastSource()
        val initialTempUnit = preferencesManager.getTemperatureUnit()
        val initialWindUnit = preferencesManager.getWindSpeedUnit()
        val initialPressureUnit = preferencesManager.getPressureUnit()
        val initialThemeMode = preferencesManager.getThemeMode()
        val savedRefreshInterval = preferencesManager.getWidgetRefreshInterval()
        val initialRefreshInterval = if (savedRefreshInterval == WidgetRefreshInterval.OFF) {
            WidgetRefreshInterval.OFF
        } else {
            WidgetRefreshInterval.ONE_HOUR
        }
        if (savedRefreshInterval != initialRefreshInterval) {
            preferencesManager.setWidgetRefreshInterval(initialRefreshInterval)
        }
        val initialWidgetUseGps = preferencesManager.isWidgetGpsEnabled()
        val initialWidgetFixedLocation = preferencesManager.getWidgetFixedLocation()

        _uiState.update {
            it.copy(
                selectedLocation = initialSelectedLocation,
                favoriteLocations = initialFavorites,
                apiKey = initialKey,
                bpfApiKey = initialBpfKey,
                mapImagesApiKey = initialMapImagesKey,
                clientSecret = initialSecret,
                useMetOfficeSource = initialForecastSource != ForecastSource.OPEN_METEO,
                forecastSource = initialForecastSource,
                tempUnit = initialTempUnit,
                windUnit = initialWindUnit,
                pressureUnit = initialPressureUnit,
                themeMode = initialThemeMode,
                widgetRefreshInterval = initialRefreshInterval,
                widgetUseGps = initialWidgetUseGps,
                widgetFixedLocation = initialWidgetFixedLocation,
                isFirstRunApiPromptVisible = preferencesManager.shouldShowApiOnboarding(),
                customLatInput = String.format(Locale.US, "%.4f", initialLocation.latitude),
                customLonInput = String.format(Locale.US, "%.4f", initialLocation.longitude)
            )
        }

        // Ensure background auto-refresh is active based on saved preference (default: 1 hour)
        WidgetRefreshManager.scheduleAutoRefresh(application.applicationContext, initialRefreshInterval)

        viewModelScope.launch {
            repository.debugInfo.collect { debugInfo ->
                _uiState.update { it.copy(debugInfo = debugInfo) }
            }
        }

        loadWeather(initialSelectedLocation)
    }

    private fun isFavoriteLocation(location: LocationItem, favorites: List<LocationItem>): Boolean {
        return favorites.any {
            it.id == location.id ||
            (it.name.equals(location.name, ignoreCase = true) && Math.abs(it.latitude - location.latitude) < 0.05 && Math.abs(it.longitude - location.longitude) < 0.05) ||
            (Math.abs(it.latitude - location.latitude) < 0.01 && Math.abs(it.longitude - location.longitude) < 0.01)
        }
    }

    fun onVisibleTimeCheck(nowMillis: Long = System.currentTimeMillis()) {
        val currentState = _uiState.value
        val currentReport = currentState.weatherReport
        if (currentReport != null) {
            val update = WeatherClockUtils.advance(currentReport, currentState.selectedDayIndex, nowMillis)
            _uiState.update {
                it.copy(
                    weatherReport = update.report,
                    selectedDayIndex = update.selectedDayIndex,
                    clockTickMillis = nowMillis
                )
            }

            val stale = nowMillis - currentReport.fetchedAtMillis >= automaticRefreshMaxAgeMillis
            val retryAllowed = nowMillis - lastAutomaticRefreshAttemptMillis >= automaticRefreshRetryMillis
            if (stale && retryAllowed && weatherLoadJob?.isActive != true) {
                lastAutomaticRefreshAttemptMillis = nowMillis
                loadWeather(currentState.selectedLocation, isRefresh = true, useFreshCache = false)
            }
        } else {
            _uiState.update { it.copy(clockTickMillis = nowMillis) }
        }
    }

    fun loadWeather(
        location: LocationItem = _uiState.value.selectedLocation,
        isRefresh: Boolean = false,
        useFreshCache: Boolean = !isRefresh
    ) {
        weatherLoadJob?.cancel()
        weatherLoadJob = viewModelScope.launch {
            val requestedSource = _uiState.value.forecastSource
            val isFav = isFavoriteLocation(location, _uiState.value.favoriteLocations)
            val updatedLocation = location.copy(isFavorite = isFav)
            var cachedReportDisplayed = false

            if (requestedSource != ForecastSource.MET_OFFICE_BPF) {
                val cachedReport = preferencesManager.getCachedWeatherReport()?.takeIf {
                    matchesForecast(it, updatedLocation, requestedSource)
                }
                if (cachedReport != null) {
                    val cachedAgeMillis = System.currentTimeMillis() - cachedReport.fetchedAtMillis
                    if (useFreshCache && cachedAgeMillis in 0..automaticRefreshMaxAgeMillis) {
                        applyWeatherReport(cachedReport.copy(location = updatedLocation), updatedLocation)
                        return@launch
                    }

                    if (cachedAgeMillis in 0..refreshDisplayMaxAgeMillis) {
                        _uiState.update {
                            it.copy(
                                weatherReport = cachedReport.copy(location = updatedLocation),
                                selectedLocation = updatedLocation,
                                isLoading = false,
                                isRefreshing = true,
                                errorMessage = null
                            )
                        }
                        cachedReportDisplayed = true
                    }
                }
            }

            if (useFreshCache && requestedSource == ForecastSource.MET_OFFICE_BPF) {
                val cachedReport = preferencesManager.getCachedBpfWeatherReport(updatedLocation)
                if (cachedReport != null) {
                    val cachedAgeMillis = System.currentTimeMillis() - cachedReport.fetchedAtMillis
                    if (cachedAgeMillis in 0..bpfCacheMaxAgeMillis) {
                        applyWeatherReport(cachedReport.copy(location = updatedLocation), updatedLocation)
                        return@launch
                    }

                    if (cachedAgeMillis in 0..refreshDisplayMaxAgeMillis) {
                        _uiState.update {
                            it.copy(
                                weatherReport = cachedReport.copy(location = updatedLocation),
                                selectedLocation = updatedLocation,
                                isLoading = false,
                                isRefreshing = true,
                                errorMessage = null
                            )
                        }
                        showRefreshingToast()
                        cachedReportDisplayed = true
                    }
                }
            }

            if (!cachedReportDisplayed) {
                if (isRefresh) {
                    val currentState = _uiState.value
                    val visibleReport = currentState.weatherReport
                    val visibleReportMatchesLocation = visibleReport != null &&
                        matchesForecast(visibleReport, updatedLocation, requestedSource)
                    val visibleReportAgeMillis = visibleReport?.let {
                        System.currentTimeMillis() - it.fetchedAtMillis
                    }
                    val keepVisibleReport = visibleReportMatchesLocation &&
                        visibleReportAgeMillis != null &&
                        visibleReportAgeMillis in 0..refreshDisplayMaxAgeMillis
                    _uiState.update {
                        it.copy(
                            weatherReport = visibleReport.takeIf { keepVisibleReport },
                            isLoading = !keepVisibleReport,
                            isRefreshing = true,
                            errorMessage = null
                        )
                    }
                    showRefreshingToast()
                } else {
                    _uiState.update {
                        it.copy(weatherReport = null, selectedLocation = updatedLocation,
                            isLoading = true, errorMessage = null)
                    }
                }
            }

            val result = repository.getWeatherReport(updatedLocation)
            result.onSuccess { report ->
                showFallbackToastIfNeeded(requestedSource, report)
                applyWeatherReport(report, updatedLocation)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = error.localizedMessage ?: "Unable to fetch forecast. Please check your connection."
                    )
                }
            }
        }
    }

    private fun showRefreshingToast() {
        Toast.makeText(
            getApplication<Application>().applicationContext,
            "Getting latest weather",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showFallbackToastIfNeeded(
        requestedSource: ForecastSource,
        report: WeatherReport
    ) {
        if (
            requestedSource == ForecastSource.MET_OFFICE_BPF &&
            report.dataSource == WeatherDataSource.MET_OFFICE_BPF &&
            report.partialFallbackSource == WeatherDataSource.MET_OFFICE_DATAHUB
        ) {
            Toast.makeText(
                getApplication<Application>().applicationContext,
                "BPF forecast partially unavailable. Missing data filled from Met Office Spot.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val actualSource = report.dataSource
        val requestedSourceWasUsed = when (requestedSource) {
            ForecastSource.MET_OFFICE_BPF -> actualSource == WeatherDataSource.MET_OFFICE_BPF
            ForecastSource.MET_OFFICE_SPOT -> actualSource == WeatherDataSource.MET_OFFICE_DATAHUB ||
                    actualSource == WeatherDataSource.MET_OFFICE_DATAPOINT
            ForecastSource.OPEN_METEO -> actualSource == WeatherDataSource.OPEN_METEO_METEOROLOGICAL
        }
        if (requestedSourceWasUsed) {
            return
        }

        val requestedName = when (requestedSource) {
            ForecastSource.MET_OFFICE_BPF -> "BPF forecast"
            ForecastSource.MET_OFFICE_SPOT -> "Met Office Spot forecast"
            ForecastSource.OPEN_METEO -> "Open-Meteo forecast"
        }
        val fallbackName = when (actualSource) {
            WeatherDataSource.MET_OFFICE_DATAHUB,
            WeatherDataSource.MET_OFFICE_DATAPOINT -> "Met Office Spot"
            WeatherDataSource.OPEN_METEO_METEOROLOGICAL -> "Open-Meteo open data"
            WeatherDataSource.MET_OFFICE_BPF -> return
        }
        Toast.makeText(
            getApplication<Application>().applicationContext,
            "$requestedName unavailable. Falling back to $fallbackName.",
            Toast.LENGTH_LONG
        ).show()
    }

    private suspend fun applyWeatherReport(report: WeatherReport, location: LocationItem) {
        _uiState.update { currentState ->
            val replacementSelectedDayIndex = WeatherClockUtils.selectedDayIndexAfterReplacement(
                previousReport = currentState.weatherReport,
                selectedDayIndex = currentState.selectedDayIndex,
                replacementReport = report
            )
            currentState.copy(
                weatherReport = report,
                selectedLocation = location,
                selectedDayIndex = replacementSelectedDayIndex,
                isLoading = false,
                isRefreshing = false,
                errorMessage = null
            )
        }
        // Moshi serialization of a seven-day report can be sizeable. Keep it
        // off the main thread after publishing the immediately usable UI state.
        withContext(Dispatchers.IO) {
            preferencesManager.setSelectedLocation(location)
            preferencesManager.setCachedWeatherReport(report)
            if (report.dataSource == WeatherDataSource.MET_OFFICE_BPF) {
                preferencesManager.setCachedBpfWeatherReport(report)
            }
        }
    }

    fun selectLocation(location: LocationItem) {
        val isFav = isFavoriteLocation(location, _uiState.value.favoriteLocations)
        val updatedLocation = location.copy(isFavorite = isFav)
        _uiState.update { current ->
            val existingReport = current.weatherReport?.takeIf { report ->
                kotlin.math.abs(report.location.latitude - updatedLocation.latitude) < 0.0001 &&
                    kotlin.math.abs(report.location.longitude - updatedLocation.longitude) < 0.0001
            }
            current.copy(
                weatherReport = existingReport,
                selectedLocation = updatedLocation,
                isLocationSheetOpen = false,
                apiDiagnosticResults = if (
                    current.selectedLocation.latitude != updatedLocation.latitude ||
                    current.selectedLocation.longitude != updatedLocation.longitude
                ) emptyMap() else current.apiDiagnosticResults
            )
        }
        loadWeather(updatedLocation)
    }

    fun searchLocations(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()

        if (query.trim().length < 2) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // Debounce
            _uiState.update { it.copy(isSearching = true) }
            val results = repository.searchLocations(query)
            _uiState.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    fun toggleFavorite(location: LocationItem) {
        preferencesManager.addOrToggleFavorite(location)
        val updatedFavorites = preferencesManager.getFavoriteLocations()
        val isNowFavorite = isFavoriteLocation(_uiState.value.selectedLocation, updatedFavorites)
        _uiState.update {
            it.copy(
                favoriteLocations = updatedFavorites,
                selectedLocation = it.selectedLocation.copy(isFavorite = isNowFavorite)
            )
        }
    }

    fun setGpsLocation(latitude: Double, longitude: Double, detectedName: String? = null) {
        val location = LocationItem(
            id = "gps_current",
            name = detectedName ?: "Current Location",
            region = "GPS",
            country = null,
            latitude = latitude,
            longitude = longitude,
            // A GPS fix represents the device's present location, so its configured
            // timezone is more authoritative than a longitude-only approximation.
            timezone = TimeZone.getDefault().id,
            isCurrentLocation = true
        )
        selectLocation(location)
    }

    fun saveApiKey(apiKey: String, clientSecret: String = ""): Boolean {
        val trimmedKey = apiKey.trim()
        val trimmedSecret = clientSecret.trim()
        try {
            preferencesManager.setApiKey(trimmedKey)
            preferencesManager.setClientSecret(trimmedSecret)
        } catch (_: RuntimeException) {
            showCredentialStorageError()
            return false
        }
        _uiState.update {
            it.copy(
                apiKey = trimmedKey,
                clientSecret = trimmedSecret,
                isApiKeyDialogOpen = false,
                apiKeyTestStatus = null
            )
        }
        // Refresh forecast with new key
        loadWeather(_uiState.value.selectedLocation, isRefresh = true)
        return true
    }

    fun testApiKey(apiKey: String, clientSecret: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingApiKey = true, apiKeyTestStatus = null) }
            val result = repository.testMetOfficeApiKey(apiKey, clientSecret)
            _uiState.update { it.copy(isTestingApiKey = false, apiKeyTestStatus = result) }
        }
    }

    fun saveBpfApiKey(apiKey: String): Boolean {
        val trimmedKey = apiKey.trim()
        try {
            preferencesManager.setBpfApiKey(trimmedKey)
        } catch (_: RuntimeException) {
            showCredentialStorageError()
            return false
        }
        _uiState.update { it.copy(bpfApiKey = trimmedKey, bpfApiKeyTestStatus = null) }
        if (_uiState.value.forecastSource == ForecastSource.MET_OFFICE_BPF) {
            loadWeather(_uiState.value.selectedLocation, isRefresh = true)
        }
        return true
    }

    fun testBpfApiKey(apiKey: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingBpfApiKey = true, bpfApiKeyTestStatus = null) }
            val result = repository.testBpfApiKey(apiKey)
            _uiState.update { it.copy(isTestingBpfApiKey = false, bpfApiKeyTestStatus = result) }
        }
    }

    fun clearBpfApiKey() {
        preferencesManager.setBpfApiKey("")
        _uiState.update { it.copy(bpfApiKey = "", bpfApiKeyTestStatus = null) }
        if (_uiState.value.forecastSource == ForecastSource.MET_OFFICE_BPF) {
            loadWeather(_uiState.value.selectedLocation, isRefresh = true)
        }
    }

    fun saveMapImagesApiKey(apiKey: String): Boolean {
        val trimmed = apiKey.trim()
        try {
            if (trimmed != _uiState.value.mapImagesApiKey) {
                preferencesManager.clearMapManifestCache()
            }
            preferencesManager.setMapImagesApiKey(trimmed)
        } catch (_: RuntimeException) {
            showCredentialStorageError()
            return false
        }
        _uiState.update { it.copy(mapImagesApiKey = trimmed, mapImagesApiKeyTestStatus = null) }
        return true
    }

    private fun showCredentialStorageError() {
        Toast.makeText(
            getApplication<Application>().applicationContext,
            "Unable to securely save credentials on this device. Nothing was stored.",
            Toast.LENGTH_LONG
        ).show()
    }

    fun testMapImagesApiKey(apiKey: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingMapImagesApiKey = true, mapImagesApiKeyTestStatus = null) }
            val result = mapImagesRepository.testApiKey(apiKey)
            _uiState.update { it.copy(isTestingMapImagesApiKey = false, mapImagesApiKeyTestStatus = result) }
        }
    }

    fun clearMapImagesApiKey() {
        preferencesManager.setMapImagesApiKey("")
        preferencesManager.clearMapManifestCache()
        _uiState.update { it.copy(mapImagesApiKey = "", mapImagesApiKeyTestStatus = null) }
    }

    fun openMapImages() {
        _uiState.update { it.copy(isMapImagesOpen = true) }
    }

    fun closeMapImages() {
        _uiState.update { it.copy(isMapImagesOpen = false) }
    }

    fun openSettings(openApiSettings: Boolean = false) {
        _uiState.update {
            it.copy(
                isSettingsOpen = true,
                openApiSettingsOnOpen = openApiSettings
            )
        }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsOpen = false, openApiSettingsOnOpen = false) }
    }

    fun dismissFirstRunApiPrompt() {
        preferencesManager.markApiOnboardingDismissed()
        _uiState.update { it.copy(isFirstRunApiPromptVisible = false) }
    }

    fun openApiSettingsFromFirstRun() {
        preferencesManager.markApiOnboardingDismissed()
        _uiState.update {
            it.copy(
                isFirstRunApiPromptVisible = false,
                isSettingsOpen = true,
                openApiSettingsOnOpen = true
            )
        }
    }

    fun toggleDataSource(useMetOffice: Boolean) {
        selectForecastSource(if (useMetOffice) ForecastSource.MET_OFFICE_SPOT else ForecastSource.OPEN_METEO)
    }

    fun selectForecastSource(source: ForecastSource) {
        if (source == _uiState.value.forecastSource) {
            // Changing to the already-active source should not spend another
            // API request; use pull-to-refresh for an explicit refresh.
            return
        }
        preferencesManager.setForecastSource(source)
        _uiState.update {
            it.copy(
                forecastSource = source,
                useMetOfficeSource = source != ForecastSource.OPEN_METEO
            )
        }
        loadWeather(
            _uiState.value.selectedLocation,
            isRefresh = true,
            useFreshCache = true
        )
    }

    fun clearApiKey() {
        preferencesManager.setApiKey("")
        preferencesManager.setClientSecret("")
        _uiState.update {
            it.copy(
                apiKey = "",
                clientSecret = "",
                apiKeyTestStatus = null
            )
        }
        if (_uiState.value.forecastSource == ForecastSource.MET_OFFICE_SPOT) {
            loadWeather(_uiState.value.selectedLocation, isRefresh = true)
        }
    }

    fun syncWidgetNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            WidgetRefreshManager.performWidgetRefresh(context)
        }
    }

    fun setWidgetRefreshInterval(interval: WidgetRefreshInterval) {
        val effectiveInterval = if (interval == WidgetRefreshInterval.OFF) {
            WidgetRefreshInterval.OFF
        } else {
            WidgetRefreshInterval.ONE_HOUR
        }
        preferencesManager.setWidgetRefreshInterval(effectiveInterval)
        _uiState.update { it.copy(widgetRefreshInterval = effectiveInterval) }
        val context = getApplication<Application>().applicationContext
        WidgetRefreshManager.scheduleAutoRefresh(context, effectiveInterval)
        if (effectiveInterval != WidgetRefreshInterval.OFF) {
            viewModelScope.launch(Dispatchers.IO) {
                HourlyForecastWidget.updateAllWidgets(context)
            }
        }
    }

    fun setWidgetUseGps(useGps: Boolean) {
        preferencesManager.setWidgetGpsEnabled(useGps)
        _uiState.update { it.copy(widgetUseGps = useGps) }
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            WidgetRefreshManager.performWidgetRefresh(context)
        }
    }

    fun setWidgetFixedLocation(location: LocationItem) {
        preferencesManager.setWidgetFixedLocation(location)
        _uiState.update { it.copy(widgetFixedLocation = location, isWidgetLocationPickerOpen = false) }
        if (!_uiState.value.widgetUseGps) {
            viewModelScope.launch(Dispatchers.IO) {
                val context = getApplication<Application>().applicationContext
                WidgetRefreshManager.performWidgetRefresh(context)
            }
        }
    }

    fun openWidgetLocationPicker() {
        _uiState.update { it.copy(isWidgetLocationPickerOpen = true) }
    }

    fun closeWidgetLocationPicker() {
        _uiState.update { it.copy(isWidgetLocationPickerOpen = false) }
    }

    fun openApiKeyDialog() {
        _uiState.update { it.copy(isApiKeyDialogOpen = true, apiKeyTestStatus = null) }
    }

    fun closeApiKeyDialog() {
        _uiState.update { it.copy(isApiKeyDialogOpen = false, apiKeyTestStatus = null) }
    }

    fun openLocationSheet() {
        _uiState.update { it.copy(isLocationSheetOpen = true, searchQuery = "", searchResults = emptyList()) }
    }

    fun closeLocationSheet() {
        _uiState.update { it.copy(isLocationSheetOpen = false) }
    }

    fun openUnitsDialog() {
        _uiState.update { it.copy(isUnitsDialogOpen = true) }
    }

    fun closeUnitsDialog() {
        _uiState.update { it.copy(isUnitsDialogOpen = false) }
    }

    fun setTemperatureUnit(unit: TemperatureUnit) {
        preferencesManager.setTemperatureUnit(unit)
        _uiState.update { it.copy(tempUnit = unit) }
        viewModelScope.launch(Dispatchers.IO) {
            HourlyForecastWidget.updateAllWidgets(getApplication<Application>().applicationContext)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        preferencesManager.setThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setWindSpeedUnit(unit: WindSpeedUnit) {
        preferencesManager.setWindSpeedUnit(unit)
        _uiState.update { it.copy(windUnit = unit) }
    }

    fun setPressureUnit(unit: PressureUnit) {
        preferencesManager.setPressureUnit(unit)
        _uiState.update { it.copy(pressureUnit = unit) }
    }

    fun selectForecastDay(dayIndex: Int) {
        _uiState.update { it.copy(selectedDayIndex = dayIndex) }
    }

    fun openDayDetailSheet(day: DailyForecastItem, dayIndex: Int) {
        _uiState.update {
            it.copy(
                selectedDayIndex = dayIndex,
                selectedDayForDetail = day,
                isDayDetailSheetOpen = true
            )
        }
    }

    fun closeDayDetailSheet() {
        _uiState.update {
            it.copy(
                isDayDetailSheetOpen = false,
                selectedDayForDetail = null
            )
        }
    }

    fun openDebugSheet() {
        val currentLoc = _uiState.value.selectedLocation
        val diagnosticSource = when (_uiState.value.forecastSource) {
            ForecastSource.MET_OFFICE_SPOT -> ApiDiagnosticSource.MET_OFFICE_SPOT
            ForecastSource.MET_OFFICE_BPF -> ApiDiagnosticSource.MET_OFFICE_BPF
            ForecastSource.OPEN_METEO -> ApiDiagnosticSource.OPEN_METEO
        }
        repository.setDebugPayloadCaptureEnabled(true)
        _uiState.update {
            it.copy(
                isDebugSheetOpen = true,
                selectedApiDiagnosticSource = diagnosticSource,
                customLatInput = String.format(Locale.US, "%.4f", currentLoc.latitude),
                customLonInput = String.format(Locale.US, "%.4f", currentLoc.longitude)
            )
        }
    }

    fun closeDebugSheet() {
        repository.setDebugPayloadCaptureEnabled(false)
        _uiState.update { it.copy(isDebugSheetOpen = false) }
    }

    fun selectApiDiagnosticSource(source: ApiDiagnosticSource) {
        _uiState.update { it.copy(selectedApiDiagnosticSource = source) }
    }

    fun runApiDiagnostic(source: ApiDiagnosticSource = _uiState.value.selectedApiDiagnosticSource) {
        if (_uiState.value.isRunningApiDiagnostic) return
        val location = _uiState.value.selectedLocation
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedApiDiagnosticSource = source,
                    isRunningApiDiagnostic = true
                )
            }
            try {
                val result = repository.runApiDiagnostic(source, location)
                _uiState.update {
                    val stillInspectingSameLocation =
                        kotlin.math.abs(it.selectedLocation.latitude - location.latitude) < 0.0001 &&
                        kotlin.math.abs(it.selectedLocation.longitude - location.longitude) < 0.0001
                    it.copy(
                        apiDiagnosticResults = if (stillInspectingSameLocation) {
                            it.apiDiagnosticResults + (source to result)
                        } else {
                            it.apiDiagnosticResults
                        }
                    )
                }
            } finally {
                _uiState.update { it.copy(isRunningApiDiagnostic = false) }
            }
        }
    }

    fun updateCustomLat(latStr: String) {
        _uiState.update { it.copy(customLatInput = latStr) }
    }

    fun updateCustomLon(lonStr: String) {
        _uiState.update { it.copy(customLonInput = lonStr) }
    }

    fun nudgeCoordinates(latDelta: Double, lonDelta: Double) {
        val currLat = _uiState.value.customLatInput.toDoubleOrNull() ?: _uiState.value.selectedLocation.latitude
        val currLon = _uiState.value.customLonInput.toDoubleOrNull() ?: _uiState.value.selectedLocation.longitude
        val newLat = currLat + latDelta
        val newLon = currLon + lonDelta
        _uiState.update {
            it.copy(
                customLatInput = String.format(Locale.US, "%.4f", newLat),
                customLonInput = String.format(Locale.US, "%.4f", newLon)
            )
        }
        runCoordinateTest(newLat, newLon)
    }

    fun runCoordinateTest(lat: Double? = null, lon: Double? = null) {
        val targetLat = lat ?: _uiState.value.customLatInput.toDoubleOrNull() ?: return
        val targetLon = lon ?: _uiState.value.customLonInput.toDoubleOrNull() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isTestingCoordinates = true, coordinateTestResult = null) }
            val result = repository.testCustomCoordinates(targetLat, targetLon)
            _uiState.update { it.copy(isTestingCoordinates = false, coordinateTestResult = result) }
        }
    }

    fun updateRawGeocodingQuery(query: String) {
        _uiState.update { it.copy(rawGeocodingQueryInput = query) }
        geocodeTestJob?.cancel()
        if (query.trim().isBlank()) {
            _uiState.update { it.copy(rawGeocodingResultJson = null, rawGeocodingLocations = emptyList(), isTestingGeocoding = false) }
            return
        }

        geocodeTestJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(isTestingGeocoding = true) }
            val (json, locations) = repository.searchGeocodingRaw(query)
            _uiState.update {
                it.copy(
                    isTestingGeocoding = false,
                    rawGeocodingResultJson = json,
                    rawGeocodingLocations = locations
                )
            }
        }
    }
}
