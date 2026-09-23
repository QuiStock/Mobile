package com.quistock.quistock.data.local.mapper

import com.quistock.quistock.data.local.entity.BigNumbersEntity
import com.quistock.quistock.domain.cache.CachedValue
import com.quistock.quistock.domain.model.BigNumbers

fun BigNumbersEntity.toDomain(): CachedValue<BigNumbers> {
    val domain = BigNumbers(
        nearExpirationProductCount = nearExpirationProductCount,
        criticalAnalyzedFlowCount = criticalAnalyzedFlowCount,
        activeActionCount = activeActionCount,
    )

    return CachedValue(value = domain, savedAt = savedAt, expiresAt = expiresAt)
}

fun CachedValue<BigNumbers>.toEntity(): BigNumbersEntity = BigNumbersEntity(
    savedAt = savedAt,
    expiresAt = expiresAt,
    nearExpirationProductCount = value.nearExpirationProductCount,
    criticalAnalyzedFlowCount = value.criticalAnalyzedFlowCount,
    activeActionCount = value.activeActionCount,
)
