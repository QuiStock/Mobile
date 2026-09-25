package com.quistock.quistock.presentation.login

import com.quistock.quistock.domain.model.LegacyLoginError

sealed interface LoginUiState {
    data object Idle : LoginUiState

    data object Loading : LoginUiState

    data object Authenticated : LoginUiState

    data class Error(val reason: LegacyLoginError) : LoginUiState
}
