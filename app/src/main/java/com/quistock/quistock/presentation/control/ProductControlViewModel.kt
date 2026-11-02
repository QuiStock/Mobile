package com.quistock.quistock.presentation.control

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionException
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.StockFlow
import com.quistock.quistock.domain.port.PriorityAlertsRepository
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface ProductControlState {
    data object Loading : ProductControlState
    data object Error : ProductControlState
    data class Ready(val products: List<AlertProduct>, val partial: Boolean) : ProductControlState
}

enum class ProductFlowFilter { ALL, HIGH, MEDIUM, LOW, UNAVAILABLE }

class ProductControlViewModel(
    private val repository: PriorityAlertsRepository,
    private val profile: ProfileUseCase,
    private val session: SessionUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow<ProductControlState>(ProductControlState.Loading)
    val state = mutableState.asStateFlow()
    private val mutableSearch = MutableStateFlow("")
    val search = mutableSearch.asStateFlow()
    private val mutableFilter = MutableStateFlow(ProductFlowFilter.ALL)
    val filter = mutableFilter.asStateFlow()
    private var context: ProductStore? = null
    private var request: Job? = null
    private var needsReload = true

    init {
        viewModelScope.launch {
            combine(profile.state, session.state) { currentProfile, currentSession ->
                (currentProfile as? ProfileState.Ready)?.takeIf {
                    currentSession == SessionState.Active && it.generation == session.snapshot().generation
                }?.let { ready ->
                    ready.profile.storeId?.let { ProductStore(ready.generation, it, ready.profile.storeCode) }
                }
            }.collect { current ->
                if (context != current) {
                    request?.cancel()
                    request = null
                    context = current
                    needsReload = true
                    mutableState.value = ProductControlState.Loading
                }
                if (needsReload && current != null) load()
            }
        }
    }

    fun enter() {
        if (needsReload) load()
    }

    fun retry() {
        if (mutableState.value == ProductControlState.Error) load()
    }

    fun setSearch(value: String) {
        mutableSearch.value = value
    }

    fun setFilter(value: ProductFlowFilter) {
        mutableFilter.value = value
    }

    fun visibleProducts(): List<AlertProduct> = filterProducts(
        (mutableState.value as? ProductControlState.Ready)?.products.orEmpty(),
        mutableSearch.value,
        mutableFilter.value,
    )

    fun canUse(product: AlertProduct): Boolean {
        val products = (mutableState.value as? ProductControlState.Ready)?.products
        return canNavigate() && products?.any { it == product } == true
    }

    private fun canNavigate(): Boolean = context?.let {
        it.generation == session.snapshot().generation && session.state.value == SessionState.Active &&
            (profile.state.value as? ProfileState.Ready)?.profile?.storeId == it.storeId
    } == true

    private fun load() {
        val expectedStore = context ?: return
        if (!canNavigate() || request?.isActive == true) return
        val snapshot = session.snapshot()
        needsReload = false
        mutableState.value = ProductControlState.Loading
        request = viewModelScope.launch {
            try {
                val result = repository.read(expectedStore.storeId, expectedStore.storeCode)
                session.withCurrentSession(snapshot) {
                    if (context != expectedStore || !canNavigate()) return@withCurrentSession
                    mutableState.value = when (result) {
                        AlertProductsResult.Failure -> ProductControlState.Error

                        is AlertProductsResult.Success -> ProductControlState.Ready(
                            result.products,
                            result.invalidCount > 0,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SessionException) {
                if (context == expectedStore) mutableState.value = ProductControlState.Loading
            } catch (_: Exception) {
                if (context == expectedStore && canNavigate()) mutableState.value = ProductControlState.Error
            }
        }
    }

    private data class ProductStore(val generation: Long, val storeId: Long, val storeCode: String?)
}

fun filterProducts(products: List<AlertProduct>, query: String, filter: ProductFlowFilter): List<AlertProduct> =
    products.filter { it.name.contains(query.trim(), ignoreCase = true) }.filter { product ->
        when (filter) {
            ProductFlowFilter.ALL -> true
            ProductFlowFilter.HIGH -> product.flow == StockFlow.HIGH
            ProductFlowFilter.MEDIUM -> product.flow == StockFlow.MEDIUM
            ProductFlowFilter.LOW -> product.flow == StockFlow.LOW
            ProductFlowFilter.UNAVAILABLE -> product.flow == null
        }
    }
