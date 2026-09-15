package io.github.tychomagnetic.metterweather.ui

import io.github.tychomagnetic.metterweather.data.model.CurrentWeather
import io.github.tychomagnetic.metterweather.data.model.WeatherReport
import io.github.tychomagnetic.metterweather.data.util.TimezoneUtils

data class HeroWeatherPresentation(
    val weather: CurrentWeather,
    val periodLabel: String,
    val rainLabel: String,
    val isDailySummary: Boolean,
    val hasWindDirection: Boolean
)

/**
 * Builds the summary shown above the day selector.
 *
 * Today remains a live observation/forecast for the current hour. Future days use
 * the daily headline condition, matching the day selector and detail sheet.
 * High/low temperature and the strongest hourly wind replace point-in-time
 * values for future days, while precipitation uses the daily peak probability.
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
            rainLabel = "Rain now",
            isDailySummary = false,
            hasWindDirection = true
        )
    }

    val strongestWindHour = report.hourly
        .asSequence()
        .filter {
            TimezoneUtils.getForecastLocalDate(it.fullTime, report.location) == selectedDay.date
        }
        .maxByOrNull { it.windSpeedMph }

    val selectedWeather = report.current.copy(
        temperatureCelsius = selectedDay.maxTempCelsius,
        feelsLikeCelsius = selectedDay.maxTempCelsius,
        weatherCode = selectedDay.dayWeatherCode,
        maxTempCelsius = selectedDay.maxTempCelsius,
        minTempCelsius = selectedDay.minTempCelsius,
        windSpeedMph = strongestWindHour?.windSpeedMph ?: selectedDay.maxWindGustMph,
        windGustMph = selectedDay.maxWindGustMph,
        windDirectionDegrees = strongestWindHour?.windDirectionDegrees
            ?: report.current.windDirectionDegrees,
        precipitationChance = selectedDay.precipitationChance,
        uvIndex = selectedDay.uvIndex,
        timestamp = strongestWindHour?.fullTime ?: selectedDay.date,
        isNight = false
    )

    return HeroWeatherPresentation(
        weather = selectedWeather,
        periodLabel = selectedDay.dayOfWeek,
        rainLabel = "Peak rain",
        isDailySummary = true,
        hasWindDirection = strongestWindHour != null
    )
}
