package com.quistock.quistock.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.RoleAlertType
import com.quistock.quistock.domain.usecase.RoleAlertsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Switch state per alert type applicable to the signed-in role; empty when no mobile profile is ready. */
class AlertPreferencesViewModel(private val alerts: RoleAlertsUseCase) : ViewModel() {
    private val mutableState = MutableStateFlow<Map<RoleAlertType, Boolean>>(emptyMap())
    val state = mutableState.asStateFlow()

    fun load() {
        viewModelScope.launch { mutableState.value = alerts.settings()?.enabled.orEmpty() }
    }

    fun set(type: RoleAlertType, enabled: Boolean) {
        if (state.value[type] == enabled || type !in state.value) return
        mutableState.value = state.value + (type to enabled)
        viewModelScope.launch {
            if (!alerts.setEnabled(type, enabled)) {
                load()
            } else if (enabled) {
                alerts.check()
            }
        }
    }
}
