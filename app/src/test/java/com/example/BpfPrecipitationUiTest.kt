package io.github.tychomagnetic.metterweather

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.tychomagnetic.metterweather.data.model.*
import io.github.tychomagnetic.metterweather.ui.components.HourlyItemCard
import io.github.tychomagnetic.metterweather.ui.components.HourlyTimelineEntry
import io.github.tychomagnetic.metterweather.ui.theme.MetOfficeWeatherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class BpfPrecipitationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `compact cards distinguish hourly and three hour chances including zero`() {
        compose.setContent {
            MetOfficeWeatherTheme {
                Surface {
                    Row {
                        listOf(20 to 1, 20 to 3, 100 to 3, 0 to 3).forEach { (chance, hours) ->
                            val item = HourlyForecastItem("8 AM", "2026-09-17T07:00:00Z",
                                temperatureCelsius = 18.0, feelsLikeCelsius = 17.0,
                                weatherCode = MetOfficeWeatherCode.CLOUDY, precipitationChance = chance,
                                windSpeedMph = 5.0, windDirectionDegrees = 180, humidityPercent = 70, uvIndex = 1,
                                precipitationPeriod = PrecipitationPeriod("2026-09-17T06:00:00Z",
                                    "2026-09-17T09:00:00Z", hours, if (hours == 1) 0.1 else 0.3))
                            HourlyItemCard(HourlyTimelineEntry(item, 0, 8, false, "Thu", "2026-09-17"),
                                TemperatureUnit.CELSIUS, WindSpeedUnit.MPH, connected = true)
                        }
                    }
                }
            }
        }
        listOf("20%", "20%/3h", "100%/3h", "0%/3h").forEach {
            val node = compose.onNodeWithText(it).assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
            assertTrue("Probability must expose its text layout", layouts.isNotEmpty())
            // Compose rounds the measured width to whole pixels; its generic
            // overflow flag can fire for a fractional pixel even on "20%".
            assertFalse("Probability '$it' must fit the narrow card", layouts.any { layout ->
                layout.multiParagraph.didExceedMaxLines || layout.didOverflowHeight ||
                    layout.getLineRight(0) > layout.size.width + 1f
            })
        }
        compose.onRoot().captureRoboImage("build/outputs/bpf-precipitation-cards.png")
    }
}
