package io.github.tychomagnetic.metterweather.widget

import io.github.tychomagnetic.metterweather.data.repository.WidgetForecastException
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal fun classifyWidgetFailure(error: Throwable): WidgetRefreshOutcome = when {
    error is WidgetForecastException -> when (error.statusCode) {
        null, 401, 403 -> WidgetRefreshOutcome.CREDENTIALS_REQUIRED
        429 -> WidgetRefreshOutcome.QUOTA_EXCEEDED
        408, in 500..599 -> WidgetRefreshOutcome.RETRYABLE_FAILURE
        else -> WidgetRefreshOutcome.FAILED
    }
    error is IOException -> WidgetRefreshOutcome.RETRYABLE_FAILURE
    else -> WidgetRefreshOutcome.FAILED
}

internal fun quotaResumeTime(retryAfter: String?, now: Long): Long {
    val seconds = retryAfter?.trim()?.toLongOrNull()
    val date = runCatching {
        ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }.getOrNull()
    // Never make rapid quota retries, even if the server omits or misstates its delay.
    return if (seconds != null && seconds in 1..31_536_000) {
        now + maxOf(seconds * 1000L, 60_000L)
    } else if (date != null && date > now) {
        maxOf(date, now + 60_000L)
    } else now + 60L * 60L * 1000L
}
