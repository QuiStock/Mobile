package com.quistock.quistock.presentation.home

import com.quistock.quistock.domain.model.SessionFailure

data class HomeNumbers(
    val nearExpirationProductCount: Int,
    val criticalAnalyzedFlowCount: Int,
    val activeActionCount: Int,
)

enum class HomeWarning { STALE, FUTURE_DATE }

data class HomeUiState(
    val numbers: HomeNumbers? = null,
    val loading: Boolean = false,
    val warning: HomeWarning? = null,
    val dataDate: String? = null,
    val error: Boolean = false,
    val sessionFailure: SessionFailure? = null,
) {
    val retryVisible: Boolean get() = error || warning != null || sessionFailure != null
    val retryEnabled: Boolean get() = retryVisible && !loading
}
