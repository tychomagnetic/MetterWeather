package io.github.tychomagnetic.metterweather

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import io.github.tychomagnetic.metterweather.data.local.PreferencesManager
import io.github.tychomagnetic.metterweather.data.model.ThemeMode
import io.github.tychomagnetic.metterweather.ui.theme.MetOfficeWeatherTheme
import io.github.tychomagnetic.metterweather.ui.theme.MetterColors
import io.github.tychomagnetic.metterweather.ui.theme.MetterTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class ThemeModeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @After
    fun clearPreferences() {
        context.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun themeModePersists() {
        val preferences = PreferencesManager(context)

        preferences.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, PreferencesManager(context).getThemeMode())
    }

    @Test
    fun darkThemeProvidesDarkSemanticPalette() {
        lateinit var colors: MetterColors

        composeRule.setContent {
            MetOfficeWeatherTheme(themeMode = ThemeMode.DARK) {
                colors = MetterTheme.colors
            }
        }

        composeRule.runOnIdle {
            assertEquals(Color(0xFF141218), colors.canvas)
            assertEquals(Color(0xFFE6E0E9), colors.textPrimary)
            assertEquals(Color(0xFFD0BCFF), colors.primary)
            assertEquals(Color(0xFF381E72), colors.onPrimary)
        }
    }

    @Test
    fun lightThemeProvidesLightSemanticPalette() {
        lateinit var colors: MetterColors

        composeRule.setContent {
            MetOfficeWeatherTheme(themeMode = ThemeMode.LIGHT) {
                colors = MetterTheme.colors
            }
        }

        composeRule.runOnIdle {
            assertEquals(Color(0xFFFEF7FF), colors.canvas)
            assertEquals(Color(0xFF1C1B1F), colors.textPrimary)
            assertEquals(Color(0xFF6750A4), colors.primary)
            assertEquals(Color.White, colors.onPrimary)
        }
    }
}
