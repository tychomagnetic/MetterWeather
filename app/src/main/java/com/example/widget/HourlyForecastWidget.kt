package io.github.tychomagnetic.metterweather.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.LocalSize
import androidx.glance.ColorFilter
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.semantics.semantics
import androidx.glance.semantics.contentDescription
import androidx.work.WorkInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.tychomagnetic.metterweather.MainActivity
import io.github.tychomagnetic.metterweather.R
import io.github.tychomagnetic.metterweather.data.local.PreferencesManager
import io.github.tychomagnetic.metterweather.data.model.HourlyForecastItem
import io.github.tychomagnetic.metterweather.data.model.LocationItem
import io.github.tychomagnetic.metterweather.data.model.MetOfficeWeatherCode
import io.github.tychomagnetic.metterweather.data.model.TemperatureUnit
import io.github.tychomagnetic.metterweather.data.model.WeatherIconType
import io.github.tychomagnetic.metterweather.data.model.WeatherReport
import io.github.tychomagnetic.metterweather.data.model.WindSpeedUnit
import io.github.tychomagnetic.metterweather.data.util.TimezoneUtils
import kotlinx.coroutines.CancellationException

object WidgetKeys {
    const val VISIBLE_HOURS = 5
    val PAGE_OFFSET = intPreferencesKey("widget_page_offset")
    val REFRESH_TIMESTAMP = longPreferencesKey("widget_refresh_timestamp")
}

internal fun shiftedWidgetOffset(offset: Int, direction: Int, maxOffset: Int, pageSize: Int = WidgetKeys.VISIBLE_HOURS): Int =
    (offset.coerceIn(0, maxOffset) + direction.coerceIn(-1, 1) * pageSize)
        .coerceIn(0, maxOffset)

internal fun widgetHourCount(width: Float): Int = when {
    width >= 460f -> 8
    width >= 260f -> 5
    else -> 2
}

class HourlyForecastWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val glancePrefs = currentState<Preferences>()
            val pageOffset = glancePrefs[WidgetKeys.PAGE_OFFSET] ?: 0
            val workFlow = remember {
                WidgetRefreshManager.observeManualRefresh(context)
            }
            val work by workFlow.collectAsState(initial = emptyList())
            val refreshState = work.firstOrNull { !it.state.isFinished }?.state

            val appPrefs = PreferencesManager(context)
            val tempUnit = appPrefs.getTemperatureUnit()
            val resolvedLocation = WidgetLocationHelper.getWidgetDisplayLocation(context, appPrefs)
            val targetLocation = resolvedLocation ?: unavailableGpsLocation(context)
            val cachedReport = resolvedLocation?.let { location ->
                appPrefs.getCachedWidgetWeatherReport()?.takeIf { report ->
                    kotlin.math.abs(report.location.latitude - location.latitude) < 0.05 &&
                        kotlin.math.abs(report.location.longitude - location.longitude) < 0.05
                }
            }

            GlanceTheme {
                HourlyForecastWidgetContent(
                    context = context,
                    report = cachedReport,
                    selectedLocation = targetLocation,
                    tempUnit = tempUnit,
                    pageOffset = pageOffset,
                    refreshState = refreshState,
                    windUnit = appPrefs.getWindSpeedUnit()
                )
            }
        }
    }
    companion object {
        suspend fun updateAllWidgets(context: Context, resetPage: Boolean = false) {
            val glanceIds = try {
                GlanceAppWidgetManager(context).getGlanceIds(HourlyForecastWidget::class.java)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("HourlyForecastWidget", "Unable to find widget instances for update", error)
                return
            }
            val widget = HourlyForecastWidget()
            for (glanceId in glanceIds) {
                try {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { glancePrefs ->
                        glancePrefs.toMutablePreferences().apply {
                            if (resetPage) this[WidgetKeys.PAGE_OFFSET] = 0
                            this[WidgetKeys.REFRESH_TIMESTAMP] = System.currentTimeMillis()
                        }
                    }
                    widget.update(context, glanceId)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w("HourlyForecastWidget", "Unable to update widget $glanceId", error)
                }
            }
        }
    }
}

