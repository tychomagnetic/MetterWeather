package io.github.tychomagnetic.metterweather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import io.github.tychomagnetic.metterweather.data.model.CurrentWeather
import io.github.tychomagnetic.metterweather.ui.theme.BentoCanvas
import io.github.tychomagnetic.metterweather.ui.theme.BentoTile

@Composable
fun WeatherBackground(
    current: CurrentWeather?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BentoCanvas,
                        BentoTile
                    )
                )
            )
    ) {
        content()
    }
}
