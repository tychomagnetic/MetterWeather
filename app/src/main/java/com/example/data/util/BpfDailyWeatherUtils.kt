package io.github.tychomagnetic.metterweather.data.util

import io.github.tychomagnetic.metterweather.data.model.LocationItem
import io.github.tychomagnetic.metterweather.data.model.MetOfficeWeatherCode
import java.time.Instant

/** Only use rolling 24-hour summaries that cover exactly one local calendar day. */
internal object BpfDailyWeatherUtils {
    fun select(
        summariesByStart: Map<String, Double>,
        location: LocationItem
    ): Map<String, MetOfficeWeatherCode> {
        val zone = TimezoneUtils.getTimeZoneForLocation(location).toZoneId()
        return summariesByStart.mapNotNull { (start, value) ->
            val millis = TimezoneUtils.parseIsoToMillis(start) ?: return@mapNotNull null
            val localStart = Instant.ofEpochMilli(millis).atZone(zone)
            val date = localStart.toLocalDate()
            if (localStart != date.atStartOfDay(zone) ||
                localStart.plusHours(24) != date.plusDays(1).atStartOfDay(zone) ||
                !value.isFinite() || value !in 0.0..30.0 || value != value.toInt().toDouble()
            ) return@mapNotNull null
            date.toString() to MetOfficeWeatherCode.fromCode(value.toInt())
        }.toMap()
    }
}
