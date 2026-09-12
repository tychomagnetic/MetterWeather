package io.github.tychomagnetic.metterweather

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.tychomagnetic.metterweather.data.local.PreferencesManager
import io.github.tychomagnetic.metterweather.data.model.CurrentWeather
import io.github.tychomagnetic.metterweather.data.model.DailyForecastItem
import io.github.tychomagnetic.metterweather.data.model.LocationItem
import io.github.tychomagnetic.metterweather.data.model.MetOfficeHourlyTimeSeriesItem
import io.github.tychomagnetic.metterweather.data.model.MetOfficeWeatherCode
import io.github.tychomagnetic.metterweather.data.model.WeatherDataSource
import io.github.tychomagnetic.metterweather.data.model.WeatherReport
import io.github.tychomagnetic.metterweather.data.repository.SpotHourlyMapper
import io.github.tychomagnetic.metterweather.ui.components.buildHourlyTimelineEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ForecastReliabilityTest {
    private val location = LocationItem.DEFAULT_LOCATIONS.first()

    @Test
    fun `Spot mapper omits incomplete timestamps instead of inventing values`() {
        val complete = spotHour()
        assertNotNull(SpotHourlyMapper.map(complete, location))
        assertNull(SpotHourlyMapper.map(complete.copy(mslp = null), location))
        assertNull(SpotHourlyMapper.map(complete.copy(screenRelativeHumidity = null), location))
        assertNull(SpotHourlyMapper.map(complete.copy(significantWeatherCode = null), location))
    }

    @Test
    fun `timeline does not synthesize hours for a daily-only forecast`() {
        val daily = DailyForecastItem(
            date = "2099-09-01",
            dayOfWeek = "Monday",
            dateFormatted = "1 Sep",
            maxTempCelsius = 20.0,
            minTempCelsius = 10.0,
            dayWeatherCode = MetOfficeWeatherCode.CLOUDY,
            nightWeatherCode = MetOfficeWeatherCode.CLEAR_NIGHT,
            precipitationChance = 20,
            uvIndex = 3,
            maxWindGustMph = 14.0
        )

        assertEquals(0, buildHourlyTimelineEntries(listOf(daily), emptyList(), location).size)
    }

    @Test
    fun `BPF cache retains only the newest bounded set of locations`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val prefs = PreferencesManager(context)

        val locations = (0..PreferencesManager.MAX_BPF_CACHE_ENTRIES).map { index ->
            location.copy(
                id = "bounded-cache-$index",
                name = "Cache $index",
                latitude = 50.0 + index,
                longitude = -1.0
            )
        }
        locations.forEachIndexed { index, item ->
            assertEquals(true, prefs.setCachedBpfWeatherReport(report(item, fetchedAt = 1_000L + index)))
        }

        assertNull(prefs.getCachedBpfWeatherReport(locations.first()))
        locations.drop(1).forEach { assertNotNull(prefs.getCachedBpfWeatherReport(it)) }
        assertNull(prefs.cacheWriteErrorFlow.value)
    }

    @Test fun `trace threshold caches are ignored and new interval metadata survives reload`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = context.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE)
        storage.edit().clear().commit()
        val period = io.github.tychomagnetic.metterweather.data.model.PrecipitationPeriod(
            "2099-09-01T12:00:00Z", "2099-09-01T15:00:00Z", 3, 0.3)
        val saved = report(location, 1_000L).copy(hourly = listOf(
            SpotHourlyMapper.map(spotHour(), location)!!.copy(precipitationPeriod = period)))
        val oldKey = "cached_bpf_location_v5_" + String.format(java.util.Locale.US,
            "%.4f_%.4f", location.latitude, location.longitude)
        val json = io.github.tychomagnetic.metterweather.data.remote.ApiServiceProvider.moshi
            .adapter(WeatherReport::class.java).toJson(saved)
        storage.edit().putString(oldKey, json).commit()
        val prefs = PreferencesManager(context)
        assertNull(prefs.getCachedBpfWeatherReport(location))
        assertEquals(true, prefs.setCachedBpfWeatherReport(saved))
        assertEquals(period, PreferencesManager(context).getCachedBpfWeatherReport(location)!!.hourly.first().precipitationPeriod)
    }

    private fun spotHour() = MetOfficeHourlyTimeSeriesItem(
        time = "2099-09-01T12:00:00Z",
        screenTemperature = 18.0,
        screenApparentTemperature = 17.0,
        screenRelativeHumidity = 70.0,
        significantWeatherCode = 7,
        probOfPrecipitation = 20,
        windSpeed10m = 4.0,
        windDirectionFrom10m = 180,
        mslp = 101_200.0,
        uvIndex = 3
    )

    private fun report(location: LocationItem, fetchedAt: Long) = WeatherReport(
        location = location,
        current = CurrentWeather(
            temperatureCelsius = 18.0,
            feelsLikeCelsius = 17.0,
            weatherCode = MetOfficeWeatherCode.CLOUDY,
            maxTempCelsius = 20.0,
            minTempCelsius = 12.0,
            humidityPercent = 70,
            windSpeedMph = 8.0,
            windGustMph = 12.0,
            windDirectionDegrees = 180,
            precipitationChance = 30,
            uvIndex = 2,
            visibilityMeters = 20_000,
            pressureHpa = 1012.0,
            timestamp = "2099-09-01T12:00:00Z",
            isNight = false
        ),
        hourly = emptyList(),
        daily = emptyList(),
        dataSource = WeatherDataSource.MET_OFFICE_BPF,
        fetchedAtMillis = fetchedAt
    )
}
