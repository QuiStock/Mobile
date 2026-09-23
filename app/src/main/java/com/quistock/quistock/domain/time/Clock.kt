package com.quistock.quistock.domain.time

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

abstract class Clock(val clock: kotlin.time.Clock, val timeZone: TimeZone) {
    fun now(): Instant = clock.now()

    fun nextMidnight(): Instant = clock.now()
        .toLocalDateTime(timeZone)
        .date
        .plus(value = 1, unit = DateTimeUnit.DAY)
        .atStartOfDayIn(timeZone)
}
