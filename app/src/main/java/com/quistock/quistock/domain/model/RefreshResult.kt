package com.quistock.quistock.domain.model

sealed interface RefreshResult<out T> {
    object Failed : RefreshResult<Nothing>
    data class Stale<T>(val data: T) : RefreshResult<T>
    data class UpToDate<T>(val data: T) : RefreshResult<T>
}
