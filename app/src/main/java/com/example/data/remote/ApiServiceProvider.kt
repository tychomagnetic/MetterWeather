package io.github.tychomagnetic.metterweather.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Process-wide Retrofit services. Keeping these instances shared lets OkHttp
 * reuse its connection pools when the foreground app and widget refreshes use
 * the same provider.
 */
object ApiServiceProvider {
    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val standardClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val bpfClient: OkHttpClient = standardClient.newBuilder()
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mapImagesClient: OkHttpClient = standardClient.newBuilder()
        .followRedirects(true)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val metOfficeApi: MetOfficeApiService = retrofit(
        baseUrl = MetOfficeApiService.BASE_URL,
        client = standardClient,
        withMoshiConverter = true
    ).create(MetOfficeApiService::class.java)

    val metOfficeBpfApi: MetOfficeBpfApiService = retrofit(
        baseUrl = MetOfficeBpfApiService.BASE_URL,
        client = bpfClient,
        withMoshiConverter = false
    ).create(MetOfficeBpfApiService::class.java)

    val openMeteoApi: OpenMeteoApiService = retrofit(
        baseUrl = OpenMeteoApiService.BASE_URL,
        client = standardClient,
        withMoshiConverter = true
    ).create(OpenMeteoApiService::class.java)

    val geocodingApi: GeocodingApiService = retrofit(
        baseUrl = GeocodingApiService.BASE_URL,
        client = standardClient,
        withMoshiConverter = true
    ).create(GeocodingApiService::class.java)

    val mapImagesApi: MetOfficeMapImagesApiService = retrofit(
        baseUrl = MetOfficeMapImagesApiService.BASE_URL,
        client = mapImagesClient,
        withMoshiConverter = true
    ).create(MetOfficeMapImagesApiService::class.java)

    private fun retrofit(
        baseUrl: String,
        client: OkHttpClient,
        withMoshiConverter: Boolean
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .apply {
                if (withMoshiConverter) {
                    addConverterFactory(MoshiConverterFactory.create(moshi))
                }
            }
            .build()
    }
}
