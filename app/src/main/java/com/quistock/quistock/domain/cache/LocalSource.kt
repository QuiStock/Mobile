package com.quistock.quistock.domain.cache

interface LocalSource<T> {
    suspend fun read(): CachedValue<T>?
    suspend fun save(value: CachedValue<T>)
}
