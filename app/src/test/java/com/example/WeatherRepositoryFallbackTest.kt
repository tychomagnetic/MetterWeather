package io.github.tychomagnetic.metterweather

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.tychomagnetic.metterweather.data.local.PreferencesManager
import io.github.tychomagnetic.metterweather.data.model.ApiDiagnosticSource
import io.github.tychomagnetic.metterweather.data.model.ForecastSource
import io.github.tychomagnetic.metterweather.data.model.LocationItem
import io.github.tychomagnetic.metterweather.data.model.MetOfficeGeometry
import io.github.tychomagnetic.metterweather.data.model.MetOfficeHourlyFeature
import io.github.tychomagnetic.metterweather.data.model.MetOfficeHourlyProperties
import io.github.tychomagnetic.metterweather.data.model.MetOfficeHourlyResponse
import io.github.tychomagnetic.metterweather.data.model.MetOfficeHourlyTimeSeriesItem
import io.github.tychomagnetic.metterweather.data.model.MetOfficeLocation
import io.github.tychomagnetic.metterweather.data.model.OpenMeteoResponse
import io.github.tychomagnetic.metterweather.data.model.OpenMeteoCurrent
import io.github.tychomagnetic.metterweather.data.model.OpenMeteoDaily
import io.github.tychomagnetic.metterweather.data.model.OpenMeteoHourly
import io.github.tychomagnetic.metterweather.data.model.WeatherDataSource
import io.github.tychomagnetic.metterweather.data.remote.MetOfficeApiService
import io.github.tychomagnetic.metterweather.data.remote.MetOfficeBpfApiService
import io.github.tychomagnetic.metterweather.data.remote.OpenMeteoApiService
import io.github.tychomagnetic.metterweather.data.repository.WeatherRepository
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeatherRepositoryFallbackTest {

    @Test
    fun `live BPF mixed cadence response trims endpoint without requesting Spot`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val prefs = PreferencesManager(context).apply {
            setForecastSource(ForecastSource.MET_OFFICE_BPF)
            setBpfApiKey("bpf-test-key")
            setApiKey("spot-test-key")
        }
        val spotApi = SuccessfulSpotApi()
        val openApi = RecordingOpenMeteoApi()
        val repository = WeatherRepository(
            preferencesManager = prefs,
            metOfficeApi = spotApi,
            metOfficeBpfApi = CapturedBpfApi(),
            openMeteoApi = openApi
        )

        val report = repository.getWeatherReport(LocationItem.DEFAULT_LOCATIONS.first()).getOrThrow()

        assertEquals(WeatherDataSource.MET_OFFICE_BPF, report.dataSource)
        assertEquals(null, report.partialFallbackSource)
        assertFalse(spotApi.hourlyRequested)
        assertFalse(openApi.requested)
        assertEquals(168, report.hourly.size)
        assertEquals("2099-09-15T14:00:00Z", report.hourly.last().fullTime)
        assertTrue(report.hourly.all { it.weatherCode.code in 0..30 })
        assertEquals(7, report.daily.size)
        assertEquals(1, report.hourly.first().precipitationPeriod?.hours)
        assertEquals(3, report.hourly.last().precipitationPeriod?.hours)
        assertTrue(report.hourly.all { it.precipitationPeriod != null })
    }

    // London response captured on 2026-09-08 with the production request's
    // parameters. Metadata removed and year shifted to keep it in the future;
    // all source timestamps, bounds, axes and values otherwise preserved.
    private class CapturedBpfApi : MetOfficeBpfApiService {
        private fun payload(name: String): Response<ResponseBody> = Response.success(
            checkNotNull(javaClass.getResource("/bpf/$name.json")).readText().toResponseBody()
        )

        override suspend fun getUkPercentiles(
            coords: String, parameterNames: String, datetime: String, apiKey: String
        ) = payload("percentiles")

        override suspend fun getUkProbabilities(
            coords: String, parameterNames: String, datetime: String, apiKey: String
        ) = payload("probabilities")

        override suspend fun getCollections(apiKey: String): Response<ResponseBody> =
            error("Collection discovery is not needed for forecast refresh")
    }

    @Test
    fun `BPF diagnostic never follows the forecast fallback chain`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val prefs = PreferencesManager(context).apply {
            setBpfApiKey("bpf-test-key")
            setApiKey("spot-test-key")
        }
        val spotApi = SuccessfulSpotApi()
        val openApi = RecordingOpenMeteoApi()
        val repository = WeatherRepository(
            preferencesManager = prefs,
            metOfficeApi = spotApi,
            metOfficeBpfApi = FailingBpfApi(),
            openMeteoApi = openApi
        )

        val result = repository.runApiDiagnostic(
            ApiDiagnosticSource.MET_OFFICE_BPF,
            LocationItem.DEFAULT_LOCATIONS.first()
        )

        assertEquals(WeatherDataSource.MET_OFFICE_BPF, result.dataSource)
        assertEquals(404, result.httpStatusCode)
        assertTrue(result.errorDetails?.contains("BPF percentile request failed") == true)
        assertFalse(spotApi.hourlyRequested)
        assertFalse(openApi.requested)
    }

    @Test
    fun `isolated BPF hole is filled from Spot without replacing BPF report`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val prefs = PreferencesManager(context).apply {
            setForecastSource(ForecastSource.MET_OFFICE_BPF)
            setBpfApiKey("bpf-test-key")
            setApiKey("spot-test-key")
        }
        val spotApi = SuccessfulSpotApi()
        val openApi = RecordingOpenMeteoApi()
        val repository = WeatherRepository(
            preferencesManager = prefs,
            metOfficeApi = spotApi,
            metOfficeBpfApi = WeatherCodeHoleBpfApi(),
            openMeteoApi = openApi
        )

        val report = repository.getWeatherReport(LocationItem.DEFAULT_LOCATIONS.first()).getOrThrow()

        assertEquals(WeatherDataSource.MET_OFFICE_BPF, report.dataSource)
        assertEquals(WeatherDataSource.MET_OFFICE_DATAHUB, report.partialFallbackSource)
        assertEquals(7, report.hourly.single().weatherCode.code)
        assertEquals(80, report.hourly.single().precipitationChance)
        assertTrue(spotApi.hourlyRequested)
        assertFalse(openApi.requested)
    }

    @Test
    fun `three hourly Spot tail fills every hour of a BPF gap across a month boundary`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val prefs = PreferencesManager(context).apply {
            setForecastSource(ForecastSource.MET_OFFICE_BPF)
            setBpfApiKey("bpf-test-key")
            setApiKey("spot-test-key")
        }
        val openApi = RecordingOpenMeteoApi()
        val repository = WeatherRepository(
            preferencesManager = prefs,
            metOfficeApi = RollingTailSpotApi(),
            metOfficeBpfApi = MonthBoundaryHoleBpfApi(),
            openMeteoApi = openApi
        )

        val report = repository.getWeatherReport(LocationItem.DEFAULT_LOCATIONS.first()).getOrThrow()

        assertEquals(WeatherDataSource.MET_OFFICE_BPF, report.dataSource)
        assertEquals(WeatherDataSource.MET_OFFICE_DATAHUB, report.partialFallbackSource)
        listOf("00", "01", "02").forEach { hour ->
            val item = report.hourly.first { it.fullTime == "2099-09-01T$hour:00:00Z" }
            assertEquals(13, item.weatherCode.code)
            // BPF probability is complete here, so only the missing weather
            // code should be sourced from Spot.
            assertEquals(20, item.precipitationChance)
        }
        assertFalse(openApi.requested)
    }

    @Test
    fun `incomplete BPF probability response falls back to Spot`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val prefs = PreferencesManager(context).apply {
            setForecastSource(ForecastSource.MET_OFFICE_BPF)
            setBpfApiKey("bpf-test-key")
            setApiKey("spot-test-key")
        }
        val spotApi = SuccessfulSpotApi()
        val repository = WeatherRepository(
            preferencesManager = prefs,
            metOfficeApi = spotApi,
            metOfficeBpfApi = MissingProbabilityBpfApi(),
            openMeteoApi = RecordingOpenMeteoApi()
        )

        val result = repository.getWeatherReport(LocationItem.DEFAULT_LOCATIONS.first())

        assertTrue(result.isSuccess)
        assertEquals(WeatherDataSource.MET_OFFICE_DATAHUB, result.getOrThrow().dataSource)
        assertTrue(spotApi.hourlyRequested)
    }

    @Test
    fun `Open-Meteo uses current probability UV MSL pressure and returned timezone`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val prefs = PreferencesManager(context).apply {
            setForecastSource(ForecastSource.OPEN_METEO)
        }
        val requestedLocation = LocationItem(
            id = "gps_test",
            name = "GPS test",
            latitude = 35.0456,
            longitude = -85.3097,
            timezone = "Europe/London",
            isCurrentLocation = true
        )
        val repository = WeatherRepository(
            preferencesManager = prefs,
            metOfficeApi = SuccessfulSpotApi(),
            metOfficeBpfApi = FailingBpfApi(),
            openMeteoApi = SuccessfulOpenMeteoApi()
        )

        val report = repository.getWeatherReport(requestedLocation).getOrThrow()

        assertEquals(73, report.current.precipitationChance)
        assertEquals(3, report.current.uvIndex)
        assertEquals(1019.2, report.current.pressureHpa, 0.001)
        assertEquals("America/New_York", report.location.timezone)
        assertEquals(5, report.hourly.first().precipitationChance)
        assertEquals(4, report.hourly.first().uvIndex)
    }

    @Test
    fun `failed BPF request falls back to Spot before open data`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val prefs = PreferencesManager(context).apply {
            setForecastSource(ForecastSource.MET_OFFICE_BPF)
            setBpfApiKey("bpf-test-key")
            setApiKey("spot-test-key")
        }
        val spotApi = SuccessfulSpotApi()
        val openApi = RecordingOpenMeteoApi()
        val location = LocationItem(
            id = "outside_bpf",
            name = "Outside BPF",
            country = "France",
            latitude = 48.8566,
            longitude = 2.3522,
            timezone = "Europe/Paris"
        )
        val repository = WeatherRepository(
            preferencesManager = prefs,
            metOfficeApi = spotApi,
            metOfficeBpfApi = FailingBpfApi(),
            openMeteoApi = openApi
        )

        val result = repository.getWeatherReport(location)

        assertTrue(result.isSuccess)
        assertEquals(WeatherDataSource.MET_OFFICE_DATAHUB, result.getOrThrow().dataSource)
        assertEquals(location, result.getOrThrow().location)
        assertTrue(spotApi.hourlyRequested)
        assertFalse(openApi.requested)
    }

    @Test
    fun `distant Met Office grid response is rejected instead of relabelled`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val prefs = PreferencesManager(context).apply {
            setForecastSource(ForecastSource.MET_OFFICE_SPOT)
            setApiKey("spot-test-key")
        }
        val spotApi = SuccessfulSpotApi(
            resolvedLatitude = 51.5074,
            resolvedLongitude = -0.1278
        )
        val openApi = RecordingOpenMeteoApi()
        val chattanooga = LocationItem(
            id = "chattanooga_us",
            name = "Chattanooga",
            country = "United States",
            latitude = 35.0456,
            longitude = -85.3097,
            timezone = "America/New_York"
        )
        val repository = WeatherRepository(
            preferencesManager = prefs,
            metOfficeApi = spotApi,
            metOfficeBpfApi = FailingBpfApi(),
            openMeteoApi = openApi
        )

        val result = repository.getWeatherReport(chattanooga)

        assertTrue(result.isFailure)
        assertTrue(spotApi.hourlyRequested)
        assertTrue(openApi.requested)
    }

    @Test
    fun `sparse global Spot site within two hundred kilometres is accepted`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("met_office_weather_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val prefs = PreferencesManager(context).apply {
            setForecastSource(ForecastSource.MET_OFFICE_SPOT)
            setApiKey("spot-test-key")
        }
        val spotApi = SuccessfulSpotApi(
            resolvedLatitude = 35.06,
            resolvedLongitude = -86.6
        )
        val openApi = RecordingOpenMeteoApi()
        val chattanooga = LocationItem(
            id = "chattanooga_us",
            name = "Chattanooga",
            country = "United States",
            latitude = 35.0456,
            longitude = -85.3097,
            timezone = "America/New_York"
        )
        val repository = WeatherRepository(
            preferencesManager = prefs,
            metOfficeApi = spotApi,
            metOfficeBpfApi = FailingBpfApi(),
            openMeteoApi = openApi
        )

        val result = repository.getWeatherReport(chattanooga)

        assertTrue(result.isSuccess)
        assertEquals(WeatherDataSource.MET_OFFICE_DATAHUB, result.getOrThrow().dataSource)
        assertFalse(openApi.requested)
    }

    private class FailingBpfApi : MetOfficeBpfApiService {
        private fun failure(): Response<ResponseBody> =
            Response.error(404, "Outside BPF coverage".toResponseBody())

        override suspend fun getUkPercentiles(
            coords: String,
            parameterNames: String,
            datetime: String,
            apiKey: String
        ) = failure()

        override suspend fun getUkProbabilities(
            coords: String,
            parameterNames: String,
            datetime: String,
            apiKey: String
        ) = failure()

        override suspend fun getCollections(apiKey: String) = failure()
    }

    private class MissingProbabilityBpfApi : MetOfficeBpfApiService {
        override suspend fun getUkPercentiles(
            coords: String,
            parameterNames: String,
            datetime: String,
            apiKey: String
        ): Response<ResponseBody> = Response.success("{}".toResponseBody())

        override suspend fun getUkProbabilities(
            coords: String,
            parameterNames: String,
            datetime: String,
            apiKey: String
        ): Response<ResponseBody> = Response.error(503, "probabilities unavailable".toResponseBody())

        override suspend fun getCollections(apiKey: String): Response<ResponseBody> =
            Response.success("{}".toResponseBody())
    }

    private class WeatherCodeHoleBpfApi : MetOfficeBpfApiService {
        private val time = "2026-08-17T12:00:00Z"

        override suspend fun getUkPercentiles(
            coords: String,
            parameterNames: String,
            datetime: String,
            apiKey: String
        ): Response<ResponseBody> = Response.success(
            """
            {
              "type":"CoverageCollection",
              "coverages":[
                ${coverage("airTemperature1p5m", 294.15, includeLocation = true)},
                ${coverage("feelsLikeTemperature1p5m", 293.15)},
                ${coverage("relativeHumidity1p5m", 0.61)},
                ${coverage("windSpeed10m", 3.0)},
                ${coverage("windSpeedOfGust10mMaximumPt01h", 5.0)},
                ${coverage("windSpeedOfGust10mMaximumPt03h", 5.0)},
                ${coverage("windFromDirection10mMean", 220.0)},
                ${coverage("airPressureAtSeaLevel", 101500.0)},
                ${coverage("visibilityInAir1p5m", 20000.0)},
                ${coverage("ultravioletIndex", 4.0)}
              ]
            }
            """.trimIndent().toResponseBody()
        )

        override suspend fun getUkProbabilities(
            coords: String,
            parameterNames: String,
            datetime: String,
            apiKey: String
        ): Response<ResponseBody> = Response.success(
            """
            {
              "type":"CoverageCollection",
              "coverages":[
                ${probabilityCoverage("probabilityOfLweThicknessOfPrecipitationAmountAboveThresholdSumPt01h")},
                ${probabilityCoverage("probabilityOfLweThicknessOfPrecipitationAmountAboveThresholdSumPt03h")}
              ]
            }
            """.trimIndent().toResponseBody()
        )

        override suspend fun getCollections(apiKey: String): Response<ResponseBody> =
            Response.success("{}".toResponseBody())

        private fun coverage(parameter: String, value: Double, includeLocation: Boolean = false): String {
            val locationAxes = if (includeLocation) {
                "\"x\":{\"values\":[0.0]},\"y\":{\"values\":[51.5]},\"locationId\":{\"values\":[\"test\"]},"
            } else ""
            return """
                {
                  "type":"Coverage",
                  "domain":{"axes":{$locationAxes"t":{"values":["$time"]}}},
                  "ranges":{"$parameter":{"axisNames":["t"],"shape":[1],"values":[$value]}}
                }
            """.trimIndent()
        }

        private fun probabilityCoverage(parameter: String): String {
            val intervalEnd = if (parameter.endsWith("Pt03h")) {
                "2026-08-17T15:00:00Z"
            } else {
                "2026-08-17T13:00:00Z"
            }
            return """
                {
                  "type":"Coverage",
                  "domain":{"axes":{"t":{"values":["$intervalEnd"],"bounds":["$time","$intervalEnd"]},"${parameter}Values":{"values":["${if (parameter.endsWith("Pt03h")) ">3.0E-4" else ">1.0E-4"}"]}}},
                  "ranges":{"$parameter":{"axisNames":["${parameter}Values","t"],"shape":[1,1],"values":[0.8]}}
                }
            """.trimIndent()
        }
    }

    private class MonthBoundaryHoleBpfApi : MetOfficeBpfApiService {
        private val hours = (21..23).map { "2099-08-31T${it}:00:00Z" } +
            (0..5).map { "2099-09-01T${it.toString().padStart(2, '0')}:00:00Z" }
        private val intervalEnds = hours.map { timestamp ->
            val millis = java.time.Instant.parse(timestamp).toEpochMilli() + 60L * 60L * 1000L
            java.time.Instant.ofEpochMilli(millis).toString()
        }
        private val hourlyBounds = hours.zip(intervalEnds).flatMap { (start, end) -> listOf(start, end) }

        override suspend fun getUkPercentiles(
            coords: String,
            parameterNames: String,
            datetime: String,
            apiKey: String
        ): Response<ResponseBody> = Response.success(
            """
            {
              "type":"CoverageCollection",
              "coverages":[
                ${seriesCoverage("airTemperature1p5m", 294.15, includeLocation = true)},
                ${seriesCoverage("feelsLikeTemperature1p5m", 293.15)},
                ${seriesCoverage("relativeHumidity1p5m", 0.61)},
                ${seriesCoverage("windSpeed10m", 3.0)},
                ${intervalCoverage("windSpeedOfGust10mMaximumPt01h", intervalEnds, hourlyBounds, List(hours.size) { 5.0 })},
                ${seriesCoverage("windFromDirection10mMean", 220.0)},
                ${seriesCoverage("airPressureAtSeaLevel", 101500.0)},
                ${seriesCoverage("visibilityInAir1p5m", 20000.0)},
                ${weatherCodeCoverage()},
                ${seriesCoverage("ultravioletIndex", 4.0)}
              ]
            }
            """.trimIndent().toResponseBody()
        )

        override suspend fun getUkProbabilities(
            coords: String,
            parameterNames: String,
            datetime: String,
            apiKey: String
        ): Response<ResponseBody> = Response.success(
            """
            {
              "type":"CoverageCollection",
              "coverages":[
                ${probabilityCoverage("probabilityOfLweThicknessOfPrecipitationAmountAboveThresholdSumPt01h", intervalEnds, hourlyBounds)},
                ${probabilityCoverage(
                    "probabilityOfLweThicknessOfPrecipitationAmountAboveThresholdSumPt03h",
                    listOf("2099-09-01T00:00:00Z", "2099-09-01T03:00:00Z", "2099-09-01T06:00:00Z"),
                    listOf(
                        "2099-08-31T21:00:00Z", "2099-09-01T00:00:00Z",
                        "2099-09-01T00:00:00Z", "2099-09-01T03:00:00Z",
                        "2099-09-01T03:00:00Z", "2099-09-01T06:00:00Z"
                    )
                )}
              ]
            }
            """.trimIndent().toResponseBody()
        )

        override suspend fun getCollections(apiKey: String): Response<ResponseBody> =
            Response.success("{}".toResponseBody())

        private fun seriesCoverage(parameter: String, value: Double, includeLocation: Boolean = false): String {
            val locationAxes = if (includeLocation) {
                "\"x\":{\"values\":[-0.1278]},\"y\":{\"values\":[51.5074]},\"locationId\":{\"values\":[\"test\"]},"
            } else ""
            return """
                {
                  "type":"Coverage",
                  "domain":{"axes":{$locationAxes"t":{"values":${jsonStrings(hours)}}}},
                  "ranges":{"$parameter":{"axisNames":["t"],"shape":[${hours.size}],"values":[${List(hours.size) { value }.joinToString(",")}]}}
                }
            """.trimIndent()
        }

        private fun weatherCodeCoverage(): String = intervalCoverage(
            parameter = "weatherCodePt03h",
            times = listOf("2099-09-01T00:00:00Z", "2099-09-01T06:00:00Z"),
            bounds = listOf(
                "2099-08-31T21:00:00Z", "2099-09-01T00:00:00Z",
                "2099-09-01T03:00:00Z", "2099-09-01T06:00:00Z"
            ),
            values = listOf(3.0, 7.0)
        )

        private fun intervalCoverage(
            parameter: String,
            times: List<String>,
            bounds: List<String>,
            values: List<Double>
        ): String = """
            {
              "type":"Coverage",
              "domain":{"axes":{"t":{"values":${jsonStrings(times)},"bounds":${jsonStrings(bounds)}}}},
              "ranges":{"$parameter":{"axisNames":["t"],"shape":[${times.size}],"values":[${values.joinToString(",")}]}}
            }
        """.trimIndent()

        private fun probabilityCoverage(parameter: String, times: List<String>, bounds: List<String>): String = """
            {
              "type":"Coverage",
              "domain":{"axes":{"t":{"values":${jsonStrings(times)},"bounds":${jsonStrings(bounds)}},"${parameter}Values":{"values":["${if (parameter.endsWith("Pt03h")) ">3.0E-4" else ">1.0E-4"}"]}}},
              "ranges":{"$parameter":{"axisNames":["${parameter}Values","t"],"shape":[1,${times.size}],"values":[${List(times.size) { 0.2 }.joinToString(",")}]}}
            }
        """.trimIndent()

        private fun jsonStrings(values: List<String>): String =
            values.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
    }

    private class RollingTailSpotApi : MetOfficeApiService {
        private fun response(timeSeries: List<MetOfficeHourlyTimeSeriesItem>) = Response.success(
            MetOfficeHourlyResponse(
                features = listOf(
                    MetOfficeHourlyFeature(
                        geometry = MetOfficeGeometry(coordinates = listOf(-0.1278, 51.5074)),
                        properties = MetOfficeHourlyProperties(
                            location = MetOfficeLocation("London"),
                            modelRunDate = "2099-08-31T12:00:00Z",
                            timeSeries = timeSeries
                        )
                    )
                )
            )
        )

        private fun item(time: String, code: Int, probability: Int) = MetOfficeHourlyTimeSeriesItem(
            time = time,
            maxScreenAirTemp = 15.0,
            minScreenAirTemp = 13.0,
            feelsLikeTemperature = 12.5,
            screenRelativeHumidity = 77.0,
            significantWeatherCode = code,
            probOfPrecipitation = probability,
            windSpeed10m = 3.5,
            windGustSpeed10m = 6.5,
            windDirectionFrom10m = 240,
            visibility = 20_000,
            mslp = 101_400.0,
            uvIndex = 0
        )

        override suspend fun getPointHourly(
            latitude: Double,
            longitude: Double,
            includeLocationName: Boolean,
            excludeParameterMetadata: Boolean,
            apiKey: String,
            clientId: String?,
            clientSecret: String?
        ) = response(listOf(item("2099-08-31T21:00:00Z", 3, 5)))

        override suspend fun getPointThreeHourly(
            latitude: Double,
            longitude: Double,
            includeLocationName: Boolean,
            excludeParameterMetadata: Boolean,
            apiKey: String,
            clientId: String?,
            clientSecret: String?
        ) = response(
            listOf(
                item("2099-09-01T00:00:00Z", 13, 72),
                item("2099-09-01T03:00:00Z", 7, 20)
            )
        )

        override suspend fun getPointDaily(
            latitude: Double,
            longitude: Double,
            includeLocationName: Boolean,
            excludeParameterMetadata: Boolean,
            apiKey: String,
            clientId: String?,
            clientSecret: String?
        ) = Response.error<io.github.tychomagnetic.metterweather.data.model.MetOfficeDailyResponse>(404, "unused".toResponseBody())
    }

    private class SuccessfulSpotApi(
        private val resolvedLatitude: Double? = null,
        private val resolvedLongitude: Double? = null
    ) : MetOfficeApiService {
        var hourlyRequested = false

        override suspend fun getPointHourly(
            latitude: Double,
            longitude: Double,
            includeLocationName: Boolean,
            excludeParameterMetadata: Boolean,
            apiKey: String,
            clientId: String?,
            clientSecret: String?
        ): Response<MetOfficeHourlyResponse> {
            hourlyRequested = true
            return Response.success(
                MetOfficeHourlyResponse(
                    features = listOf(
                        MetOfficeHourlyFeature(
                            geometry = MetOfficeGeometry(
                                coordinates = listOf(
                                    resolvedLongitude ?: longitude,
                                    resolvedLatitude ?: latitude
                                )
                            ),
                            properties = MetOfficeHourlyProperties(
                                location = MetOfficeLocation("Outside BPF"),
                                modelRunDate = "2026-08-17T00:00:00Z",
                                timeSeries = listOf(
                                    MetOfficeHourlyTimeSeriesItem(
                                        time = "2026-08-17T12:00:00Z",
                                        screenTemperature = 21.0,
                                        feelsLikeTemperature = 20.0,
                                        screenRelativeHumidity = 60.0,
                                        significantWeatherCode = 7,
                                        probOfPrecipitation = 20,
                                        windSpeed10m = 3.0,
                                        windGustSpeed10m = 5.0,
                                        windDirectionFrom10m = 220,
                                        visibility = 20_000,
                                        mslp = 101_500.0,
                                        uvIndex = 4
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }

        override suspend fun getPointThreeHourly(
            latitude: Double,
            longitude: Double,
            includeLocationName: Boolean,
            excludeParameterMetadata: Boolean,
            apiKey: String,
            clientId: String?,
            clientSecret: String?
        ): Response<MetOfficeHourlyResponse> = Response.error(404, "unused".toResponseBody())

        override suspend fun getPointDaily(
            latitude: Double,
            longitude: Double,
            includeLocationName: Boolean,
            excludeParameterMetadata: Boolean,
            apiKey: String,
            clientId: String?,
            clientSecret: String?
        ) = Response.error<io.github.tychomagnetic.metterweather.data.model.MetOfficeDailyResponse>(404, "unused".toResponseBody())
    }

    private class RecordingOpenMeteoApi : OpenMeteoApiService {
        var requested = false

        override suspend fun getForecast(
            latitude: Double,
            longitude: Double,
            current: String,
            hourly: String,
            daily: String,
            forecastDays: Int,
            timezone: String,
            windSpeedUnit: String
        ): Response<OpenMeteoResponse> {
            requested = true
            return Response.error(500, "should not be called".toResponseBody())
        }
    }

    private class SuccessfulOpenMeteoApi : OpenMeteoApiService {
        override suspend fun getForecast(
            latitude: Double,
            longitude: Double,
            current: String,
            hourly: String,
            daily: String,
            forecastDays: Int,
            timezone: String,
            windSpeedUnit: String
        ): Response<OpenMeteoResponse> = Response.success(
            OpenMeteoResponse(
                latitude = latitude,
                longitude = longitude,
                timezone = "America/New_York",
                current = OpenMeteoCurrent(
                    time = "2026-08-18T12:15",
                    temperature2m = 24.0,
                    relativeHumidity2m = 60,
                    apparentTemperature = 25.0,
                    precipitationProbability = 73,
                    weatherCode = 2,
                    pressureMsl = 1019.2,
                    windSpeed10m = 8.0,
                    windDirection10m = 210,
                    windGusts10m = 14.0,
                    visibility = 20_000.0,
                    uvIndex = 2.6,
                    isDay = 1
                ),
                hourly = OpenMeteoHourly(
                    time = listOf("2026-08-18T00:00"),
                    temperature2m = listOf(20.0),
                    relativeHumidity2m = listOf(70),
                    apparentTemperature = listOf(20.0),
                    precipitationProbability = listOf(5),
                    weatherCode = listOf(1),
                    pressureMsl = listOf(1018.0),
                    visibility = listOf(20_000.0),
                    windSpeed10m = listOf(5.0),
                    windDirection10m = listOf(180),
                    uvIndex = listOf(3.6),
                    isDay = listOf(0)
                ),
                daily = OpenMeteoDaily(
                    time = listOf("2026-08-18"),
                    weatherCode = listOf(2),
                    temperature2mMax = listOf(28.0),
                    temperature2mMin = listOf(18.0),
                    sunrise = listOf("2026-08-18T06:10"),
                    sunset = listOf("2026-08-18T19:20"),
                    uvIndexMax = listOf(5.6),
                    precipitationProbabilityMax = listOf(73),
                    windGusts10mMax = listOf(18.0)
                )
            )
        )
    }
}