@Composable
fun HourlyForecastWidgetContent(
    context: Context,
    report: WeatherReport?,
    selectedLocation: LocationItem,
    tempUnit: TemperatureUnit,
    pageOffset: Int,
    refreshState: WorkInfo.State? = null,
    windUnit: WindSpeedUnit = WindSpeedUnit.MPH
) {
    val size = LocalSize.current
    val visibleCount = widgetHourCount(size.width.value)
    val showControls = size.width.value >= 260f && size.height.value >= 132f
    val locationName = report?.location?.name ?: selectedLocation.name
    val allHourly = report?.hourly ?: emptyList()

    // Derive Now from the clock each time Glance renders. The cached isNow flag
    // only describes the hour in which the forecast was downloaded.
    val nowIndex = if (allHourly.isNotEmpty()) {
        TimezoneUtils.findCurrentHourItemIndex(
            fullTimes = allHourly.map { it.fullTime },
            nowMillis = System.currentTimeMillis(),
            location = report?.location ?: selectedLocation
        ).coerceIn(0, allHourly.lastIndex)
    } else {
        0
    }
    val currentItem = allHourly.getOrNull(nowIndex)
    val currentTemp = currentItem?.let { tempUnit.format(it.temperatureCelsius) } ?: "--°"
    val conditionDesc = currentItem?.weatherCode?.description ?: when (selectedLocation.id) {
        "widget_gps_permission_required" -> "Open app to grant access"
        "widget_gps_location_unavailable" -> "Waiting for a location fix"
        else -> "Weather Forecast"
    }
    val maxOffset = (allHourly.size - nowIndex - visibleCount).coerceAtLeast(0)
    val clampedOffset = if (showControls) pageOffset.coerceIn(0, maxOffset) else 0
    val effectiveStartIndex = (nowIndex + clampedOffset).coerceIn(0, (allHourly.size - 1).coerceAtLeast(0))

    val visibleHourly = if (allHourly.isNotEmpty()) {
        allHourly.drop(effectiveStartIndex).take(visibleCount).mapIndexed { visibleIndex, item ->
            item.copy(isNow = effectiveStartIndex + visibleIndex == nowIndex)
        }
    } else {
        emptyList()
    }

    val canShiftLeft = clampedOffset > 0
    val canShiftRight = clampedOffset < maxOffset

    val shape = if (android.os.Build.VERSION.SDK_INT >= 31)
        GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)
        else GlanceModifier.cornerRadius(20.dp)
    Column(GlanceModifier.fillMaxSize().appWidgetBackground().then(shape)
        .background(GlanceTheme.colors.widgetBackground).padding(4.dp)) {
        if (showControls) {
            Row(GlanceModifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight().height(48.dp).clickable(actionStartActivity<MainActivity>())
                    .semantics { contentDescription = "Open weather for $locationName. $currentTemp. $conditionDesc" }) {
                    Text(locationName, style = TextStyle(color = GlanceTheme.colors.onSurface,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 2)
                    Text(currentTemp, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
                }
                Row(GlanceModifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (visibleCount > 2) {
                        Box(GlanceModifier.size(48.dp).cornerRadius(24.dp).background(GlanceTheme.colors.secondaryContainer)
                            .clickable(actionRunCallback<ResetToNowActionCallback>())
                            .semantics { contentDescription = "Return to current hour" }, contentAlignment = Alignment.Center) {
                            Text("Now", style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 12.sp))
                        }
                    }
                    WidgetPageButton(R.drawable.ic_widget_chevron_left, "Earlier $visibleCount hours", -visibleCount, canShiftLeft)
                    WidgetPageButton(R.drawable.ic_widget_chevron_right, "Later $visibleCount hours", visibleCount, canShiftRight)
                    Box(GlanceModifier.size(48.dp).cornerRadius(24.dp).background(GlanceTheme.colors.primaryContainer)
                        .clickable(actionRunCallback<RefreshWeatherActionCallback>())
                        .semantics { contentDescription = when (refreshState) {
                            WorkInfo.State.RUNNING -> "Refreshing forecast"
                            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Refresh queued, waiting to run"
                            else -> "Refresh forecast"
                        } }, contentAlignment = Alignment.Center) {
                        when (refreshState) {
                            WorkInfo.State.RUNNING -> CircularProgressIndicator(modifier = GlanceModifier.size(24.dp),
                                color = GlanceTheme.colors.onPrimaryContainer)
                            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> Text("Queued",
                                style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 10.sp))
                            else -> Image(ImageProvider(R.drawable.ic_widget_refresh), null,
                                modifier = GlanceModifier.size(24.dp),
                                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer))
                        }
                    }
                }
            }
        } else {
            // The short title is descriptive; the forecast tiles provide the large app-opening targets.
            Text(locationName, style = TextStyle(color = GlanceTheme.colors.onSurface,
                fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
        Spacer(GlanceModifier.height(2.dp))
        if (visibleHourly.isNotEmpty()) {
            Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                // Glance Rows allow at most ten children; use padding instead of
                // spacer children so the eight-hour layout is not truncated.
                visibleHourly.forEach { item ->
                    HourlyWidgetCard(item, tempUnit, GlanceModifier.defaultWeight().padding(horizontal = 1.dp), windUnit, size.height.value < 132f)
                }
            }
        } else {
            Box(GlanceModifier.fillMaxWidth().defaultWeight().clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center) {
                Text("Tap to load hourly forecast", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp))
            }
        }

    }
}

