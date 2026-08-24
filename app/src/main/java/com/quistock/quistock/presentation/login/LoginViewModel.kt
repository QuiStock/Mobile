package com.quistock.quistock.presentation.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.quistock.quistock.domain.usecase.LoginUseCase

class LoginViewModel(
    val loginUseCase: LoginUseCase,
) : ViewModel() {
    private val _isAuthenticated = MutableLiveData(false)
    val isAuthenticated: LiveData<Boolean> = _isAuthenticated

    fun authenticate(
        email: String,
        password: String,
    ) {
        _isAuthenticated.value = loginUseCase(email = email, password = password)
    }
}
