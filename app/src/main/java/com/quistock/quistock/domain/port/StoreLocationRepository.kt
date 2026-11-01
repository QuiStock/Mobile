package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.StoreLocationResult

interface StoreLocationRepository {
    suspend fun read(storeId: Long): StoreLocationResult
}