@Composable
private fun WidgetPageButton(icon: Int, label: String, delta: Int, enabled: Boolean) {
    val action = if (enabled) GlanceModifier.clickable(actionRunCallback<ShiftHoursActionCallback>(
        actionParametersOf(ShiftHoursActionCallback.OFFSET_DELTA_KEY to delta))) else GlanceModifier
    Box(GlanceModifier.size(48.dp).then(action).semantics {
        contentDescription = if (enabled) label else "$label, unavailable"
    }, contentAlignment = Alignment.Center) {
        Image(ImageProvider(icon), null, modifier = GlanceModifier.size(24.dp),
            colorFilter = ColorFilter.tint(if (enabled) GlanceTheme.colors.onSurface else GlanceTheme.colors.outline))
    }
}

@Composable
fun HourlyWidgetCard(
    item: HourlyForecastItem,
    tempUnit: TemperatureUnit,
    modifier: GlanceModifier = GlanceModifier,
    windUnit: WindSpeedUnit = WindSpeedUnit.MPH,
    compact: Boolean = false
) {
    val textColor = if (item.isNow) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant
    val precipitationColor = if (item.precipitationChance >= 30)
        ColorProvider(day = Color(0xFF005BBB), night = Color(0xFF9CCAFF)) else textColor
    val windLabel = "${windUnit.convert(item.windSpeedMph).toInt()}${if (windUnit == WindSpeedUnit.KNOTS) "kt" else windUnit.label} ${item.windDirectionCompass}"
    Box(modifier.fillMaxHeight().cornerRadius(10.dp)
        .background(if (item.isNow) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.surfaceVariant)
        .clickable(actionStartActivity<MainActivity>())
        .semantics { contentDescription = "${if (item.isNow) "Now" else item.timeLabel}, ${tempUnit.format(item.temperatureCelsius)}, ${item.weatherCode.description}, ${item.precipitationChance} percent chance of precipitation. Average wind ${windUnit.format(item.windSpeedMph)} from ${item.windDirectionCompass}. Open weather." }
        .padding(vertical = if (compact) 0.dp else 2.dp),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (item.isNow) "Now" else item.timeLabel, style = TextStyle(
                color = textColor, fontSize = if (compact) 10.sp else 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(getWidgetWeatherIcon(item.weatherCode)), null, modifier = GlanceModifier.size(if (compact) 16.dp else 20.dp))
                Text("${tempUnit.convert(item.temperatureCelsius).toInt()}°", style = TextStyle(
                    color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            Text("${item.precipitationChance}%", style = TextStyle(color = precipitationColor, fontSize = if (compact) 10.sp else 11.sp), maxLines = 1)
            Text(windLabel, style = TextStyle(color = textColor, fontSize = 9.sp), maxLines = 1)
        }
    }
}

fun getWidgetWeatherIcon(weatherCode: MetOfficeWeatherCode): Int {
    return when (weatherCode.iconType) {
        WeatherIconType.CLEAR_DAY -> R.drawable.ic_widget_sunny
        WeatherIconType.CLEAR_NIGHT -> R.drawable.ic_widget_night
        WeatherIconType.PARTLY_CLOUDY_DAY -> R.drawable.ic_widget_partly_cloudy
        WeatherIconType.PARTLY_CLOUDY_NIGHT -> R.drawable.ic_widget_partly_cloudy_night
        WeatherIconType.CLOUDY, WeatherIconType.OVERCAST -> R.drawable.ic_widget_cloudy
        WeatherIconType.MIST, WeatherIconType.FOG -> R.drawable.ic_widget_fog
        WeatherIconType.DRIZZLE -> R.drawable.ic_widget_drizzle
        WeatherIconType.LIGHT_RAIN -> R.drawable.ic_widget_rain
        WeatherIconType.RAIN_SHOWER_DAY -> R.drawable.ic_widget_rain_shower_day
        WeatherIconType.RAIN_SHOWER_NIGHT -> R.drawable.ic_widget_rain_shower_night
        WeatherIconType.HEAVY_RAIN_SHOWER_DAY -> R.drawable.ic_widget_heavy_rain_shower_day
        WeatherIconType.HEAVY_RAIN_SHOWER_NIGHT -> R.drawable.ic_widget_heavy_rain_shower_night
        WeatherIconType.HEAVY_RAIN -> R.drawable.ic_widget_heavy_rain
        WeatherIconType.SLEET, WeatherIconType.SLEET_DAY, WeatherIconType.SLEET_NIGHT, WeatherIconType.HAIL -> R.drawable.ic_widget_sleet
        WeatherIconType.SNOW, WeatherIconType.SNOW_DAY, WeatherIconType.SNOW_NIGHT -> R.drawable.ic_widget_snow
        WeatherIconType.HEAVY_SNOW, WeatherIconType.HEAVY_SNOW_DAY, WeatherIconType.HEAVY_SNOW_NIGHT -> R.drawable.ic_widget_heavy_snow
        WeatherIconType.THUNDERSTORM, WeatherIconType.THUNDERSTORM_DAY, WeatherIconType.THUNDERSTORM_NIGHT -> R.drawable.ic_widget_thunderstorm
    }
}

class RefreshWeatherActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        WidgetRefreshManager.enqueueManualRefresh(context)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { glancePrefs ->
            glancePrefs.toMutablePreferences().apply {
                this[WidgetKeys.PAGE_OFFSET] = 0
                this[WidgetKeys.REFRESH_TIMESTAMP] = System.currentTimeMillis()
            }
        }
        HourlyForecastWidget().update(context, glanceId)
    }
}

