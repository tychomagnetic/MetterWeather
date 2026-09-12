package io.github.tychomagnetic.metterweather.ui

import io.github.tychomagnetic.metterweather.data.model.HourlyForecastItem
import io.github.tychomagnetic.metterweather.data.model.LocationItem
import io.github.tychomagnetic.metterweather.data.util.TimezoneUtils

internal fun HourlyForecastItem.precipitationLabel(): String =
    "$precipitationChance%" + if (precipitationPeriod?.hours == 3) "/3h" else ""

internal fun HourlyForecastItem.precipitationDescription(location: LocationItem): String {
    val period = precipitationPeriod ?: return "Precipitation chance: $precipitationChance%"
    val start = TimezoneUtils.formatHourLabel(period.start, location)
    val end = TimezoneUtils.formatHourLabel(period.end, location)
    val threshold = if (period.hours == 3) "0.3" else "0.1"
    return "$precipitationChance% chance of more than $threshold mm of precipitation during $start–$end."
}
