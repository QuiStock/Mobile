package com.quistock.quistock.presentation.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.usecase.GetHomeSummaryUseCase
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
    private val summary: GetHomeSummaryUseCase,
) : ViewModel() {
    suspend fun photoBytes(): ByteArray? = loadPhoto()

    private var lastNavigationState: SessionState? = null
    private val mutableRecovery = MutableStateFlow(SessionRecoveryUiState())
    val recovery = mutableRecovery.asStateFlow()
    private var retryTimer: Job? = null
    private var logoutJob: Job? = null
    private var offlineExpiry: Job? = null

    fun retrySession() {
        if (session.state.value == SessionState.Active && profile.state.value is ProfileState.Failure) {
            retryProfile()
        } else if (session.state.value is SessionState.Failure || session.state.value is SessionState.OfflineReadOnly) {
            retryAuthentication()
        }
    }

    private fun retryProfile() {
        if (!mutableRecovery.value.retryBlocked && !mutableRecovery.value.retrying) {
            viewModelScope.launch { profile.load() }
        }
    }

    private fun retryAuthentication() {
        if (mutableRecovery.value.retrying || session.retryDelayMillis > 0) return
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
        val state = session.state.value
        val remaining = when {
            state is SessionState.Failure || state is SessionState.OfflineReadOnly -> session.retryDelayMillis
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

    private fun clearProfile(state: SessionState) {
        loadPhoto.clear()
        val preserveRejection = profile.state.value is ProfileState.Rejected &&
            (state == SessionState.Expired || state == SessionState.LocalFailure)
        if (!preserveRejection) profile.clear()
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
                offlineExpiry?.cancel()
                if (state is SessionState.Failure) summary.openOffline()
                if (state is SessionState.OfflineReadOnly) {
                    val expected = session.snapshot()
                    session.local.binding()?.let { scope ->
                        offlineExpiry = viewModelScope.launch {
                            delay((scope.expiresAt - System.currentTimeMillis()).coerceAtLeast(1))
                            session.local.expireOffline(expected)
                        }
                    }
                }
                if (state == SessionState.Active) {
                    profile.load()
                } else if (state !is SessionState.Failure) {
                    clearProfile(state)
                }
            }
        }
        viewModelScope.launch {
            profile.state.collectLatest {
                updateRetryBlock()
                if (it is ProfileState.Failure) summary.openOffline()
            }
        }
    }
}

data class SessionRecoveryUiState(val retrying: Boolean = false, val retryBlocked: Boolean = false)
