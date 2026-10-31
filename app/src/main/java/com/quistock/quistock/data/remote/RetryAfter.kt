package com.quistock.quistock.data.remote

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal fun retryAfterAt(value: String?, now: Long): Long {
    val seconds = value?.toLongOrNull()?.takeIf { it >= 0 }
    return if (seconds != null) {
        now + seconds.coerceAtMost((Long.MAX_VALUE - now) / MILLIS_PER_SECOND) * MILLIS_PER_SECOND
    } else {
        runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .getOrDefault(now).coerceAtLeast(now)
    }
}

private const val MILLIS_PER_SECOND = 1000L
