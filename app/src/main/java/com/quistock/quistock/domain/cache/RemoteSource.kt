package com.quistock.quistock.domain.cache

interface RemoteSource<T> {
    suspend fun fetch(): T
}
