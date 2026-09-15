package io.github.tychomagnetic.metterweather

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import io.github.tychomagnetic.metterweather.data.model.CurrentWeather
import io.github.tychomagnetic.metterweather.data.model.MetOfficeWeatherCode
import io.github.tychomagnetic.metterweather.data.model.TemperatureUnit
import io.github.tychomagnetic.metterweather.data.model.WindSpeedUnit
import io.github.tychomagnetic.metterweather.ui.components.HeroWeatherCard
import io.github.tychomagnetic.metterweather.ui.theme.MetOfficeWeatherTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HeroWeatherCardTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dailySummaryPromotesHighLowAndMaxWindInsteadOfPointInTimeTemperature() {
        composeTestRule.setContent {
            MetOfficeWeatherTheme {
                HeroWeatherCard(
                    current = weather(),
                    periodLabel = "Tomorrow",
                    rainLabel = "Peak rain",
                    isDailySummary = true,
                    tempUnit = TemperatureUnit.CELSIUS,
                    windUnit = WindSpeedUnit.MPH
                )
            }
        }

        composeTestRule.onNodeWithText("HIGH").assertIsDisplayed()
        composeTestRule.onNodeWithText("24°").assertIsDisplayed()
        composeTestRule.onNodeWithText("LOW").assertIsDisplayed()
        composeTestRule.onNodeWithText("15°").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithText("18°").fetchSemanticsNodes().isEmpty())
        assertTrue(
            composeTestRule.onAllNodesWithText("Feels like", substring = true)
                .fetchSemanticsNodes().isEmpty()
        )
        composeTestRule.onNodeWithText("Max 14 mph WSW").assertIsDisplayed()
    }

    private fun weather() = CurrentWeather(
        temperatureCelsius = 18.0,
        feelsLikeCelsius = 17.0,
        weatherCode = MetOfficeWeatherCode.OVERCAST,
        maxTempCelsius = 24.0,
        minTempCelsius = 15.0,
        humidityPercent = 75,
        windSpeedMph = 14.0,
        windGustMph = 22.0,
        windDirectionDegrees = 240,
        precipitationChance = 76,
        uvIndex = 5,
        visibilityMeters = 20_000,
        pressureHpa = 1009.0,
        timestamp = "2026-08-17T14:00:00Z",
        isNight = false
    )
}
