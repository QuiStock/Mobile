package com.quistock.quistock.presentation.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.usecase.LoginUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LoginViewModel(val loginUseCase: LoginUseCase) : ViewModel() {
    private val _uiState = MutableLiveData<LoginUiState>(LoginUiState.Idle)
    val uiState: LiveData<LoginUiState> = _uiState

    fun authenticate(email: String, password: String) {
        if (_uiState.value == LoginUiState.Loading) return

        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                if (loginUseCase(email, password)) {
                    _uiState.value = LoginUiState.Authenticated
                } else {
                    _uiState.value = LoginUiState.Error(reason = LoginError.InvalidCredentials)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = LoginUiState.Error(reason = LoginError.Unexpected)
            }
        }
    }
}