private fun unavailableGpsLocation(context: Context): LocationItem {
    val permissionGranted = WidgetLocationHelper.hasLocationPermission(context)
    return LocationItem(
        id = if (permissionGranted) {
            "widget_gps_location_unavailable"
        } else {
            "widget_gps_permission_required"
        },
        name = if (permissionGranted) {
            "Current location unavailable"
        } else {
            "Location permission required"
        },
        latitude = 1000.0,
        longitude = 1000.0,
        isCurrentLocation = true
    )
}

class ResetToNowActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prefs = PreferencesManager(context)
        prefs.setWidgetPageOffset(0)
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { glancePrefs ->
            glancePrefs.toMutablePreferences().apply {
                this[WidgetKeys.PAGE_OFFSET] = 0
                this[WidgetKeys.REFRESH_TIMESTAMP] = System.currentTimeMillis()
            }
        }
        HourlyForecastWidget().update(context, glanceId)
    }
}

class ShiftHoursActionCallback : ActionCallback {
    companion object {
        val OFFSET_DELTA_KEY = ActionParameters.Key<Int>("offset_delta")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val delta = parameters[OFFSET_DELTA_KEY] ?: 0
        val visibleCount = kotlin.math.abs(delta).coerceIn(2, 8)
        val prefs = PreferencesManager(context)
        val report = prefs.getCachedWidgetWeatherReport()
        val allHourly = report?.hourly ?: emptyList()
        val nowIndex = if (allHourly.isNotEmpty()) {
            TimezoneUtils.findCurrentHourItemIndex(
                fullTimes = allHourly.map { it.fullTime },
                nowMillis = System.currentTimeMillis(),
                location = report?.location
            ).coerceIn(0, allHourly.lastIndex)
        } else {
            0
        }
        val maxOffset = (allHourly.size - nowIndex - visibleCount).coerceAtLeast(0)

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { glancePrefs ->
            val currentOffset = glancePrefs[WidgetKeys.PAGE_OFFSET] ?: 0
            val newOffset = shiftedWidgetOffset(currentOffset, delta, maxOffset, visibleCount)
            prefs.setWidgetPageOffset(newOffset)
            glancePrefs.toMutablePreferences().apply {
                this[WidgetKeys.PAGE_OFFSET] = newOffset
                this[WidgetKeys.REFRESH_TIMESTAMP] = System.currentTimeMillis()
            }
        }
        HourlyForecastWidget().update(context, glanceId)
    }
}
