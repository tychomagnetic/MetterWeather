package io.github.tychomagnetic.metterweather.data.repository

class WidgetForecastException(
    val statusCode: Int? = null,
    val retryAfter: String? = null
) : Exception(
    if (statusCode == null) "A Met Office Spot API key is required for widget refreshes."
    else "Met Office Spot widget refresh failed (HTTP $statusCode)."
)
