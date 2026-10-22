package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.ProfileResult

interface ProfileRepository {
    suspend fun read(): ProfileResult
}
