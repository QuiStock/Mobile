package com.quistock.quistock.presentation.home

import com.quistock.quistock.domain.model.CachedHomeSummary
import com.quistock.quistock.domain.model.SummaryFailure

data class HomeSummaryUiState(
    val cached: CachedHomeSummary? = null,
    val loading: Boolean = false,
    val offline: Boolean = false,
    val failure: SummaryFailure? = null,
    val persistenceFailed: Boolean = false,
    val retryAt: Long = 0,
    val now: Long = System.currentTimeMillis(),
)
