package com.quistock.quistock.data.local.repository

import com.quistock.quistock.data.local.dao.BigNumbersDao
import com.quistock.quistock.data.local.mapper.toDomain
import com.quistock.quistock.data.local.mapper.toEntity
import com.quistock.quistock.domain.cache.CachedValue
import com.quistock.quistock.domain.model.BigNumbers
import com.quistock.quistock.domain.port.CachedBigNumbersRepository

class RoomBigNumbersRepository(private val dao: BigNumbersDao) : CachedBigNumbersRepository {
    override suspend fun read(): CachedValue<BigNumbers>? = dao.getLatest()?.toDomain()

    override suspend fun save(value: CachedValue<BigNumbers>) {
        dao.getLatest()?.let { dao.delete(it) }
        dao.insert(value.toEntity())
    }
}
