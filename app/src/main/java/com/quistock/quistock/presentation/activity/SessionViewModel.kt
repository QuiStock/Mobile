package com.quistock.quistock.presentation.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SessionViewModel(val session: SessionUseCase) : ViewModel() {
    private var lastNavigationState: SessionState? = null
    private val mutableRecovery = MutableStateFlow(SessionRecoveryUiState())
    val recovery = mutableRecovery.asStateFlow()
    private var retryTimer: Job? = null

    fun retrySession() {
        if (session.state.value !is SessionState.Failure || mutableRecovery.value.retrying ||
            session.retryDelayMillis > 0
        ) {
            return
        }
        mutableRecovery.value = mutableRecovery.value.copy(retrying = true)
        viewModelScope.launch {
            try {
                session.refresh(session.snapshot())
            } finally {
                mutableRecovery.value = mutableRecovery.value.copy(retrying = false)
                updateRetryBlock()
            }
        }
    }

    private fun updateRetryBlock() {
        retryTimer?.cancel()
        val remaining = if (session.state.value is SessionState.Failure) session.retryDelayMillis else 0L
        mutableRecovery.value = mutableRecovery.value.copy(retryBlocked = remaining > 0)
        if (remaining > 0) {
            retryTimer = viewModelScope.launch {
                delay(remaining)
                mutableRecovery.value = mutableRecovery.value.copy(retryBlocked = false)
            }
        }
    }

    fun consumeNavigation(state: SessionState): Boolean {
        if (lastNavigationState == state) return false
        lastNavigationState = state
        return true
    }

    init {
        viewModelScope.launch { session.restore() }
        viewModelScope.launch {
            session.state.collectLatest { updateRetryBlock() }
        }
    }
}

data class SessionRecoveryUiState(val retrying: Boolean = false, val retryBlocked: Boolean = false)
