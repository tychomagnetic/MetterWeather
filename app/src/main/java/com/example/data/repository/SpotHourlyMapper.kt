package io.github.tychomagnetic.metterweather.data.repository

import io.github.tychomagnetic.metterweather.data.model.HourlyForecastItem
import io.github.tychomagnetic.metterweather.data.model.LocationItem
import io.github.tychomagnetic.metterweather.data.model.MetOfficeHourlyTimeSeriesItem
import io.github.tychomagnetic.metterweather.data.model.MetOfficeWeatherCode
import io.github.tychomagnetic.metterweather.data.util.TimezoneUtils
import kotlin.math.roundToInt

/** Maps only complete Spot timestamps so missing provider data is never displayed as an estimate. */
internal object SpotHourlyMapper {
    fun map(item: MetOfficeHourlyTimeSeriesItem, location: LocationItem): HourlyForecastItem? {
        val time = item.time?.takeIf { it.isNotBlank() } ?: return null
        val temperature = temperature(item) ?: return null
        val weatherCode = item.significantWeatherCode?.takeIf { it in 0..30 } ?: return null
        val precipitation = item.probOfPrecipitation ?: return null
        val windSpeed = item.windSpeed10m ?: return null
        val windDirection = item.windDirectionFrom10m ?: return null
        val humidity = item.screenRelativeHumidity ?: return null
        val pressure = item.mslp ?: return null
        val feelsLike = feelsLike(item) ?: temperature
        val isNight = TimezoneUtils.isNightTime(time, location)

        return HourlyForecastItem(
            timeLabel = TimezoneUtils.formatHourLabel(time, location, false),
            fullTime = time,
            date = TimezoneUtils.getForecastLocalDate(time, location),
            temperatureCelsius = roundOneDecimal(temperature),
            feelsLikeCelsius = roundOneDecimal(feelsLike),
            weatherCode = MetOfficeWeatherCode.fromCode(weatherCode, isNight),
            precipitationChance = precipitation.coerceIn(0, 100),
            windSpeedMph = roundOneDecimal(windSpeed * METRES_PER_SECOND_TO_MPH),
            windDirectionDegrees = windDirection,
            humidityPercent = humidity.roundToInt().coerceIn(0, 100),
            // A missing UV value at night means no UV exposure. During daylight,
            // omit the timestamp instead of presenting zero as provider data.
            uvIndex = item.uvIndex ?: if (isNight) 0 else return null,
            pressureHpa = roundOneDecimal(if (pressure > 50_000) pressure / 100.0 else pressure),
            isNow = false
        )
    }

    private fun temperature(item: MetOfficeHourlyTimeSeriesItem): Double? =
        item.screenTemperature
            ?: item.maxScreenAirTemp
            ?: item.minScreenAirTemp
            ?: item.screenApparentTemperature
            ?: item.feelsLikeTemp
            ?: item.feelsLikeTemperature

    private fun feelsLike(item: MetOfficeHourlyTimeSeriesItem): Double? =
        item.screenApparentTemperature
            ?: item.feelsLikeTemp
            ?: item.feelsLikeTemperature

    private fun roundOneDecimal(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0

    private const val METRES_PER_SECOND_TO_MPH = 2.23694
}
