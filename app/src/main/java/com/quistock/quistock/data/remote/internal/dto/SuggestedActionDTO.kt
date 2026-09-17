package com.quistock.quistock.data.remote.internal.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SuggestedActionDTO {
    @SerialName("PROMOTION")
    PROMOTION,

    @SerialName("STOCK_ORDER")
    STOCK_ORDER,

    @SerialName("MONITOR")
    MONITOR,
}
