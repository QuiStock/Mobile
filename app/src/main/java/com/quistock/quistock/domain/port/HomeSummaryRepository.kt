package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.CachedHomeSummary
import com.quistock.quistock.domain.model.HomeSummaryResult
import com.quistock.quistock.domain.model.SessionBinding

interface HomeSummaryRepository {
    suspend fun read(storeId: Long): HomeSummaryResult
}

interface HomeSummaryCache {
    suspend fun read(scope: SessionBinding): CachedHomeSummary?
    suspend fun write(scope: SessionBinding, summary: CachedHomeSummary): Boolean
}
