package com.quistock.quistock.domain.model

data class ChatbotResponse(
    val answer: String,
    val responsibleAgent: String,
    val referencedData: List<ReferencedData>,
) {
    data class ReferencedData(
        val productId: String,
        val name: String,
        val flowType: FlowType,
        val suggestedAction: SuggestedAction,
    )
}
