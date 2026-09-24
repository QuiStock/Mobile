package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.BigNumbers

interface CachedBigNumbersRepository {
    suspend fun read(): BigNumbers?
    suspend fun save(value: BigNumbers)
}

interface RemoteBigNumbersRepository {
    suspend fun fetch(): BigNumbers
}
