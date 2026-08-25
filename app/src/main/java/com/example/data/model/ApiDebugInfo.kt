package io.github.tychomagnetic.metterweather.data.model

import com.squareup.moshi.JsonClass

enum class ApiDiagnosticSource(val displayName: String) {
    MET_OFFICE_SPOT("Met Office Spot"),
    MET_OFFICE_BPF("Met Office BPF"),
    OPEN_METEO("Open-Meteo")
}

@JsonClass(generateAdapter = true)
data class ApiDebugInfo(
    val location: LocationItem,
    val dataSource: WeatherDataSource,
    val requestUrl: String,
    val httpStatusCode: Int,
    val httpMessage: String,
    val responseTimeMs: Long,
    val bpfPercentileRequestTimeMs: Long? = null,
    val bpfProbabilityRequestTimeMs: Long? = null,
    val bpfParsingTimeMs: Long? = null,
    val bpfTransformationTimeMs: Long? = null,
    val bpfFallbackTimeMs: Long? = null,
    val rawJsonHourly: String? = null,
    val rawJsonThreeHourly: String? = null,
    val rawJsonDaily: String? = null,
    val rawJsonFallback: String? = null,
    val lastGeocodingQuery: String? = null,
    val rawJsonGeocoding: String? = null,
    val serverResolvedLat: Double? = null,
    val serverResolvedLon: Double? = null,
    val serverResolvedName: String? = null,
    val errorDetails: String? = null,
    val timestamp: String = ""
)

data class CoordinateTestResult(
    val latitude: Double,
    val longitude: Double,
    val requestUrl: String,
    val httpStatusCode: Int,
    val responseTimeMs: Long,
    val rawJson: String,
    val serverResolvedLat: Double? = null,
    val serverResolvedLon: Double? = null,
    val serverElevation: Double? = null,
    val currentTempCelsius: Double? = null,
    val timeSeriesSample: List<String> = emptyList(),
    val isError: Boolean = false,
    val errorMessage: String? = null
)
