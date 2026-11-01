package com.quistock.quistock.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.StoreLocation
import com.quistock.quistock.domain.model.StoreLocationResult
import com.quistock.quistock.domain.usecase.LoadStoreLocationUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StoreLocationState {
    data object Loading : StoreLocationState
    class Ready(val location: StoreLocation) : StoreLocationState
    data object NoAddress : StoreLocationState
    data object Failure : StoreLocationState
}

class StoreLocationViewModel(private val loadLocation: LoadStoreLocationUseCase) : ViewModel() {
    private val mutableState = MutableStateFlow<StoreLocationState>(StoreLocationState.Loading)
    val state: StateFlow<StoreLocationState> = mutableState.asStateFlow()

    private var storeId: Long? = null
    private var job: Job? = null

    /** Loads once per store; a failed load for the same store can be retried with [retry]. */
    fun load(storeId: Long) {
        if (this.storeId == storeId) return
        this.storeId = storeId
        start(storeId)
    }

    fun retry() {
        val id = storeId ?: return
        if (mutableState.value is StoreLocationState.Failure) start(id)
    }

    private fun start(id: Long) {
        job?.cancel()
        mutableState.value = StoreLocationState.Loading
        job = viewModelScope.launch {
            mutableState.value = when (val result = loadLocation(id)) {
                is StoreLocationResult.Success -> StoreLocationState.Ready(result.location)
                StoreLocationResult.NoAddress -> StoreLocationState.NoAddress
                StoreLocationResult.Failure -> StoreLocationState.Failure
            }
        }
    }
}
