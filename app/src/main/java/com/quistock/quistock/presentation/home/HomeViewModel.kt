package com.quistock.quistock.presentation.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.BigNumbers
import com.quistock.quistock.domain.model.RefreshResult
import com.quistock.quistock.domain.port.Clock
import com.quistock.quistock.domain.usecase.RefreshBigNumbersUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

class HomeViewModel(private val refreshBigNumbers: RefreshBigNumbersUseCase, private val clock: Clock) : ViewModel() {
    private val _uiState = MutableLiveData(HomeUiState())
    val uiState: LiveData<HomeUiState> = _uiState
    private var refreshJob: Job? = null

    fun load() {
        refreshJob?.cancel()
        refresh(HomeUiState(loading = true))
    }

    fun retry() {
        val current = _uiState.value ?: return
        if (!current.retryEnabled) return
        refresh(current.copy(loading = true))
    }

    private fun refresh(loadingState: HomeUiState) {
        _uiState.value = loadingState
        refreshJob = viewModelScope.launch {
            _uiState.value = when (val result = refreshBigNumbers()) {
                is RefreshResult.UpToDate -> HomeUiState(numbers = result.data.toUiNumbers())
                is RefreshResult.Stale -> staleState(result.data)
                RefreshResult.Failed -> HomeUiState(error = true)
            }
        }
    }

    private fun staleState(data: BigNumbers): HomeUiState {
        val date = data.createdAt.toLocalDateTime(clock.timeZone).date
        val warning = if (clock.midnightOfDay(data.createdAt) > clock.midnightOfDay(clock.now())) {
            HomeWarning.FUTURE_DATE
        } else {
            HomeWarning.STALE
        }
        return HomeUiState(
            numbers = data.toUiNumbers(),
            warning = warning,
            dataDate = buildString {
                append(date.day.toString().padStart(2, '0'))
                append('/')
                append(date.month.toString().padStart(2, '0'))
                append('/')
                append(date.year)
            },
        )
    }

    private fun BigNumbers.toUiNumbers() = HomeNumbers(
        nearExpirationProductCount = nearExpirationProductCount,
        criticalAnalyzedFlowCount = criticalAnalyzedFlowCount,
        activeActionCount = activeActionCount,
    )
}
