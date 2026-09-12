package io.github.tychomagnetic.metterweather

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.github.tychomagnetic.metterweather.data.model.*
import io.github.tychomagnetic.metterweather.data.util.BpfPrecipitationUtils
import io.github.tychomagnetic.metterweather.ui.precipitationDescription
import io.github.tychomagnetic.metterweather.ui.precipitationLabel
import org.junit.Assert.*
import org.junit.Test

class BpfPrecipitationTest {
    @Test fun `hourly selects 0_1mm rather than trace rain regardless of axis order`() {
        val result = BpfPrecipitationUtils.read(coverage(1, listOf(">0.0001", ">0.0", ">0.0003"),
            listOf(0.2, 0.71, 0.1, 0.3, 0.8, 0.15)), 1)
        assertEquals(0.2, result.getValue("2026-09-17T06:00:00Z").value, 0.000001)
        assertEquals(0.3, result.getValue("2026-09-17T07:00:00Z").value, 0.000001)
        assertEquals(0.1, result.values.first().period.thresholdMm, 0.000001)
    }

    @Test fun `three hourly selects 0_3mm in scientific notation and retains actual bounds`() {
        val result = BpfPrecipitationUtils.read(coverage(3, listOf(">0.0", ">1.0E-4", ">3.0E-4"),
            listOf(0.71, 0.4, 0.2, 0.9, 0.7, 0.5)), 3)
        val atEightBst = result.getValue("2026-09-17T07:00:00Z")
        assertEquals(0.2, atEightBst.value, 0.000001)
        assertEquals("2026-09-17T06:00:00Z", atEightBst.period.start)
        assertEquals("2026-09-17T09:00:00Z", atEightBst.period.end)
        assertEquals(3, atEightBst.period.hours)
        assertEquals(0.3, atEightBst.period.thresholdMm, 0.000001)
        assertEquals(atEightBst, result["2026-09-17T08:00:00Z"])
        assertEquals(0.5, result.getValue("2026-09-17T09:00:00Z").value, 0.000001)
        assertEquals(6, result.size)
    }

    @Test fun `missing threshold never substitutes trace rain`() {
        assertTrue(BpfPrecipitationUtils.read(coverage(3, listOf(">0.0", ">1.0E-4"),
            listOf(0.71, 0.4, 0.9, 0.7)), 3).isEmpty())
    }

    @Test fun `missing sample does not shift the remaining interval bounds`() {
        val result = BpfPrecipitationUtils.read(coverage(3, listOf(">3.0E-4"), listOf(null, 0.2)), 3)
        assertFalse(result.containsKey("2026-09-17T07:00:00Z"))
        assertEquals("2026-09-17T09:00:00Z", result.getValue("2026-09-17T10:00:00Z").period.start)
        assertEquals(3, result.size)
    }

    @Test fun `captured multi threshold response uses the measured precipitation axes`() {
        val collection = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            .adapter(BpfCoverageCollection::class.java)
            .fromJson(checkNotNull(javaClass.getResource("/bpf/probabilities.json")).readText())!!
        assertEquals(0.9501953, BpfPrecipitationUtils.read(collection, 1)
            .getValue("2099-09-08T14:00:00Z").value, 0.0000001)
        assertEquals(0.9501953, BpfPrecipitationUtils.read(collection, 3)
            .getValue("2099-09-08T12:00:00Z").value, 0.0000001)
    }

    @Test fun `three hour labels explain the actual local interval even on its middle card`() {
        val probability = BpfPrecipitationUtils.read(coverage(3, listOf(">3.0E-4"), listOf(0.2, 0.5)), 3)
            .getValue("2026-09-17T07:00:00Z")
        val hour = HourlyForecastItem("8 AM", "2026-09-17T07:00:00Z",
            temperatureCelsius = 18.0, feelsLikeCelsius = 18.0,
            weatherCode = MetOfficeWeatherCode.CLOUDY, precipitationChance = 20,
            windSpeedMph = 5.0, windDirectionDegrees = 180, humidityPercent = 70, uvIndex = 1,
            precipitationPeriod = probability.period)
        assertEquals("20%/3h", hour.precipitationLabel())
        val description = hour.precipitationDescription(LocationItem.DEFAULT_LOCATIONS.first())
        assertTrue(description.contains("0.3 mm"))
        assertTrue(description.contains("7"))
        assertTrue(description.contains("10"))
        assertEquals("20%", hour.copy(precipitationPeriod = probability.period.copy(hours = 1)).precipitationLabel())
    }

    private fun coverage(hours: Int, thresholds: List<String>, values: List<Double?>): BpfCoverageCollection {
        val parameter = "probabilityOfLweThicknessOfPrecipitationAmountAboveThresholdSumPt0${hours}h"
        val start = java.time.Instant.parse("2026-09-17T06:00:00Z")
        val boundary1 = start.plusSeconds(hours * 3600L).toString()
        val boundary2 = start.plusSeconds(hours * 7200L).toString()
        return BpfCoverageCollection(coverages = listOf(BpfCoverage(
            domain = BpfDomain(mapOf(
                "t" to BpfAxis(listOf(boundary1, boundary2), listOf(start.toString(), boundary1, boundary1, boundary2)),
                "${parameter}Values" to BpfAxis(thresholds)
            )),
            ranges = mapOf(parameter to BpfRange(listOf("${parameter}Values", "t"), listOf(thresholds.size, 2), values))
        )))
    }
}
