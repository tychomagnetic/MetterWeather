package io.github.tychomagnetic.metterweather

import io.github.tychomagnetic.metterweather.data.model.LocationItem
import io.github.tychomagnetic.metterweather.data.model.MetOfficeWeatherCode
import io.github.tychomagnetic.metterweather.data.util.BpfDailyWeatherUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BpfDailyWeatherUtilsTest {
    private val location = LocationItem.DEFAULT_LOCATIONS.first()

    @Test fun `BST summary uses local midnight rather than UTC midnight`() {
        val result = BpfDailyWeatherUtils.select(mapOf(
            "2099-07-01T23:00:00Z" to 12.0,
            "2099-07-02T00:00:00Z" to 3.0
        ), location)
        assertEquals(mapOf("2099-07-02" to MetOfficeWeatherCode.LIGHT_RAIN), result)
    }

    @Test fun `GMT accepts a midnight UTC summary`() {
        assertEquals(mapOf("2099-01-02" to MetOfficeWeatherCode.LIGHT_RAIN),
            BpfDailyWeatherUtils.select(mapOf("2099-01-02T00:00:00Z" to 12.0), location))
    }

    @Test fun `clock change days cannot use a rolling 24 hour summary`() {
        assertTrue(BpfDailyWeatherUtils.select(mapOf(
            "2026-03-29T00:00:00Z" to 12.0,
            "2026-10-24T23:00:00Z" to 12.0
        ), location).isEmpty())
    }

    @Test fun `missing and invalid summaries leave derivation available`() {
        assertTrue(BpfDailyWeatherUtils.select(emptyMap(), location).isEmpty())
        for (code in listOf(-1.0, 31.0, 12.5, Double.NaN)) {
            assertTrue(BpfDailyWeatherUtils.select(mapOf("2099-01-02T00:00:00Z" to code), location).isEmpty())
        }
    }
}
