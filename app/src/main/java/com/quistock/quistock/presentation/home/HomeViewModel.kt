package com.quistock.quistock.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.CachedHomeSummary
import com.quistock.quistock.domain.model.HomeSummaryResult
import com.quistock.quistock.domain.model.PriorityAlert
import com.quistock.quistock.domain.model.PriorityAlertsResult
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.SummaryFailure
import com.quistock.quistock.domain.usecase.GetHomeSummaryUseCase
import com.quistock.quistock.domain.usecase.LoadPriorityAlertsUseCase
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface HomeAlertsState {
    data object Loading : HomeAlertsState
    data object Error : HomeAlertsState
    data class Ready(val alerts: List<PriorityAlert>, val partial: Boolean) : HomeAlertsState
}

class HomeViewModel(
    private val loadAlerts: LoadPriorityAlertsUseCase,
    private val profile: ProfileUseCase,
    private val session: SessionUseCase,
    private val loadSummary: GetHomeSummaryUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow<HomeAlertsState>(HomeAlertsState.Loading)
    val state = mutableState.asStateFlow()
    private var context: AlertContext? = null
    private var request: Job? = null
    private var needsReload = true
    private var entered = false
    private val mutableSummary =
        MutableStateFlow(HomeSummaryUiState(offline = session.state.value is SessionState.OfflineReadOnly))
    val summary = mutableSummary.asStateFlow()
    private var summaryRequest: Job? = null

    init {
        viewModelScope.launch {
            session.state.collectLatest { state ->
                if (state is SessionState.OfflineReadOnly) {
                    summaryRequest?.cancel()
                    summaryRequest = null
                    if (entered) refreshSummary()
                } else if (state != SessionState.Active) {
                    summaryRequest?.cancel()
                    summaryRequest = null
                    mutableSummary.value = HomeSummaryUiState()
                } else if (entered) {
                    refreshSummary()
                }
            }
        }
        viewModelScope.launch {
            combine(profile.state, session.state) { currentProfile, currentSession ->
                (currentProfile as? ProfileState.Ready)?.takeIf {
                    currentSession == SessionState.Active && it.generation == session.snapshot().generation
                }?.let { ready ->
                    ready.profile.storeId?.let { AlertContext(ready.generation, it, ready.profile.storeCode) }
                }
            }.collect { current ->
                if (context != current) {
                    if (context != null && context?.storeId != current?.storeId) {
                        mutableSummary.value = HomeSummaryUiState()
                    }
                    request?.cancel()
                    request = null
                    context = current
                    needsReload = true
                    mutableState.value = HomeAlertsState.Loading
                }
                if (entered && needsReload && current != null) reload()
            }
        }
    }

    fun enter() {
        val newEntry = !entered
        entered = true
        if (newEntry) refreshSummary()
        if (needsReload) reload()
    }

    fun leave() {
        entered = false
        summaryRequest?.cancel()
        summaryRequest = null
        needsReload = true
        request?.cancel()
        request = null
        mutableState.value = HomeAlertsState.Loading
    }

    fun retry() {
        if (mutableState.value == HomeAlertsState.Error) reload()
    }

    fun retrySummary() {
        if (loadSummary.retryAt <= System.currentTimeMillis()) refreshSummary()
    }

    fun tickSummary() {
        val now = System.currentTimeMillis()
        val offline = session.state.value is SessionState.OfflineReadOnly
        mutableSummary.value = mutableSummary.value.copy(
            now = now,
            offline = offline,
            retryAt = maxOf(loadSummary.retryAt, now + if (offline) session.retryDelayMillis else 0L),
        )
        if (offline && session.binding() == null) viewModelScope.launch { session.expire(session.snapshot()) }
    }

    fun nextSummaryTickMillis(): Long {
        val now = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
        val midnight = now.atZone(java.time.ZoneId.of("America/Sao_Paulo")).toLocalDate().plusDays(1)
            .atStartOfDay(java.time.ZoneId.of("America/Sao_Paulo")).toInstant()
        val retry = (summary.value.retryAt - now.toEpochMilli()).takeIf { it > 0 } ?: Long.MAX_VALUE
        return minOf(60_000L, java.time.Duration.between(now, midnight).toMillis(), retry).coerceAtLeast(1)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun refreshSummary() {
        if (!entered || summaryRequest?.isActive == true) return
        val expected = session.snapshot()
        if (session.state.value != SessionState.Active && session.state.value !is SessionState.OfflineReadOnly) return
        mutableSummary.value = mutableSummary.value.copy(loading = true, failure = null)
        summaryRequest = viewModelScope.launch {
            try {
                val cached = loadSummary.cached()
                if (session.snapshot().generation != expected.generation) return@launch
                mutableSummary.value = mutableSummary.value.copy(cached = cached)
                if (session.state.value is SessionState.OfflineReadOnly) {
                    if (cached == null) {
                        session.closeOffline(expected)
                    } else {
                        mutableSummary.value = HomeSummaryUiState(cached = cached, offline = true)
                    }
                } else {
                    when (val result = loadSummary()) {
                        is HomeSummaryResult.Success -> session.withCurrentSession(expected) {
                            mutableSummary.value = HomeSummaryUiState(
                                cached = CachedHomeSummary(result.summary, result.receivedAt),
                                persistenceFailed = !result.persisted,
                            )
                        }

                        is HomeSummaryResult.Failure -> if (session.snapshot().generation == expected.generation) {
                            mutableSummary.value = mutableSummary.value.copy(
                                loading = false,
                                failure = result.reason,
                                retryAt = result.retryAt,
                            )
                        }
                    }
                }
                tickSummary()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (session.snapshot().generation == expected.generation) {
                    mutableSummary.value = mutableSummary.value.copy(loading = false, failure = SummaryFailure.CONTRACT)
                }
            }
        }
    }

    fun canNavigate(): Boolean = context?.let {
        it.generation == session.snapshot().generation && session.state.value == SessionState.Active &&
            (profile.state.value as? ProfileState.Ready)?.profile?.storeId == it.storeId
    } == true

    private fun reload() {
        val expected = context ?: return
        if (request?.isActive == true || !canNavigate()) return
        val snapshot = session.snapshot()
        needsReload = false
        mutableState.value = HomeAlertsState.Loading
        request = viewModelScope.launch {
            val result = loadAlerts(expected.storeId, expected.storeCode)
            try {
                session.withCurrentSession(snapshot) {
                    if (context != expected || !canNavigate()) return@withCurrentSession
                    mutableState.value = when (result) {
                        PriorityAlertsResult.Failure -> HomeAlertsState.Error
                        is PriorityAlertsResult.Success -> HomeAlertsState.Ready(result.alerts, result.partial)
                    }
                }
            } catch (_: SessionException) {
                if (context == expected) mutableState.value = HomeAlertsState.Loading
            }
        }
    }

    private data class AlertContext(val generation: Long, val storeId: Long, val storeCode: String?)
}
