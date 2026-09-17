package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.ChatbotRequest
import com.quistock.quistock.domain.model.ChatbotResponse

interface ChatbotRepository {
    suspend fun sendMessage(request: ChatbotRequest): ChatbotResponse
}