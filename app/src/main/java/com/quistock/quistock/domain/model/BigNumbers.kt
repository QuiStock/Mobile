package com.quistock.quistock.domain.model

import kotlin.time.Instant

data class BigNumbers(
    val nearExpirationProductCount: Int,
    val criticalAnalyzedFlowCount: Int,
    val activeActionCount: Int,
    val createdAt: Instant,
)
