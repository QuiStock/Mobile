package com.quistock.quistock.presentation.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.LoginError
import com.quistock.quistock.domain.model.LoginResult
import com.quistock.quistock.domain.port.UserPreferences
import com.quistock.quistock.domain.usecase.LoginUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LoginViewModel(val loginUseCase: LoginUseCase, val userPreferences: UserPreferences) : ViewModel() {
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
                    is LoginResult.Success -> {
                        val user = result.user
                        _userEmail.value = user.email
                        _uiState.value = LoginUiState.Authenticated
                        userPreferences.saveUserId(user.id)
                    }

                    is LoginError -> _uiState.value = LoginUiState.Error(result)
                }
            } catch (exception: CancellationException) {
                throw exception
            }
        }
    }
}
