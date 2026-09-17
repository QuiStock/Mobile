package com.quistock.quistock.data.remote.internal.mapper

import com.quistock.quistock.data.remote.internal.dto.SuggestedActionDTO
import com.quistock.quistock.domain.model.SuggestedAction

fun SuggestedActionDTO.toDomain() = when (this) {
    SuggestedActionDTO.PROMOTION -> SuggestedAction.PROMOTION
    SuggestedActionDTO.MONITOR -> SuggestedAction.MONITOR
    SuggestedActionDTO.STOCK_ORDER -> SuggestedAction.STOCK_ORDER
}
