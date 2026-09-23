package com.quistock.quistock.domain.cache

sealed interface CacheLoadState<out T> {
    data object Loading : CacheLoadState<Nothing>

    data class Data<T>(val value: T, val refreshing: Boolean = false, val staleWarning: Boolean = false) :
        CacheLoadState<T>

    data object Unavailable : CacheLoadState<Nothing>
}
