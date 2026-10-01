package com.quistock.quistock.data.local.mapper

import com.quistock.quistock.data.local.entity.BigNumbersEntity
import com.quistock.quistock.domain.model.BigNumbers

fun BigNumbersEntity.toDomain(): BigNumbers = BigNumbers(
    nearExpirationProductCount = nearExpirationProductCount,
    criticalAnalyzedFlowCount = criticalAnalyzedFlowCount,
    activeActionCount = activeActionCount,
    createdAt = createdAt,
)

fun BigNumbers.toEntity(): BigNumbersEntity = BigNumbersEntity(
    nearExpirationProductCount = nearExpirationProductCount,
    criticalAnalyzedFlowCount = criticalAnalyzedFlowCount,
    activeActionCount = activeActionCount,
    createdAt = createdAt,
)
