package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.AlertRisk
import com.quistock.quistock.domain.model.CreationRisk
import com.quistock.quistock.domain.model.PriorityAlert
import com.quistock.quistock.domain.model.PriorityAlertsResult
import com.quistock.quistock.domain.port.PriorityAlertsRepository
import java.util.Locale

class LoadPriorityAlertsUseCase(private val repository: PriorityAlertsRepository) {
    suspend operator fun invoke(storeId: Long, storeCode: String?): PriorityAlertsResult =
        when (val result = repository.read(storeId, storeCode)) {
            AlertProductsResult.Failure -> PriorityAlertsResult.Failure

            is AlertProductsResult.Success -> PriorityAlertsResult.Success(
                result.products.mapNotNull { product ->
                    val risk = when {
                        CreationRisk.NEAR_EXPIRY in product.risks &&
                            CreationRisk.EXPIRED !in product.risks -> AlertRisk.EXPIRY

                        CreationRisk.STOCKOUT in product.risks -> AlertRisk.STOCKOUT

                        CreationRisk.EXPIRATION in product.risks &&
                            CreationRisk.EXPIRED !in product.risks -> AlertRisk.OVERSTOCK

                        else -> null
                    }
                    risk?.let { PriorityAlert(product, it) }
                }.sortedWith(
                    compareBy<PriorityAlert> { it.risk.ordinal }
                        .thenBy { if (it.risk == AlertRisk.EXPIRY) it.product.expirationDays else null }
                        .thenBy { it.product.name.lowercase(Locale.ROOT) }
                        .thenBy { it.product.id },
                ),
                partial = result.invalidCount > 0,
            )
        }
}
