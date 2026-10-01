package com.quistock.quistock.presentation.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.coroutines.launch

class SessionViewModel(val session: SessionUseCase) : ViewModel() {
    private var lastNavigationState: SessionState? = null

    fun consumeNavigation(state: SessionState): Boolean {
        if (lastNavigationState == state) return false
        lastNavigationState = state
        return true
    }

    init {
        viewModelScope.launch { session.restore() }
    }
}
