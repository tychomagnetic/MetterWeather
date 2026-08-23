package io.github.tychomagnetic.metterweather.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class MetterColors(
    val canvas: Color,
    val hero: Color,
    val heroText: Color,
    val tile: Color,
    val card: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val primary: Color,
    val onPrimary: Color,
    val pillAccent: Color,
    val successContainer: Color,
    val successBorder: Color,
    val successContent: Color,
    val onSuccess: Color,
    val warningContainer: Color,
    val warningBorder: Color,
    val warningContent: Color,
    val errorContainer: Color,
    val errorBorder: Color,
    val errorContent: Color,
    val infoContainer: Color,
    val infoBorder: Color,
    val infoContent: Color,
    val onInfo: Color,
    val fieldContainer: Color
)

internal val LightMetterColors = MetterColors(
    canvas = Color(0xFFFEF7FF),
    hero = Color(0xFFEADDFF),
    heroText = Color(0xFF21005D),
    tile = Color(0xFFF3EDF7),
    card = Color.White,
    border = Color(0xFFCAC4D0),
    textPrimary = Color(0xFF1C1B1F),
    textSecondary = Color(0xFF49454F),
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    pillAccent = Color(0xFFD0BCFF),
    successContainer = Color(0xFFE8F5E9),
    successBorder = Color(0xFFA5D6A7),
    successContent = Color(0xFF1B5E20),
    onSuccess = Color.White,
    warningContainer = Color(0xFFFFF8E1),
    warningBorder = Color(0xFFFFE082),
    warningContent = Color(0xFFE65100),
    errorContainer = Color(0xFFFFEBEE),
    errorBorder = Color(0xFFFFCDD2),
    errorContent = Color(0xFFB71C1C),
    infoContainer = Color(0xFFE0F2FE),
    infoBorder = Color(0xFF7DD3FC),
    infoContent = Color(0xFF075985),
    onInfo = Color.White,
    fieldContainer = Color.White
)

internal val DarkMetterColors = MetterColors(
    canvas = Color(0xFF141218),
    hero = Color(0xFF2B2930),
    heroText = Color(0xFFEADDFF),
    tile = Color(0xFF211F26),
    card = Color(0xFF2B2930),
    border = Color(0xFF49454F),
    textPrimary = Color(0xFFE6E0E9),
    textSecondary = Color(0xFFCAC4D0),
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    pillAccent = Color(0xFF4F378B),
    successContainer = Color(0xFF173C24),
    successBorder = Color(0xFF4C8C60),
    successContent = Color(0xFFA5D6A7),
    onSuccess = Color(0xFF173C24),
    warningContainer = Color(0xFF44340B),
    warningBorder = Color(0xFF8B6F24),
    warningContent = Color(0xFFFFE082),
    errorContainer = Color(0xFF4A1C1C),
    errorBorder = Color(0xFF8C4A4A),
    errorContent = Color(0xFFFFB4AB),
    infoContainer = Color(0xFF12354A),
    infoBorder = Color(0xFF397895),
    infoContent = Color(0xFFB3E5FC),
    onInfo = Color(0xFF12354A),
    fieldContainer = Color(0xFF2B2930)
)

internal val LocalMetterColors = staticCompositionLocalOf { LightMetterColors }
internal val LocalMetterIsDark = staticCompositionLocalOf { false }

object MetterTheme {
    val colors: MetterColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMetterColors.current

    val isDark: Boolean
        @Composable
        @ReadOnlyComposable
        get() = LocalMetterIsDark.current
}

val BentoCanvas: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.canvas
val BentoHero: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.hero
val BentoHeroText: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.heroText
val BentoTile: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.tile
val BentoCardWhite: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.card
val BentoBorder: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.border
val BentoTextPrimary: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.textPrimary
val BentoTextSecondary: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.textSecondary
val BentoPurplePrimary: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.primary
val BentoOnPrimary: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.onPrimary
val BentoPillAccent: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.pillAccent
val MetterSuccessContainer: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.successContainer
val MetterSuccessBorder: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.successBorder
val MetterSuccessContent: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.successContent
val MetterOnSuccess: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.onSuccess
val MetterWarningContainer: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.warningContainer
val MetterWarningBorder: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.warningBorder
val MetterWarningContent: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.warningContent
val MetterErrorContainer: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.errorContainer
val MetterErrorBorder: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.errorBorder
val MetterErrorContent: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.errorContent
val MetterInfoContainer: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.infoContainer
val MetterInfoBorder: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.infoBorder
val MetterInfoContent: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.infoContent
val MetterOnInfo: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.onInfo
val MetterFieldContainer: Color @Composable @ReadOnlyComposable get() = MetterTheme.colors.fieldContainer

// Accents & Weather Highlights
val SolarGold = Color(0xFFFFB300)
val RainCyan = Color(0xFF0288D1)
val WeatherIconPurple = Color(0xFF6750A4)
val WeatherIconPurpleLight = Color(0xFFD0BCFF)

// Material 3 Tokens for Bento Theme
val LightPrimary = Color(0xFF6750A4)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFEADDFF)
val LightOnPrimaryContainer = Color(0xFF21005D)
val LightSecondary = Color(0xFF625B71)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFF3EDF7)
val LightOnSecondaryContainer = Color(0xFF1D192B)
val LightSurface = Color(0xFFFEF7FF)
val LightOnSurface = Color(0xFF1C1B1F)

val DarkPrimary = Color(0xFFD0BCFF)
val DarkOnPrimary = Color(0xFF381E72)
val DarkPrimaryContainer = Color(0xFF4F378B)
val DarkOnPrimaryContainer = Color(0xFFEADDFF)
val DarkSecondary = Color(0xFFCCC2DC)
val DarkOnSecondary = Color(0xFF332D41)
val DarkSecondaryContainer = Color(0xFF4A4458)
val DarkOnSecondaryContainer = Color(0xFFE8DEF8)
val DarkSurface = Color(0xFF141218)
val DarkOnSurface = Color(0xFFE6E0E9)
