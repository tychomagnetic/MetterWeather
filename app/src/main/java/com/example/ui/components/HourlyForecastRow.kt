package io.github.tychomagnetic.metterweather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.tychomagnetic.metterweather.data.model.DailyForecastItem
import io.github.tychomagnetic.metterweather.data.model.HourlyForecastItem
import io.github.tychomagnetic.metterweather.data.model.LocationItem
import io.github.tychomagnetic.metterweather.data.model.TemperatureUnit
import io.github.tychomagnetic.metterweather.data.model.WindSpeedUnit
import io.github.tychomagnetic.metterweather.data.util.TimezoneUtils
import io.github.tychomagnetic.metterweather.ui.theme.BentoBorder
import io.github.tychomagnetic.metterweather.ui.theme.BentoCardWhite
import io.github.tychomagnetic.metterweather.ui.theme.BentoHero
import io.github.tychomagnetic.metterweather.ui.theme.BentoHeroText
import io.github.tychomagnetic.metterweather.ui.theme.BentoOnPrimary
import io.github.tychomagnetic.metterweather.ui.theme.BentoPurplePrimary
import io.github.tychomagnetic.metterweather.ui.theme.BentoTextPrimary
import io.github.tychomagnetic.metterweather.ui.theme.BentoTextSecondary
import io.github.tychomagnetic.metterweather.ui.theme.BentoTile
import io.github.tychomagnetic.metterweather.ui.theme.RainCyan
import io.github.tychomagnetic.metterweather.ui.theme.SolarGold
import io.github.tychomagnetic.metterweather.ui.precipitationLabel
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HourlyTimelineEntry(
    val item: HourlyForecastItem,
    val dayIndex: Int,
    val hourOfDay: Int,
    val isMidnight: Boolean,
    val dayLabel: String,
    val dateStr: String
)

internal fun buildHourlyTimelineEntries(
    dailyList: List<DailyForecastItem>,
    hourlyList: List<HourlyForecastItem>,
    location: LocationItem?
): List<HourlyTimelineEntry> {
    val entries = mutableListOf<HourlyTimelineEntry>()
    if (dailyList.isNotEmpty()) {
        dailyList.forEachIndexed { dayIndex, day ->
            val targetDate = day.date.take(10)
            hourlyList
                .filter { localDateFromTime(it.fullTime, location) == targetDate }
                .forEach { hour ->
                    val hourOfDay = extractHourFromTime(hour.fullTime, location)
                    val localDate = localDateFromTime(hour.fullTime, location)
                    entries += HourlyTimelineEntry(
                        item = hour,
                        dayIndex = dayIndex,
                        hourOfDay = hourOfDay,
                        isMidnight = hourOfDay == 0,
                        dayLabel = weekdayAbbreviation(localDate),
                        dateStr = localDate
                    )
                }
        }
    } else {
        hourlyList.forEach { hour ->
            val hourOfDay = extractHourFromTime(hour.fullTime, location)
            val localDate = localDateFromTime(hour.fullTime, location)
            entries += HourlyTimelineEntry(
                item = hour,
                dayIndex = 0,
                hourOfDay = hourOfDay,
                isMidnight = hourOfDay == 0,
                dayLabel = weekdayAbbreviation(localDate),
                dateStr = localDate
            )
        }
    }
    return entries
}

