package com.quistock.quistock.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.GeoPoint
import com.quistock.quistock.domain.port.DeviceLocationProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UserLocationState {
    /** Location access was not granted; the user can grant it from the card. */
    data object PermissionNeeded : UserLocationState
    data object Locating : UserLocationState
    class Located(val point: GeoPoint) : UserLocationState

    /** Access granted, but no position (location off, no fix, or timeout). */
    data object Unavailable : UserLocationState
}

class UserLocationViewModel(private val provider: DeviceLocationProvider) : ViewModel() {
    private val mutableState = MutableStateFlow<UserLocationState>(UserLocationState.Locating)
    val state: StateFlow<UserLocationState> = mutableState.asStateFlow()

    private var job: Job? = null

    /** Reads the current position; a known position stays on screen while a new one is requested. */
    fun refresh() {
        if (!provider.hasPermission()) {
            job?.cancel()
            mutableState.value = UserLocationState.PermissionNeeded
            return
        }
        if (job?.isActive == true) return
        if (mutableState.value !is UserLocationState.Located) mutableState.value = UserLocationState.Locating
        job = viewModelScope.launch {
            val point = provider.current()
            mutableState.value = when {
                point != null -> UserLocationState.Located(point)
                mutableState.value is UserLocationState.Located -> mutableState.value
                else -> UserLocationState.Unavailable
            }
        }
    }
}
