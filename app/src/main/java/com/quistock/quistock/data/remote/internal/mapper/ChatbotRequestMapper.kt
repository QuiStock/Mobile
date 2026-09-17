package com.quistock.quistock.data.remote.internal.mapper

import com.quistock.quistock.data.remote.internal.dto.ChatbotRequestDTO
import com.quistock.quistock.domain.model.ChatbotRequest

fun ChatbotRequest.toDto(): ChatbotRequestDTO = ChatbotRequestDTO(
    userId = userId,
    message = message,
)
