package com.quistock.quistock.data.remote.internal.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatbotResponseDTO(
    @SerialName("answer") val answer: String,
    @SerialName("responsible_agent") val responsibleAgent: String,
    @SerialName("referenced_data") val referencedData: List<ReferencedDataDTO>,
) {
    @Serializable
    data class ReferencedDataDTO(
        @SerialName("product_id") val productId: String,
        @SerialName("name") val name: String,
        @SerialName("flow_type") val flowType: FlowTypeDTO,
        @SerialName("suggested_action") val suggestedAction: SuggestedActionDTO,
    )
}
