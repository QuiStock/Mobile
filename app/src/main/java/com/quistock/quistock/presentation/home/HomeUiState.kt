package com.quistock.quistock.presentation.home

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
) {
    val retryVisible: Boolean get() = error || warning != null
    val retryEnabled: Boolean get() = retryVisible && !loading
}
