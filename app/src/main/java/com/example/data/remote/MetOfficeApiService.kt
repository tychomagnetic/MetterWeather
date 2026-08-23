package io.github.tychomagnetic.metterweather.data.remote

import io.github.tychomagnetic.metterweather.data.model.MetOfficeDailyResponse
import io.github.tychomagnetic.metterweather.data.model.MetOfficeHourlyResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface MetOfficeApiService {

    @GET("sitespecific/v0/point/hourly")
    suspend fun getPointHourly(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("includeLocationName") includeLocationName: Boolean = true,
        @Query("excludeParameterMetadata") excludeParameterMetadata: Boolean = true,
        @Header("apikey") apiKey: String,
        @Header("x-ibm-client-id") clientId: String? = null,
        @Header("x-ibm-client-secret") clientSecret: String? = null
    ): Response<MetOfficeHourlyResponse>

    @GET("sitespecific/v0/point/three-hourly")
    suspend fun getPointThreeHourly(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("includeLocationName") includeLocationName: Boolean = true,
        @Query("excludeParameterMetadata") excludeParameterMetadata: Boolean = true,
        @Header("apikey") apiKey: String,
        @Header("x-ibm-client-id") clientId: String? = null,
        @Header("x-ibm-client-secret") clientSecret: String? = null
    ): Response<MetOfficeHourlyResponse>

    @GET("sitespecific/v0/point/daily")
    suspend fun getPointDaily(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("includeLocationName") includeLocationName: Boolean = true,
        @Query("excludeParameterMetadata") excludeParameterMetadata: Boolean = true,
        @Header("apikey") apiKey: String,
        @Header("x-ibm-client-id") clientId: String? = null,
        @Header("x-ibm-client-secret") clientSecret: String? = null
    ): Response<MetOfficeDailyResponse>

    companion object {
        const val BASE_URL = "https://data.hub.api.metoffice.gov.uk/"
    }
}
