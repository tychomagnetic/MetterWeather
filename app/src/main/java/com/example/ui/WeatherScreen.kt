package io.github.tychomagnetic.metterweather.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tychomagnetic.metterweather.data.model.LocationItem
import io.github.tychomagnetic.metterweather.data.util.TimezoneUtils
import io.github.tychomagnetic.metterweather.ui.components.ApiDebugSheet
import io.github.tychomagnetic.metterweather.ui.components.ApiKeyDialog
import io.github.tychomagnetic.metterweather.ui.components.DailyForecastCard
import io.github.tychomagnetic.metterweather.ui.components.DayHourlyDetailSheet
import io.github.tychomagnetic.metterweather.ui.components.HeroWeatherCard
import io.github.tychomagnetic.metterweather.ui.components.HourlyForecastRow
import io.github.tychomagnetic.metterweather.ui.components.LocationSearchSheet
import io.github.tychomagnetic.metterweather.ui.components.UnitSettingsDialog
import io.github.tychomagnetic.metterweather.ui.components.WeatherBackground
import io.github.tychomagnetic.metterweather.ui.components.WeatherMetricsGrid
import io.github.tychomagnetic.metterweather.ui.components.WeatherTopBar
import io.github.tychomagnetic.metterweather.ui.theme.BentoBorder
import io.github.tychomagnetic.metterweather.ui.theme.BentoHero
import io.github.tychomagnetic.metterweather.ui.theme.BentoHeroText
import io.github.tychomagnetic.metterweather.ui.theme.BentoOnPrimary
import io.github.tychomagnetic.metterweather.ui.theme.BentoPurplePrimary
import io.github.tychomagnetic.metterweather.ui.theme.BentoTextPrimary
import io.github.tychomagnetic.metterweather.ui.theme.BentoTextSecondary
import io.github.tychomagnetic.metterweather.ui.theme.MetterErrorBorder
import io.github.tychomagnetic.metterweather.ui.theme.MetterErrorContainer
import io.github.tychomagnetic.metterweather.ui.theme.MetterErrorContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val report = uiState.weatherReport
    val context = LocalContext.current

    if (uiState.isMapImagesOpen) {
        val mapImagesViewModel: MapImagesViewModel = viewModel()
        MapImagesScreen(
            viewModel = mapImagesViewModel,
            onBack = { viewModel.closeMapImages() },
            onConfigureKey = {
                viewModel.closeMapImages()
                viewModel.openSettings()
            },
            modifier = modifier
        )
        return
    }

    if (uiState.isSettingsOpen) {
        SettingsScreen(
            viewModel = viewModel,
            onBack = { viewModel.closeSettings() },
            openApiSettingsOnOpen = uiState.openApiSettingsOnOpen,
            modifier = modifier
        )
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize()
    ) { _ ->
        WeatherBackground(current = report?.current) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                val isSelectedLocFavorite = uiState.favoriteLocations.any {
                    it.id == uiState.selectedLocation.id ||
                    (it.name.equals(uiState.selectedLocation.name, ignoreCase = true) && Math.abs(it.latitude - uiState.selectedLocation.latitude) < 0.05 && Math.abs(it.longitude - uiState.selectedLocation.longitude) < 0.05) ||
                    (Math.abs(it.latitude - uiState.selectedLocation.latitude) < 0.01 && Math.abs(it.longitude - uiState.selectedLocation.longitude) < 0.01)
                } || uiState.selectedLocation.isFavorite

                // Top App Bar
                WeatherTopBar(
                    location = uiState.selectedLocation,
                    isFavorite = isSelectedLocFavorite,
                    dataSource = report?.dataSource,
                    forecastSource = uiState.forecastSource,
                    hasApiKey = uiState.apiKey.isNotBlank(),
                    hasBpfApiKey = uiState.bpfApiKey.isNotBlank(),
                    isRefreshing = uiState.isRefreshing,
                    onLocationClick = { viewModel.openLocationSheet() },
                    onFavoriteToggle = { viewModel.toggleFavorite(uiState.selectedLocation) },
                    onSearchClick = { viewModel.openLocationSheet() },
                    onSettingsClick = { viewModel.openSettings() },
                    onDataSourceSelect = { source -> viewModel.selectForecastSource(source) },
                    onRefresh = { viewModel.loadWeather(isRefresh = true) }
                )

                // Error Notification Banner
                if (uiState.errorMessage != null && report != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MetterErrorContainer,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MetterErrorBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MetterErrorContent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MetterErrorContent,
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Main Content Body. Pull-to-refresh owns the available space so
                // the gesture works anywhere in the vertically scrollable forecast.
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.loadWeather(isRefresh = true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("weather_pull_to_refresh")
                ) {
                if (uiState.isLoading && report == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = BentoPurplePrimary,
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Getting latest weather",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = BentoTextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                } else if (report != null) {
                    val heroPresentation = buildHeroWeatherPresentation(
                        report = report,
                        selectedDayIndex = uiState.selectedDayIndex
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Hero Card (Current Temp, Condition, High/Low)
                        HeroWeatherCard(
                            current = heroPresentation.weather,
                            periodLabel = heroPresentation.periodLabel,
                            rainLabel = heroPresentation.rainLabel,
                            isDailySummary = heroPresentation.isDailySummary,
                            showWindDirection = heroPresentation.hasWindDirection,
                            tempUnit = uiState.tempUnit,
                            windUnit = uiState.windUnit
                        )

                        // Hourly Forecast Timeline (Interactive day selection & scrolling)
                        HourlyForecastRow(
                            dailyList = report.daily,
                            hourlyList = report.hourly,
                            selectedDayIndex = uiState.selectedDayIndex,
                            onSelectDay = { viewModel.selectForecastDay(it) },
                            tempUnit = uiState.tempUnit,
                            windUnit = uiState.windUnit,
                            location = report.location,
                            onOpenDetailSheet = {
                                report.daily.getOrNull(uiState.selectedDayIndex)?.let { day ->
                                    viewModel.openDayDetailSheet(day, uiState.selectedDayIndex)
                                }
                            }
                        )

                        // 7-Day Forecast Card (Clickable rows with detail trigger)
                        DailyForecastCard(
                            dailyList = report.daily,
                            tempUnit = uiState.tempUnit,
                            selectedDayIndex = uiState.selectedDayIndex,
                            onDayClick = { index, day ->
                                viewModel.openDayDetailSheet(day, index)
                            }
                        )

                        Surface(
                            onClick = { viewModel.openMapImages() },
                            shape = RoundedCornerShape(16.dp),
                            color = BentoHero,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BentoBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .testTag("open_weather_maps_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp)
                            ) {
                                Icon(Icons.Default.Map, null, tint = BentoPurplePrimary, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Weather maps", color = BentoTextPrimary, fontWeight = FontWeight.Bold)
                                    Text("Precipitation, cloud, temperature and pressure", color = BentoTextSecondary, fontSize = 11.sp)
                                }
                                Icon(Icons.Default.ChevronRight, "Open weather maps", tint = BentoTextSecondary)
                            }
                        }

                        // Detailed Meteorological Metrics Grid
                        val todayDaily = report.daily.firstOrNull()
                        WeatherMetricsGrid(
                            current = report.current,
                            windUnit = uiState.windUnit,
                            pressureUnit = uiState.pressureUnit,
                            sunrise = todayDaily?.sunrise,
                            sunset = todayDaily?.sunset
                        )

                        // Attribution & Model Run Footer
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp, horizontal = 24.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = BentoPurplePrimary.copy(alpha = 0.8f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Data Source: ${report.dataSource.displayName}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = BentoTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                            val attributions = buildList {
                                add(
                                    if (report.dataSource.isOfficialMetOffice) {
                                        "Powered by Met Office data" to "https://www.metoffice.gov.uk/"
                                    } else {
                                        "Weather data by Open-Meteo.com" to "https://open-meteo.com/"
                                    }
                                )
                                report.partialFallbackSource
                                    ?.takeIf { it != report.dataSource }
                                    ?.let { fallbackSource ->
                                        add(
                                            if (fallbackSource.isOfficialMetOffice) {
                                                "Powered by Met Office data" to "https://www.metoffice.gov.uk/"
                                            } else {
                                                "Weather data by Open-Meteo.com" to "https://open-meteo.com/"
                                            }
                                        )
                                    }
                            }.distinct()
                            attributions.forEach { (attribution, attributionUrl) ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = attribution,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = BentoPurplePrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.clickable {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(attributionUrl)))
                                        }
                                    }
                                )
                            }
                            val modelRunMillis = TimezoneUtils.parseIsoToMillis(report.modelRunTime)
                            Text(
                                text = forecastAge(report.fetchedAtMillis, uiState.clockTickMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = BentoTextSecondary
                            )
                            val timestampMillis = modelRunMillis ?: report.fetchedAtMillis
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (modelRunMillis != null) {
                                    "Model run: ${formatFooterTimestamp(timestampMillis, report.location)}"
                                } else {
                                    "Data updated: ${formatFooterTimestamp(timestampMillis, report.location)}"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = BentoTextSecondary.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                } else {
                    // Empty / Error State with Retry
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Unable to Load Forecast",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = BentoTextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = uiState.errorMessage ?: "Please check network connection or verify your Met Office API credentials.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = BentoTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadWeather() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BentoPurplePrimary,
                                    contentColor = BentoOnPrimary
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
                }
            }
        }

        // Dialogs & Sheets
        if (uiState.isApiKeyDialogOpen) {
            ApiKeyDialog(
                currentApiKey = uiState.apiKey,
                currentClientSecret = uiState.clientSecret,
                isTesting = uiState.isTestingApiKey,
                testResult = uiState.apiKeyTestStatus,
                onSaveKey = { key, secret -> viewModel.saveApiKey(key, secret) },
                onTestKey = { key, secret -> viewModel.testApiKey(key, secret) },
                onDismiss = { viewModel.closeApiKeyDialog() }
            )
        }

        if (uiState.isLocationSheetOpen) {
            LocationSearchSheet(
                searchQuery = uiState.searchQuery,
                searchResults = uiState.searchResults,
                isSearching = uiState.isSearching,
                favoriteLocations = uiState.favoriteLocations,
                currentSelectedId = uiState.selectedLocation.id,
                onQueryChange = { viewModel.searchLocations(it) },
                onLocationSelect = { viewModel.selectLocation(it) },
                onGpsSelect = { lat, lon, name -> viewModel.setGpsLocation(lat, lon, name) },
                onDismiss = { viewModel.closeLocationSheet() }
            )
        }

        if (uiState.isUnitsDialogOpen) {
            UnitSettingsDialog(
                currentTempUnit = uiState.tempUnit,
                currentWindUnit = uiState.windUnit,
                currentPressureUnit = uiState.pressureUnit,
                onTempUnitChange = { viewModel.setTemperatureUnit(it) },
                onWindUnitChange = { viewModel.setWindSpeedUnit(it) },
                onPressureUnitChange = { viewModel.setPressureUnit(it) },
                onDismiss = { viewModel.closeUnitsDialog() }
            )
        }

        if (uiState.isDayDetailSheetOpen && report != null) {
            DayHourlyDetailSheet(
                dailyList = report.daily,
                allHourlyList = report.hourly,
                location = report.location,
                selectedDayIndex = uiState.selectedDayIndex,
                tempUnit = uiState.tempUnit,
                windUnit = uiState.windUnit,
                pressureUnit = uiState.pressureUnit,
                onSelectDay = { viewModel.selectForecastDay(it) },
                onDismiss = { viewModel.closeDayDetailSheet() }
            )
        }

        if (uiState.isDebugSheetOpen) {
            ApiDebugSheet(
                isOpen = uiState.isDebugSheetOpen,
                debugInfo = uiState.apiDiagnosticResults[uiState.selectedApiDiagnosticSource],
                currentLocation = uiState.selectedLocation,
                customLatInput = uiState.customLatInput,
                customLonInput = uiState.customLonInput,
                coordinateTestResult = uiState.coordinateTestResult,
                isTestingCoordinates = uiState.isTestingCoordinates,
                rawGeocodingQueryInput = uiState.rawGeocodingQueryInput,
                rawGeocodingResultJson = uiState.rawGeocodingResultJson,
                rawGeocodingLocations = uiState.rawGeocodingLocations,
                isTestingGeocoding = uiState.isTestingGeocoding,
                selectedDiagnosticSource = uiState.selectedApiDiagnosticSource,
                isRunningDiagnostic = uiState.isRunningApiDiagnostic,
                onClose = { viewModel.closeDebugSheet() },
                onSelectDiagnosticSource = { viewModel.selectApiDiagnosticSource(it) },
                onRunDiagnostic = { viewModel.runApiDiagnostic() },
                onUpdateCustomLat = { viewModel.updateCustomLat(it) },
                onUpdateCustomLon = { viewModel.updateCustomLon(it) },
                onNudgeCoordinates = { latD, lonD -> viewModel.nudgeCoordinates(latD, lonD) },
                onRunCoordinateTest = { lat, lon -> viewModel.runCoordinateTest(lat, lon) },
                onUpdateGeocodingQuery = { viewModel.updateRawGeocodingQuery(it) },
                onSelectLocationFromGeocode = { loc ->
                    viewModel.selectLocation(loc)
                    viewModel.closeDebugSheet()
                }
            )
        }
    }

    if (uiState.isFirstRunApiPromptVisible) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissFirstRunApiPrompt() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = BentoPurplePrimary
                )
            },
            title = { Text("Set up Metter Weather") },
            text = {
                Text(
                    "For official Met Office forecasts, add a Spot API key from the Met Office DataHub. " +
                        "The BPF advanced model and Weather Maps use separate optional keys. " +
                        "You can continue with Open-Meteo, but Met Office features will be unavailable until configured."
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.openApiSettingsFromFirstRun() }) {
                    Text("Get API keys")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissFirstRunApiPrompt() }) {
                    Text("Continue with Open-Meteo")
                }
            }
        )
    }
}

private fun formatFooterTimestamp(timestampMillis: Long, location: LocationItem): String {
    return SimpleDateFormat("EEE d MMM, h:mm a z", Locale.getDefault()).apply {
        timeZone = TimezoneUtils.getTimeZoneForLocation(location)
    }.format(Date(timestampMillis))
}
