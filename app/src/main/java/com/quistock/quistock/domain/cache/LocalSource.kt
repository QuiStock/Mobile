package com.quistock.quistock.domain.cache

import kotlin.time.Instant

interface LocalSource<T> {
    suspend fun read(): CachedValue<T>?
    suspend fun save(value: T, savedAt: Instant, expiresAt: Instant)
}
