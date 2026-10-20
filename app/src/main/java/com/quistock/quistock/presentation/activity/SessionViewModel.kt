package com.quistock.quistock.presentation.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.usecase.LoadProfilePhotoUseCase
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SessionViewModel(
    val session: SessionUseCase,
    val profile: ProfileUseCase,
    private val loadPhoto: LoadProfilePhotoUseCase,
) : ViewModel() {
    suspend fun photoBytes(): ByteArray? = loadPhoto()

    private var lastNavigationState: SessionState? = null
    private val mutableRecovery = MutableStateFlow(SessionRecoveryUiState())
    val recovery = mutableRecovery.asStateFlow()
    private var retryTimer: Job? = null
    private var logoutJob: Job? = null

    fun retrySession() {
        if (session.state.value == SessionState.Active && profile.state.value is ProfileState.Failure) {
            if (mutableRecovery.value.retryBlocked || mutableRecovery.value.retrying) return
            viewModelScope.launch { profile.load() }
            return
        }
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
        val remaining = when {
            session.state.value is SessionState.Failure -> session.retryDelayMillis
            else -> (profile.state.value as? ProfileState.Failure)?.retryAfterMillis ?: 0L
        }
        mutableRecovery.value = mutableRecovery.value.copy(retryBlocked = remaining > 0)
        if (remaining > 0) {
            retryTimer = viewModelScope.launch {
                delay(remaining)
                mutableRecovery.value = mutableRecovery.value.copy(retryBlocked = false)
            }
        }
    }

    fun logout() {
        if (logoutJob?.isActive == true) return
        logoutJob = viewModelScope.launch { session.logout() }
    }

    fun retryLogoutCleanup() {
        if (logoutJob?.isActive == true) return
        logoutJob = viewModelScope.launch { session.retryLogoutCleanup() }
    }

    fun consumeNavigation(state: SessionState): Boolean {
        if (lastNavigationState == state) return false
        lastNavigationState = state
        return true
    }

    init {
        viewModelScope.launch { session.restore() }
        viewModelScope.launch {
            session.state.collectLatest { state ->
                updateRetryBlock()
                if (state == SessionState.Active) {
                    profile.load()
                } else if (state !is SessionState.Failure) {
                    loadPhoto.clear()
                    if (profile.state.value !is ProfileState.Rejected) profile.clear()
                }
            }
        }
        viewModelScope.launch { profile.state.collectLatest { updateRetryBlock() } }
    }
}

data class SessionRecoveryUiState(val retrying: Boolean = false, val retryBlocked: Boolean = false)
