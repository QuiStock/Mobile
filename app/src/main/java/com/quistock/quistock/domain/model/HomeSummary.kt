package com.quistock.quistock.domain.model

import java.time.Instant
import java.time.ZoneId

// Opaque local session identity; it never contains a token or a decoded JWT claim.
data class SessionBinding(
    val sessionId: String,
    val userId: String,
    val storeId: Long,
    val expiresAt: Long,
    val persisted: Boolean = true,
) {
    fun validAt(now: Long): Boolean = sessionId.isNotBlank() && userId.isNotBlank() && storeId > 0 && expiresAt > now
    fun sameScope(other: SessionBinding): Boolean =
        sessionId == other.sessionId && userId == other.userId && storeId == other.storeId
}

data class HomeSummary(val nearExpiry: Long, val stockout: Long, val overstock: Long, val activeActions: Long) {
    init {
        require(listOf(nearExpiry, stockout, overstock, activeActions).all { it >= 0 })
        Math.addExact(stockout, overstock)
    }
    val stockoutAndOverstock: Long get() = Math.addExact(stockout, overstock)
}

data class CachedHomeSummary(val summary: HomeSummary, val receivedAt: Long) {
    fun staleAt(now: Long): Boolean = date(receivedAt) != date(now)
    private fun date(time: Long) = Instant.ofEpochMilli(time).atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate()
}

enum class SummaryFailure { NETWORK, TIMEOUT, SERVER, FORBIDDEN, NO_STORE, RATE_LIMIT, CONTRACT }

sealed interface HomeSummaryResult {
    data class Success(val summary: HomeSummary, val receivedAt: Long = 0, val persisted: Boolean = false) :
        HomeSummaryResult
    data class Failure(val reason: SummaryFailure, val retryAt: Long = 0) : HomeSummaryResult
}
