package io.github.tychomagnetic.metterweather

import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import io.github.tychomagnetic.metterweather.data.model.*
import io.github.tychomagnetic.metterweather.widget.HourlyForecastWidgetContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WidgetLayoutTest {
    @Test fun `layouts render with accessible controls across sizes and themes`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val location = LocationItem.DEFAULT_LOCATIONS.first()
            val current = CurrentWeather(18.0, 17.0, MetOfficeWeatherCode.SUNNY_INTERVALS,
                20.0, 12.0, 65, 8.0, 12.0, 180, 20, 3, 20000, 1015.0, "", false)
            val hours = (0..23).map {
                val time = Instant.ofEpochMilli(System.currentTimeMillis() / 3_600_000L * 3_600_000L)
                    .plusSeconds(it * 3600L).toString()
                HourlyForecastItem(time.substring(11, 16), time, temperatureCelsius = 18.0 - it % 4,
                    feelsLikeCelsius = 17.0, weatherCode = MetOfficeWeatherCode.SUNNY_INTERVALS,
                    precipitationChance = listOf(20, 29, 30, 70)[it % 4], windSpeedMph = 8.0, windDirectionDegrees = 180,
                    humidityPercent = 65, uvIndex = 3)
            }
            val report = WeatherReport(location, current, hours, emptyList(), WeatherDataSource.MET_OFFICE_DATAHUB)
            for (night in listOf(false, true)) {
                RuntimeEnvironment.setQualifiers((if (night) "night" else "notnight") + "-mdpi")
                for ((width, height) in listOf(180 to 90, 260 to 90, 300 to 90, 260 to 132, 300 to 160, 460 to 132)) {
                    val widget = object : GlanceAppWidget() {
                        override suspend fun provideGlance(context: Context, id: GlanceId) {
                            provideContent {
                                GlanceTheme {
                                    HourlyForecastWidgetContent(context, report, location,
                                        TemperatureUnit.CELSIUS, if (height < 132) 5 else 0,
                                        when (width) {
                                            180 -> WorkInfo.State.ENQUEUED
                                            460 -> WorkInfo.State.RUNNING
                                            else -> null
                                        }, windUnit = when (width) {
                                            180 -> WindSpeedUnit.KNOTS
                                            460 -> WindSpeedUnit.KPH
                                            else -> WindSpeedUnit.MPH
                                        })
                                }
                            }
                        }
                    }
                    val remote = widget.compose(context, size = DpSize(width.dp, height.dp))
                    val view = remote.apply(context, FrameLayout(context))
                    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, width, height)
                    var forecastTileCount = 0
                    var nowControlCount = 0
                    var currentHourTileCount = 0
                    var windRowCount = 0
                    fun checkTargets(node: View) {
                        if (node is android.widget.TextView) {
                            val expectedWind = when (width) {
                                180 -> "6kt S"
                                460 -> "12km/h S"
                                else -> "8mph S"
                            }
                            if (node.text.toString() == expectedWind) {
                                windRowCount++
                                assertTrue("Wind text must fit vertically at ${width}x${height}",
                                    node.layout.height + node.compoundPaddingTop + node.compoundPaddingBottom <= node.height)
                                assertEquals("Wind text must not be ellipsized", 0, node.layout.getEllipsisCount(0))
                            }
                            if (node.text.toString() in listOf("30%", "70%")) {
                                assertEquals("Rain probability is blue at and above 30 percent",
                                    android.graphics.Color.parseColor(if (night) "#9CCAFF" else "#005BBB"), node.currentTextColor)
                            }
                            if (node.text.toString() == "29%") {
                                assertNotEquals(android.graphics.Color.parseColor(if (night) "#9CCAFF" else "#005BBB"), node.currentTextColor)
                            }
                        }
                        if (node.contentDescription == "Return to current hour") nowControlCount++
                        if (node.contentDescription?.contains("percent chance of precipitation") == true) {
                            forecastTileCount++
                            if (node.contentDescription.startsWith("Now,")) currentHourTileCount++
                        }
                        if (node.isClickable) {
                            assertTrue("Touch target width ${node.width}", node.width >= 48)
                            assertTrue("Touch target height ${node.height}", node.height >= 48)
                        }
                        if (node is ViewGroup) for (index in 0 until node.childCount) checkTargets(node.getChildAt(index))
                    }
                    checkTargets(view)
                    assertEquals(io.github.tychomagnetic.metterweather.widget.widgetHourCount(width.toFloat()), forecastTileCount)
                    assertEquals("Every hour displays average wind speed and direction", forecastTileCount, windRowCount)
                    assertEquals(if (width >= 260 && height >= 132) 1 else 0, nowControlCount)
                    assertEquals("Short layouts must return to Now despite a saved page offset", 1, currentHourTileCount)
                    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                    view.draw(android.graphics.Canvas(bitmap))
                    val file = java.io.File("build/widget-previews/widget-${width}x${height}-${if (night) "dark" else "light"}.png")
                    file.parentFile.mkdirs()
                    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
