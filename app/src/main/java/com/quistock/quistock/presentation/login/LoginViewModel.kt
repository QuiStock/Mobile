package com.quistock.quistock.presentation.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.LoginError
import com.quistock.quistock.domain.usecase.LoginUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LoginViewModel(private val loginUseCase: LoginUseCase) : ViewModel() {
    private val mutableState = MutableLiveData<LoginUiState>(LoginUiState.Idle)
    val uiState: LiveData<LoginUiState> = mutableState

    fun showExpired() {
        mutableState.value = LoginUiState.Expired
    }
    fun showUnexpectedError() {
        mutableState.value = LoginUiState.Error(LoginError.UnexpectedError)
    }

    @Suppress("TooGenericExceptionCaught")
    fun authenticate(email: String, password: String) {
        if (mutableState.value == LoginUiState.Loading) return
        mutableState.value = LoginUiState.Loading
        viewModelScope.launch {
            try {
                mutableState.value = when (val result = loginUseCase(email, password)) {
                    is AuthResult.Success -> LoginUiState.Authenticated
                    is LoginError -> LoginUiState.Error(result)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.value = LoginUiState.Error(LoginError.UnexpectedError)
            }
        }
    }
}