@Composable
fun HourlyForecastRow(
    dailyList: List<DailyForecastItem>,
    hourlyList: List<HourlyForecastItem>,
    selectedDayIndex: Int,
    onSelectDay: (Int) -> Unit,
    tempUnit: TemperatureUnit,
    windUnit: WindSpeedUnit,
    location: LocationItem? = null,
    onOpenDetailSheet: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentDayIndex = selectedDayIndex.coerceIn(0, (dailyList.size - 1).coerceAtLeast(0))
    val selectedDay = dailyList.getOrNull(currentDayIndex)

    // Build the complete continuous multi-day timeline
    val timelineEntries = remember(dailyList, hourlyList, location) {
        buildHourlyTimelineEntries(dailyList, hourlyList, location)
    }

    val hourlyListState = rememberLazyListState()
    val dayChipsListState = rememberLazyListState()
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    // 1. When a day is selected (tapped in the carousel or changed programmatically),
    // scroll the horizontal hourly timeline:
    // - For "Today": start at "Now" (with backward scroll to earlier hours permitted)
    // - For other days: start at 6:00 AM (with backward scroll to 12am-5am permitted)
    LaunchedEffect(selectedDayIndex, timelineEntries) {
        if (timelineEntries.isNotEmpty()) {
            val isToday = selectedDayIndex == 0 || selectedDay?.dayOfWeek.equals("Today", ignoreCase = true)
            val targetIndex = if (isToday) {
                val nowEntryIndex = timelineEntries.indexOfFirst { it.dayIndex == selectedDayIndex && it.item.isNow }
                    .takeIf { it >= 0 }
                    ?: timelineEntries.indexOfFirst { it.item.isNow }
                    .takeIf { it >= 0 }
                nowEntryIndex ?: (timelineEntries.indexOfFirst { it.dayIndex == selectedDayIndex && it.hourOfDay == 6 }
                    .takeIf { it >= 0 }
                    ?: timelineEntries.indexOfFirst { it.dayIndex == selectedDayIndex })
            } else {
                timelineEntries.indexOfFirst { it.dayIndex == selectedDayIndex && it.hourOfDay == 6 }
                    .takeIf { it >= 0 }
                    ?: timelineEntries.indexOfFirst { it.dayIndex == selectedDayIndex }
            }

            if (targetIndex >= 0) {
                val currentVisibleEntry = timelineEntries.getOrNull(hourlyListState.firstVisibleItemIndex)
                if (currentVisibleEntry?.dayIndex != selectedDayIndex || !hourlyListState.isScrollInProgress) {
                    isProgrammaticScroll = true
                    try {
                        hourlyListState.animateScrollToItem(targetIndex)
                    } finally {
                        isProgrammaticScroll = false
                    }
                }
            }

            // Keep the selected day chip visible in the top carousel
            if (selectedDayIndex in 0 until dailyList.size) {
                dayChipsListState.animateScrollToItem((selectedDayIndex - 1).coerceAtLeast(0))
            }
        }
    }

    // 2. When the user manually scrolls horizontally across the midnight boundary,
    // automatically detect the new day and update selectedDayIndex!
    // Include the selected index so this observer compares against the latest
    // selection rather than the value captured when the row first appeared.
    LaunchedEffect(timelineEntries, hourlyListState, selectedDayIndex) {
        snapshotFlow {
            val firstIndex = hourlyListState.firstVisibleItemIndex
            val entry = timelineEntries.getOrNull(firstIndex)
            val dayIdx = entry?.dayIndex
            dayIdx to hourlyListState.isScrollInProgress
        }.distinctUntilChanged().collect { (visibleDayIndex, isScrolling) ->
            if (visibleDayIndex != null && isScrolling && !isProgrammaticScroll) {
                if (visibleDayIndex != selectedDayIndex) {
                    onSelectDay(visibleDayIndex)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(BentoTile)
            .border(1.dp, BentoBorder.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            .padding(vertical = 14.dp)
            .testTag("hourly_forecast_section")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Section Header with active Day title & hour count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = BentoPurplePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HOURLY FORECAST",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = BentoTextSecondary,
                            letterSpacing = 1.1.sp,
                            fontSize = 11.5.sp
                        )
                    )
                    if (selectedDay != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• ${selectedDay.dayOfWeek}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = BentoPurplePrimary,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }

                if (onOpenDetailSheet != null) {
                    Surface(
                        onClick = onOpenDetailSheet,
                        shape = RoundedCornerShape(10.dp),
                        color = BentoPurplePrimary.copy(alpha = 0.1f),
                        modifier = Modifier.testTag("open_hourly_detail_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "View All",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = BentoPurplePrimary,
                                    fontSize = 11.sp
                                )
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = BentoPurplePrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Joined day forecast selector
            if (dailyList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    state = dayChipsListState,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(dailyList.size) { index ->
                        val dayItem = dailyList[index]
                        val isSelected = index == currentDayIndex
                        val segmentShape = when (index) {
                            0 -> RoundedCornerShape(
                                topStart = 12.dp,
                                topEnd = 0.dp,
                                bottomEnd = 0.dp,
                                bottomStart = 12.dp
                            )
                            dailyList.lastIndex -> RoundedCornerShape(
                                topStart = 0.dp,
                                topEnd = 12.dp,
                                bottomEnd = 12.dp,
                                bottomStart = 0.dp
                            )
                            else -> RoundedCornerShape(0.dp)
                        }
                        Surface(
                            onClick = { onSelectDay(index) },
                            shape = segmentShape,
                            color = if (isSelected) BentoPurplePrimary else BentoCardWhite,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) BentoPurplePrimary else BentoBorder.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.testTag("hourly_day_chip_$index")
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .width(108.dp)
                                    .height(68.dp)
                                    .padding(horizontal = 7.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = dayItem.dayOfWeek,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) BentoOnPrimary else BentoTextPrimary,
                                        fontSize = 11.5.sp
                                    ),
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    WeatherIconView(
                                        iconType = dayItem.dayWeatherCode.iconType,
                                        size = 20.dp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = dayItem.dayWeatherCode.description,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            color = if (isSelected) {
                                                BentoOnPrimary.copy(alpha = 0.9f)
                                            } else {
                                                BentoTextSecondary
                                            },
                                            fontSize = 9.5.sp,
                                            lineHeight = 10.5.sp
                                        ),
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val selectedDayHasHourlyData = timelineEntries.any { it.dayIndex == currentDayIndex }
            if (selectedDayHasHourlyData) {
                // Continuous Horizontal Timeline across days
                LazyRow(
                    state = hourlyListState,
                    contentPadding = PaddingValues(0.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            1.dp,
                            BentoBorder.copy(alpha = 0.6f),
                            RoundedCornerShape(14.dp)
                        )
                ) {
                    items(
                        items = timelineEntries,
                        key = { "${it.dayIndex}_${it.hourOfDay}_${it.item.fullTime}" }
                    ) { entry ->
                        HourlyItemCard(
                            entry = entry,
                            tempUnit = tempUnit,
                            windUnit = windUnit,
                            connected = true
                        )
                    }
                }
            } else {
                Text(
                    text = "Hourly data is unavailable for this day. The daily summary is still available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BentoTextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .testTag("hourly_data_unavailable")
                )
            }
        }
    }
}

@Composable
fun HourlyItemCard(
    item: HourlyForecastItem,
    tempUnit: TemperatureUnit,
    windUnit: WindSpeedUnit,
    modifier: Modifier = Modifier
) {
    val hVal = extractHourFromTime(item.fullTime)
    val entry = HourlyTimelineEntry(
        item = item,
        dayIndex = 0,
        hourOfDay = hVal,
        isMidnight = (hVal == 0),
        dayLabel = "",
        dateStr = item.date
    )
    HourlyItemCard(
        entry = entry,
        tempUnit = tempUnit,
        windUnit = windUnit,
        modifier = modifier
    )
}

@Composable
fun HourlyItemCard(
    entry: HourlyTimelineEntry,
    tempUnit: TemperatureUnit,
    windUnit: WindSpeedUnit,
    connected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val item = entry.item
    val isNow = item.isNow
    val isMidnight = entry.isMidnight

    Box(
        modifier = modifier
            .width(if (connected) 64.dp else 72.dp)
            .then(if (connected) Modifier.height(176.dp) else Modifier)
            .clip(if (connected) RoundedCornerShape(0.dp) else RoundedCornerShape(18.dp))
            .background(if (isNow) BentoHero else BentoCardWhite)
            .border(
                if (connected && !isNow) 0.5.dp else 1.dp,
                if (isNow) BentoPurplePrimary.copy(alpha = 0.7f)
                else if (isMidnight) BentoPurplePrimary.copy(alpha = 0.4f)
                else BentoBorder.copy(alpha = 0.55f),
                if (connected) RoundedCornerShape(0.dp) else RoundedCornerShape(18.dp)
            )
            .padding(
                vertical = if (connected) 8.dp else 10.dp,
                horizontal = if (connected) 3.dp else 5.dp
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Day Badge for Midnight / Day Transitions
            if (isMidnight && !isNow) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = BentoPurplePrimary.copy(alpha = 0.14f),
                    modifier = Modifier.padding(bottom = 3.dp)
                ) {
                    Text(
                        text = entry.dayLabel.take(3).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = BentoPurplePrimary,
                            fontSize = 8.5.sp,
                            letterSpacing = 0.5.sp
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            // Time Label (e.g., "Now", "6 AM", "12 AM", "2 PM")
            Text(
                text = item.timeLabel,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (isNow) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (isNow) BentoHeroText else BentoTextSecondary,
                    fontSize = 12.sp
                ),
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Weather Icon
            WeatherIconView(
                iconType = item.weatherCode.iconType,
                size = 30.dp
            )

            Spacer(modifier = Modifier.height(5.dp))

            // Temperature
            Text(
                text = "${tempUnit.convert(item.temperatureCelsius).toInt()}°",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isNow) BentoHeroText else BentoTextPrimary,
                    fontSize = 16.sp
                )
            )

            // Feels Like Temperature
            Text(
                text = "FL ${tempUnit.convert(item.feelsLikeCelsius).toInt()}°",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isNow) BentoHeroText.copy(alpha = 0.75f) else BentoTextSecondary.copy(alpha = 0.75f)
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // The 3h suffix identifies a whole-period probability repeated on
            // constituent hour cards, rather than an independent hourly chance.
            val isHighPrecip = item.precipitationChance > 30
            val precipColor = if (isHighPrecip) RainCyan else BentoTextSecondary.copy(alpha = 0.65f)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WaterDrop,
                    contentDescription = "Precipitation chance: ${item.precipitationLabel()}",
                    tint = precipColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(1.5.dp))
                Text(
                    text = item.precipitationLabel(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (item.precipitationPeriod?.hours == 3) 9.5.sp else 11.5.sp,
                        fontWeight = if (isHighPrecip) FontWeight.Bold else FontWeight.SemiBold,
                        color = precipColor
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Wind Direction & Speed
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Wind direction: ${item.windDirectionDegrees}° (${item.windDirectionCompass})",
                    tint = if (isNow) BentoHeroText.copy(alpha = 0.85f) else BentoPurplePrimary,
                    modifier = Modifier
                        .size(11.5.dp)
                        .rotate((item.windDirectionDegrees + 180f) % 360f)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = windUnit.format(item.windSpeedMph),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isNow) BentoHeroText.copy(alpha = 0.85f) else BentoTextSecondary
                    ),
                    maxLines = 1
                )
            }

            // Reserve a fixed slot for UV so cards stay the same height. The
            // midnight boundary already has a day-name badge, so omit the UV
            // slot there to prevent the two badges from stacking vertically.
            if (!isMidnight) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier.height(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.uvIndex >= 1) {
                        val uvColor = when {
                            item.uvIndex <= 2 -> Color(0xFF43A047) // Low
                            item.uvIndex <= 5 -> SolarGold // Moderate
                            else -> Color(0xFFE53935) // High / very high
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = uvColor.copy(alpha = if (isNow) 0.25f else 0.15f)
                        ) {
                            Text(
                                text = "UV ${item.uvIndex}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = uvColor
                                ),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun extractHourFromTime(fullTime: String?, location: LocationItem? = null): Int {
    if (fullTime == null) return 0
    if (location != null) {
        return TimezoneUtils.getLocalHour(fullTime, location)
    }
    return try {
        if (fullTime.contains("T")) {
            val timePart = fullTime.substringAfter("T").take(5)
            timePart.substringBefore(":").toInt()
        } else if (fullTime.contains(" ")) {
            val timePart = fullTime.substringAfter(" ").take(5)
            timePart.substringBefore(":").toInt()
        } else {
            0
        }
    } catch (_: Exception) {
        0
    }
}

private fun localDateFromTime(fullTime: String?, location: LocationItem?): String {
    if (fullTime.isNullOrBlank()) return ""

    // Open-Meteo returns local timestamps without a zone. In that case the date
    // already represents the local calendar day and must not be converted again.
    if (location == null || !fullTime.endsWith("Z")) return fullTime.take(10)

    val millis = TimezoneUtils.parseIsoToMillis(fullTime) ?: return fullTime.take(10)
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimezoneUtils.getTimeZoneForLocation(location)
    }.format(Date(millis))
}

private fun weekdayAbbreviation(date: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val parsedDate = parser.parse(date) ?: return date.take(3)
        SimpleDateFormat("EEE", Locale.US).format(parsedDate)
    } catch (_: Exception) {
        date.take(3)
    }
}
