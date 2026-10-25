package com.quistock.quistock.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.PriorityAlert
import com.quistock.quistock.domain.model.PriorityAlertsResult
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.usecase.LoadPriorityAlertsUseCase
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {
    private val mutableState = MutableStateFlow<HomeAlertsState>(HomeAlertsState.Loading)
    val state = mutableState.asStateFlow()
    private var context: AlertContext? = null
    private var request: Job? = null
    private var needsReload = true
    private var entered = false

    init {
        viewModelScope.launch {
            combine(profile.state, session.state) { currentProfile, currentSession ->
                (currentProfile as? ProfileState.Ready)?.takeIf {
                    currentSession == SessionState.Active && it.generation == session.snapshot().generation
                }?.let { ready ->
                    ready.profile.storeId?.let { AlertContext(ready.generation, it, ready.profile.storeCode) }
                }
            }.collect { current ->
                if (context != current) {
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
        entered = true
        if (needsReload) reload()
    }

    fun leave() {
        entered = false
        needsReload = true
        request?.cancel()
        request = null
        mutableState.value = HomeAlertsState.Loading
    }

    fun retry() {
        if (mutableState.value == HomeAlertsState.Error) reload()
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
