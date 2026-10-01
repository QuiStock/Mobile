package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.ChatbotRequest
import com.quistock.quistock.domain.model.ChatbotResponse

@Deprecated("Legacy chatbot requires Firebase identity; API redesign pending", level = DeprecationLevel.WARNING)
interface ChatbotRepository {
    suspend fun sendMessage(request: ChatbotRequest): ChatbotResponse
}
