package io.github.tychomagnetic.metterweather

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import io.github.tychomagnetic.metterweather.data.local.PreferencesManager
import io.github.tychomagnetic.metterweather.data.model.*
import io.github.tychomagnetic.metterweather.data.repository.WidgetForecastException
import io.github.tychomagnetic.metterweather.ui.*
import io.github.tychomagnetic.metterweather.widget.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ForecastRefreshTest {
    private val location = LocationItem.DEFAULT_LOCATIONS.first()
    private fun report(source: WeatherDataSource) = WeatherReport(
        location, CurrentWeather(18.0, 17.0, MetOfficeWeatherCode.CLOUDY, 20.0, 12.0,
            70, 8.0, 12.0, 180, 30, 2, 20_000, 1012.0, "2026-08-16T12:00:00Z", false),
        emptyList(), emptyList(), source, fetchedAtMillis = 1_000_000L
    )

    @Test fun `startup restores old Spot and Open Meteo reports while refresh runs`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        for ((source, dataSource) in listOf(
            ForecastSource.MET_OFFICE_SPOT to WeatherDataSource.MET_OFFICE_DATAHUB,
            ForecastSource.OPEN_METEO to WeatherDataSource.OPEN_METEO_METEOROLOGICAL
        )) {
            app.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE).edit().clear().commit()
            val saved = report(dataSource)
            PreferencesManager(app).apply {
                setForecastSource(source)
                setSelectedLocation(location)
                setCachedWeatherReport(saved)
            }
            val viewModel = WeatherViewModel(app)
            val store = ViewModelStore().apply { put("weather", viewModel) }
            try {
                assertEquals(saved.fetchedAtMillis, viewModel.uiState.value.weatherReport?.fetchedAtMillis)
                assertEquals(dataSource, viewModel.uiState.value.weatherReport?.dataSource)
                assertFalse(viewModel.uiState.value.isLoading)
                assertTrue(viewModel.uiState.value.isRefreshing)
            } finally { store.clear() }
        }
    }

    @Test fun `cache must match coordinates and source`() {
        val saved = report(WeatherDataSource.MET_OFFICE_DATAHUB)
        assertTrue(matchesForecast(saved, location.copy(name = "Renamed"), ForecastSource.MET_OFFICE_SPOT))
        assertFalse(matchesForecast(saved, location.copy(latitude = location.latitude + 1), ForecastSource.MET_OFFICE_SPOT))
        assertFalse(matchesForecast(saved, location, ForecastSource.OPEN_METEO))
        assertTrue(matchesForecast(saved.copy(dataSource = WeatherDataSource.MET_OFFICE_DATAPOINT), location, ForecastSource.MET_OFFICE_SPOT))
        assertEquals("Saved 2 hr ago", forecastAge(1_000_000L, 8_200_000L))
        assertEquals("Saved just now", forecastAge(100L, 0L))
    }

    @Test fun `only network and server failures request retry`() {
        for (status in listOf(null, 401, 403)) {
            assertEquals(WidgetRefreshOutcome.CREDENTIALS_REQUIRED, classifyWidgetFailure(WidgetForecastException(status)))
        }
        assertEquals(WidgetRefreshOutcome.QUOTA_EXCEEDED, classifyWidgetFailure(WidgetForecastException(429)))
        for (status in listOf(408, 500, 502, 503)) {
            assertEquals(WidgetRefreshOutcome.RETRYABLE_FAILURE, classifyWidgetFailure(WidgetForecastException(status)))
        }
        assertEquals(WidgetRefreshOutcome.RETRYABLE_FAILURE, classifyWidgetFailure(IOException()))
        assertEquals(WidgetRefreshOutcome.FAILED, classifyWidgetFailure(WidgetForecastException(400)))
        assertEquals(WidgetRefreshOutcome.FAILED, classifyWidgetFailure(IllegalArgumentException()))
    }

    @Test fun `quota cooldown supports seconds dates and missing headers`() {
        assertEquals(7_200_000L, quotaResumeTime("7200", 0L))
        assertEquals(7_200_000L, quotaResumeTime("Thu, 1 Jan 1970 02:00:00 GMT", 0L))
        assertEquals(3_600_000L, quotaResumeTime(null, 0L))
        assertEquals(3_600_000L, quotaResumeTime("invalid", 0L))
        assertEquals(60_000L, quotaResumeTime("1", 0L))
    }

    @Test fun `refresh skips when no widget is installed`() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertFalse(WidgetRefreshManager.hasInstalledWidgets(app))
        assertEquals(WidgetRefreshOutcome.SKIPPED, WidgetRefreshManager.performWidgetRefresh(app))
    }
}
