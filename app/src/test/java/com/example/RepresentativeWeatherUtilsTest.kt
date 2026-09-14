package io.github.tychomagnetic.metterweather

import io.github.tychomagnetic.metterweather.data.model.DailyForecastItem
import io.github.tychomagnetic.metterweather.data.model.HourlyForecastItem
import io.github.tychomagnetic.metterweather.data.model.LocationItem
import io.github.tychomagnetic.metterweather.data.model.MetOfficeWeatherCode
import io.github.tychomagnetic.metterweather.data.util.RepresentativeWeatherUtils
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class RepresentativeWeatherUtilsTest {
    private val location = LocationItem.DEFAULT_LOCATIONS.first()

    @Test
    fun `five wet hours are not hidden by seven sunny hours`() {
        val items = (0 until 12).map { index ->
            hour("2026-08-22T${(index + 6).toString().padStart(2, '0')}:00:00Z",
                if (index in 2..6) MetOfficeWeatherCode.LIGHT_RAIN else MetOfficeWeatherCode.SUNNY_INTERVALS)
        }
        assertEquals(MetOfficeWeatherCode.LIGHT_RAIN, RepresentativeWeatherUtils.selectRepresentative(items))
    }

    @Test
    fun `mixed wet types count together before selecting the headline`() {
        val codes = List(3) { MetOfficeWeatherCode.LIGHT_RAIN } +
            List(3) { MetOfficeWeatherCode.LIGHT_RAIN_SHOWER_DAY } +
            List(7) { MetOfficeWeatherCode.SUNNY_INTERVALS }
        val items = codes.mapIndexed { index, code ->
            hour("2026-08-22T${(index + 6).toString().padStart(2, '0')}:00:00Z", code)
        }
        assertEquals(MetOfficeWeatherCode.LIGHT_RAIN_SHOWER_DAY,
            RepresentativeWeatherUtils.selectRepresentative(items))
    }

    @Test
    fun `isolated heavy shower does not define an otherwise fine day`() {
        val items = List(12) { index ->
            hour("2026-08-22T${(index + 6).toString().padStart(2, '0')}:00:00Z",
                if (index == 5) MetOfficeWeatherCode.HEAVY_RAIN_SHOWER_DAY else MetOfficeWeatherCode.SUNNY_INTERVALS)
        }
        assertEquals(MetOfficeWeatherCode.SUNNY_INTERVALS, RepresentativeWeatherUtils.selectRepresentative(items))
    }

    @Test
    fun `future provider summary survives repeated clock recalculation`() {
        val daily = listOf(day("2026-08-22").copy(providerDayWeatherCode = MetOfficeWeatherCode.LIGHT_RAIN))
        val hourly = listOf(hour("2026-08-22T12:00:00Z", MetOfficeWeatherCode.SUNNY_INTERVALS))
        val first = RepresentativeWeatherUtils.applyToDailyForecast(daily, hourly, location, utcMillis("2026-08-20T12:00:00Z"))
        val second = RepresentativeWeatherUtils.applyToDailyForecast(first, hourly, location, utcMillis("2026-08-21T12:00:00Z"))
        assertEquals(MetOfficeWeatherCode.LIGHT_RAIN, second.single().dayWeatherCode)
    }

    @Test
    fun `today ignores provider whole day summary once wet morning is over`() {
        val daily = listOf(day("2026-08-22").copy(providerDayWeatherCode = MetOfficeWeatherCode.LIGHT_RAIN))
        val hourly = listOf(hour("2026-08-22T08:00:00Z", MetOfficeWeatherCode.LIGHT_RAIN),
            hour("2026-08-22T14:00:00Z", MetOfficeWeatherCode.SUNNY_INTERVALS))
        val result = RepresentativeWeatherUtils.applyToDailyForecast(daily, hourly, location, utcMillis("2026-08-22T14:00:00Z"))
        assertEquals(MetOfficeWeatherCode.SUNNY_INTERVALS, result.single().dayWeatherCode)
    }

    @Test
    fun `future day uses predominant daylight condition rather than first hour`() {
        val daily = listOf(day("2026-08-22"))
        val hourly = listOf(
            hour("2026-08-22T06:00:00Z", MetOfficeWeatherCode.OVERCAST),
            hour("2026-08-22T09:00:00Z", MetOfficeWeatherCode.SUNNY_INTERVALS),
            hour("2026-08-22T12:00:00Z", MetOfficeWeatherCode.SUNNY_INTERVALS),
            hour("2026-08-22T15:00:00Z", MetOfficeWeatherCode.SUNNY_INTERVALS)
        )

        val result = RepresentativeWeatherUtils.applyToDailyForecast(
            daily, hourly, location, utcMillis("2026-08-20T12:00:00Z")
        )

        assertEquals(MetOfficeWeatherCode.SUNNY_INTERVALS, result.single().dayWeatherCode)
    }

    @Test
    fun `today after sunset describes remaining night`() {
        val daily = listOf(day("2026-08-20", sunset = "20:15"))
        val hourly = listOf(
            hour("2026-08-20T12:00:00Z", MetOfficeWeatherCode.SUNNY_INTERVALS),
            hour("2026-08-20T20:00:00Z", MetOfficeWeatherCode.LIGHT_RAIN_SHOWER_NIGHT),
            hour("2026-08-20T21:00:00Z", MetOfficeWeatherCode.LIGHT_RAIN_SHOWER_NIGHT),
            hour("2026-08-20T22:00:00Z", MetOfficeWeatherCode.PARTLY_CLOUDY_NIGHT)
        )

        val result = RepresentativeWeatherUtils.applyToDailyForecast(
            daily, hourly, location, utcMillis("2026-08-20T20:30:00Z")
        )

        assertEquals(MetOfficeWeatherCode.LIGHT_RAIN_SHOWER_NIGHT, result.single().dayWeatherCode)
    }

    @Test
    fun `day and night variants count as the same weather family`() {
        val items = listOf(
            hour("2026-08-20T05:00:00Z", MetOfficeWeatherCode.LIGHT_RAIN_SHOWER_NIGHT),
            hour("2026-08-20T06:00:00Z", MetOfficeWeatherCode.LIGHT_RAIN_SHOWER_DAY),
            hour("2026-08-20T07:00:00Z", MetOfficeWeatherCode.CLOUDY)
        )

        assertEquals(
            MetOfficeWeatherCode.LIGHT_RAIN_SHOWER_DAY,
            RepresentativeWeatherUtils.selectRepresentative(items)
        )
    }

    private fun day(date: String, sunset: String = "20:30") = DailyForecastItem(
        date = date,
        dayOfWeek = "Day",
        dateFormatted = date,
        maxTempCelsius = 20.0,
        minTempCelsius = 10.0,
        dayWeatherCode = MetOfficeWeatherCode.OVERCAST,
        nightWeatherCode = MetOfficeWeatherCode.CLEAR_NIGHT,
        precipitationChance = 0,
        uvIndex = 0,
        maxWindGustMph = 0.0,
        sunrise = "06:00",
        sunset = sunset
    )

    private fun hour(time: String, code: MetOfficeWeatherCode) = HourlyForecastItem(
        timeLabel = time,
        fullTime = time,
        date = time.take(10),
        temperatureCelsius = 15.0,
        feelsLikeCelsius = 15.0,
        weatherCode = code,
        precipitationChance = 0,
        windSpeedMph = 0.0,
        windDirectionDegrees = 0,
        humidityPercent = 50,
        uvIndex = 0
    )

    private fun utcMillis(value: String): Long = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        Locale.US
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(value)!!.time
}
