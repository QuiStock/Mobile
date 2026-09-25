package com.quistock.quistock.presentation.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.LegacyLoginError
import com.quistock.quistock.domain.model.LegacyLoginResult
import com.quistock.quistock.domain.port.UserPreferences
import com.quistock.quistock.domain.usecase.LegacyLoginUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LoginViewModel(val loginUseCase: LegacyLoginUseCase, val userPreferences: UserPreferences) : ViewModel() {
    private val _uiState = MutableLiveData<LoginUiState>(LoginUiState.Idle)
    val uiState: LiveData<LoginUiState> = _uiState

    private val _userEmail = MutableLiveData<String?>(null)
    val userEmail: LiveData<String?> = _userEmail

    @Suppress("RethrowCaughtException")
    fun authenticate(email: String, password: String) {
        if (_uiState.value == LoginUiState.Loading) return

        _uiState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                when (val result = loginUseCase(email, password)) {
                    is LegacyLoginResult.Success -> {
                        val user = result.user
                        _userEmail.value = user.email
                        _uiState.value = LoginUiState.Authenticated
                        userPreferences.saveUserId(user.id)
                    }

                    is LegacyLoginError -> _uiState.value = LoginUiState.Error(result)
                }
            } catch (exception: CancellationException) {
                throw exception
            }
        }
    }
}
