package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.TeamResult

interface TeamRepository {
    suspend fun list(expected: SessionSnapshot): TeamResult
    suspend fun create(name: String, email: String, storeId: Long, expected: SessionSnapshot): TeamResult
}
