package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.AlertProductsResult

interface PriorityAlertsRepository {
    suspend fun read(storeId: Long, storeCode: String?): AlertProductsResult
}
