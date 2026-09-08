package io.github.tychomagnetic.metterweather.ui

import io.github.tychomagnetic.metterweather.data.model.ForecastSource
import io.github.tychomagnetic.metterweather.data.model.LocationItem
import io.github.tychomagnetic.metterweather.data.model.WeatherDataSource
import io.github.tychomagnetic.metterweather.data.model.WeatherReport

internal fun matchesForecast(report: WeatherReport, location: LocationItem, source: ForecastSource): Boolean =
    kotlin.math.abs(report.location.latitude - location.latitude) < 0.0001 &&
        kotlin.math.abs(report.location.longitude - location.longitude) < 0.0001 &&
        when (source) {
            ForecastSource.MET_OFFICE_SPOT -> report.dataSource == WeatherDataSource.MET_OFFICE_DATAHUB ||
                report.dataSource == WeatherDataSource.MET_OFFICE_DATAPOINT
            ForecastSource.MET_OFFICE_BPF -> report.dataSource == WeatherDataSource.MET_OFFICE_BPF
            ForecastSource.OPEN_METEO -> report.dataSource == WeatherDataSource.OPEN_METEO_METEOROLOGICAL
        }

internal fun forecastAge(fetchedAtMillis: Long, nowMillis: Long): String {
    val minutes = ((nowMillis - fetchedAtMillis).coerceAtLeast(0L) / 60_000L)
    return when {
        minutes < 1 -> "Saved just now"
        minutes < 60 -> "Saved $minutes min ago"
        minutes < 1440 -> "Saved ${minutes / 60} hr ago"
        else -> "Saved ${minutes / 1440} days ago"
    }
}
