package com.quistock.quistock.domain.port

interface RefreshableRepository {
    suspend fun refresh()
}
