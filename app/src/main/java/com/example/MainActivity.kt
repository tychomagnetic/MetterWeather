package io.github.tychomagnetic.metterweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.tychomagnetic.metterweather.ui.WeatherScreen
import io.github.tychomagnetic.metterweather.ui.WeatherViewModel
import io.github.tychomagnetic.metterweather.ui.theme.MetOfficeWeatherTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val weatherViewModel: WeatherViewModel by viewModels()
    private var visibleClockJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MetOfficeWeatherTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WeatherScreen(viewModel = weatherViewModel)
                }
            }
        }

    }

    override fun onStart() {
        super.onStart()
        weatherViewModel.onVisibleTimeCheck()
        visibleClockJob?.cancel()
        visibleClockJob = lifecycleScope.launch {
            while (isActive) {
                val millisToNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
                delay(millisToNextMinute)
                weatherViewModel.onVisibleTimeCheck()
            }
        }
    }

    override fun onStop() {
        visibleClockJob?.cancel()
        visibleClockJob = null
        super.onStop()
    }
}
