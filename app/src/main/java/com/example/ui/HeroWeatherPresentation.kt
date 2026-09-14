package io.github.tychomagnetic.metterweather.ui

import io.github.tychomagnetic.metterweather.data.model.CurrentWeather
import io.github.tychomagnetic.metterweather.data.model.WeatherReport
import io.github.tychomagnetic.metterweather.data.util.TimezoneUtils
import kotlin.math.abs

data class HeroWeatherPresentation(
    val weather: CurrentWeather,
    val periodLabel: String,
    val rainLabel: String
)

/**
 * Builds the summary shown above the day selector.
 *
 * Today remains a live observation/forecast for the current hour. Future days use
 * the daily headline condition, matching the day selector and detail sheet.
 * Temperature and wind use the forecast nearest local noon, while precipitation
 * uses the day's peak hourly probability.
 */
fun buildHeroWeatherPresentation(
    report: WeatherReport,
    selectedDayIndex: Int
): HeroWeatherPresentation {
    val selectedDay = report.daily.getOrNull(selectedDayIndex)
    if (selectedDayIndex <= 0 || selectedDay == null) {
        return HeroWeatherPresentation(
            weather = report.current,
            periodLabel = "Now",
            rainLabel = "Rain now"
        )
    }

    val representativeHour = report.hourly
        .asSequence()
        .filter {
            TimezoneUtils.getForecastLocalDate(it.fullTime, report.location) == selectedDay.date
        }
        .minByOrNull {
            abs(TimezoneUtils.getLocalHour(it.fullTime, report.location) - 12)
        }

    val selectedWeather = report.current.copy(
        temperatureCelsius = representativeHour?.temperatureCelsius
            ?: (selectedDay.maxTempCelsius + selectedDay.minTempCelsius) / 2.0,
        feelsLikeCelsius = representativeHour?.feelsLikeCelsius
            ?: (selectedDay.maxTempCelsius + selectedDay.minTempCelsius) / 2.0,
        weatherCode = selectedDay.dayWeatherCode,
        maxTempCelsius = selectedDay.maxTempCelsius,
        minTempCelsius = selectedDay.minTempCelsius,
        humidityPercent = representativeHour?.humidityPercent ?: report.current.humidityPercent,
        windSpeedMph = representativeHour?.windSpeedMph ?: report.current.windSpeedMph,
        windGustMph = selectedDay.maxWindGustMph,
        windDirectionDegrees = representativeHour?.windDirectionDegrees
            ?: report.current.windDirectionDegrees,
        precipitationChance = selectedDay.precipitationChance,
        uvIndex = representativeHour?.uvIndex ?: selectedDay.uvIndex,
        pressureHpa = representativeHour?.pressureHpa ?: report.current.pressureHpa,
        timestamp = representativeHour?.fullTime ?: selectedDay.date,
        isNight = false
    )

    return HeroWeatherPresentation(
        weather = selectedWeather,
        periodLabel = selectedDay.dayOfWeek,
        rainLabel = "Peak rain"
    )
}
