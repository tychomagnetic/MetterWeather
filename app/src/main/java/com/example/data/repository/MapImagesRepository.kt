package io.github.tychomagnetic.metterweather.data.repository

import android.content.Context
import io.github.tychomagnetic.metterweather.data.local.PreferencesManager
import io.github.tychomagnetic.metterweather.data.model.MapCatalogResult
import io.github.tychomagnetic.metterweather.data.model.MapManifestCache
import io.github.tychomagnetic.metterweather.data.model.MapOrder
import io.github.tychomagnetic.metterweather.data.remote.MetOfficeMapImagesApiService
import io.github.tychomagnetic.metterweather.data.remote.ApiServiceProvider
import io.github.tychomagnetic.metterweather.data.util.MapImagesUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class MapImagesRepository(
    context: Context,
    private val preferences: PreferencesManager,
    private val api: MetOfficeMapImagesApiService = ApiServiceProvider.mapImagesApi,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val imageCacheDirectory = File(context.cacheDir, "map-images").apply { mkdirs() }

    suspend fun testApiKey(apiKey: String): ApiKeyTestResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext ApiKeyTestResult.Error("Map Images API key cannot be empty.")
        try {
            val response = api.getOrders(apiKey.trim())
            when {
                response.isSuccessful && response.body()?.orders?.any { it.isCompatiblePngOrder() } == true ->
                    ApiKeyTestResult.Success("Verified with Met Office Map Images")
                response.isSuccessful -> ApiKeyTestResult.Error("No compatible PNG map order was found for this key.")
                response.code() == 401 -> ApiKeyTestResult.Error("Invalid Map Images API key (HTTP 401).")
                response.code() == 403 -> ApiKeyTestResult.Error("Map Images subscription not enabled (HTTP 403).")
                else -> ApiKeyTestResult.Error("Map Images server responded with HTTP ${response.code()}.")
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            ApiKeyTestResult.Error("Map Images connection error: ${error.localizedMessage ?: "request failed"}")
        }
    }

    suspend fun loadCatalog(
        apiKey: String,
        forceRefresh: Boolean = false,
        requestedOrderId: String? = null
    ): MapCatalogResult = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Add a Met Office Map Images API key in Settings first." }
        val boundary = MapImagesUtils.latestRunBoundaryMillis(nowMillis())
        val cached = preferences.getMapManifestCache()
        val wantedOrder = requestedOrderId?.takeIf { it.isNotBlank() }
            ?: preferences.getSelectedMapOrderId().takeIf { it.isNotBlank() }
        if (!forceRefresh && cached != null && cached.checkedBoundaryMillis == boundary &&
            MapImagesUtils.runMeetsBoundary(cached.runDateTime, boundary) &&
            cached.frames.isNotEmpty() && (wantedOrder == null || cached.orderId == wantedOrder)
        ) {
            return@withContext MapCatalogResult(cached)
        }

        try {
            val ordersResponse = api.getOrders(apiKey.trim())
            if (!ordersResponse.isSuccessful) error("Map order request failed (HTTP ${ordersResponse.code()}).")
            val orders = ordersResponse.body()?.orders.orEmpty().filter { it.isCompatiblePngOrder() }
            if (orders.isEmpty()) error("No compatible PNG map order is available for this API key.")
            val order = orders.firstOrNull { it.orderId.equals(wantedOrder, ignoreCase = true) }
                ?: orders.singleOrNull()
                ?: orders.first()

            val latestResponse = api.getLatest(order.orderId, apiKey.trim())
            if (!latestResponse.isSuccessful) error("Latest map manifest failed (HTTP ${latestResponse.code()}).")
            val frames = MapImagesUtils.newestImmutableFrames(latestResponse.body()?.orderDetails?.files.orEmpty())
            if (frames.isEmpty()) error("The latest map run contains no usable PNG frames.")
            val catalog = MapManifestCache(
                checkedBoundaryMillis = boundary,
                orderId = order.orderId,
                orderName = order.name.ifBlank { order.orderId },
                availableOrders = orders,
                runDateTime = frames.first().runDateTime,
                frames = frames
            )
            preferences.setSelectedMapOrderId(order.orderId)
            preferences.setMapManifestCache(catalog)
            val latestRunPending = !MapImagesUtils.runMeetsBoundary(catalog.runDateTime, boundary)
            MapCatalogResult(
                catalog,
                warning = if (latestRunPending) {
                    "The latest 00:00/12:00 UTC map run is not available yet. It will be checked again next time you open Weather Maps."
                } else null
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            if (cached != null && cached.frames.isNotEmpty() && (wantedOrder == null || cached.orderId == wantedOrder)) {
                MapCatalogResult(cached, "Could not check the latest run. Showing the last cached map run.")
            } else {
                throw error
            }
        }
    }

    suspend fun loadImage(apiKey: String, orderId: String, fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val safeOrderId = sanitizeCacheComponent(orderId)
        val safeFileId = sanitizeCacheComponent(fileId)
        val cacheFile = File(imageCacheDirectory, "${safeOrderId}_${safeFileId}_land_legend.png")
        if (cacheFile.isFile && cacheFile.length() in 1..MAX_IMAGE_BYTES) {
            val cachedBytes = cacheFile.readBytes()
            if (cachedBytes.hasPrefix(PNG_SIGNATURE)) return@withContext cachedBytes
            cacheFile.delete()
        } else if (cacheFile.exists()) {
            cacheFile.delete()
        }

        var attempt = 0
        while (true) {
            val response = api.getImage(orderId, fileId, apiKey = apiKey.trim())
            if (response.isSuccessful) {
                val body = response.body() ?: error("Map image response was empty.")
                if (body.contentLength() > MAX_IMAGE_BYTES) {
                    error("Map image response was too large.")
                }
                val bytes = body.bytes()
                if (bytes.size > MAX_IMAGE_BYTES || !bytes.hasPrefix(PNG_SIGNATURE)) {
                    error("Map image response was not a PNG.")
                }
                cacheFile.writeBytes(bytes)
                trimImageCache()
                return@withContext bytes
            }

            response.errorBody()?.close()
            val retryAfterMillis = response.headers()["Retry-After"]
                ?.toLongOrNull()
                ?.coerceIn(1L, 60L)
                ?.times(1_000L)
            val retryableGatewayError = response.code() in setOf(502, 503, 504)
            val retryableRateLimit = response.code() == 429 && retryAfterMillis != null
            if (attempt >= MAX_IMAGE_RETRIES || (!retryableGatewayError && !retryableRateLimit)) {
                error("Map image request failed (HTTP ${response.code()}).")
            }

            val backoffMillis = retryAfterMillis ?: (1_000L shl attempt)
            attempt++
            delay(backoffMillis)
        }
        @Suppress("UNREACHABLE_CODE")
        error("Map image retry loop ended unexpectedly.")
    }

    private fun MapOrder.isCompatiblePngOrder(): Boolean =
        format.equals("PNG", ignoreCase = true) && orderId.isNotBlank()

    private fun sanitizeCacheComponent(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(MAX_CACHE_COMPONENT_LENGTH).ifBlank { "map" }

    private fun ByteArray.hasPrefix(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { index -> this[index] == prefix[index] }

    private fun trimImageCache() {
        val files = imageCacheDirectory.listFiles().orEmpty().filter { it.isFile }
        var totalBytes = files.sumOf { it.length() }
        if (totalBytes <= MAX_IMAGE_CACHE_BYTES) return

        files.sortedBy { it.lastModified() }.forEach { file ->
            if (totalBytes <= MAX_IMAGE_CACHE_BYTES) return@forEach
            val length = file.length()
            if (file.delete()) totalBytes -= length
        }
    }

    companion object {
        private const val MAX_IMAGE_RETRIES = 2
        private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024L
        private const val MAX_IMAGE_CACHE_BYTES = 100 * 1024 * 1024L
        private const val MAX_CACHE_COMPONENT_LENGTH = 160
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )

    }
}
