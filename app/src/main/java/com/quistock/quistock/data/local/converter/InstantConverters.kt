package com.quistock.quistock.data.local.converter

import androidx.room.TypeConverter
import kotlin.time.Instant

class InstantConverters {
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilliseconds()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant.Companion::fromEpochMilliseconds)
}
