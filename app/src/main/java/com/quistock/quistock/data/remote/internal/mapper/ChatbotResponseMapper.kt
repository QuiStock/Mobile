package com.quistock.quistock.data.remote.internal.mapper

import com.quistock.quistock.data.remote.internal.dto.ChatbotResponseDTO
import com.quistock.quistock.domain.model.ChatbotResponse

fun ChatbotResponseDTO.ReferencedDataDTO.toDomain(): ChatbotResponse.ReferencedData = ChatbotResponse.ReferencedData(
    productId = productId,
    name = name,
    flowType = flowType.toDomain(),
    suggestedAction = suggestedAction.toDomain(),
)

fun ChatbotResponseDTO.toDomain(): ChatbotResponse = ChatbotResponse(
    answer = answer,
    responsibleAgent = responsibleAgent,
    referencedData = referencedData.map { it.toDomain() },
)
