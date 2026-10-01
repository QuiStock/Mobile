package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.ChatbotRequest
import com.quistock.quistock.domain.model.ChatbotResponse
import com.quistock.quistock.domain.port.ChatbotRepository

@Deprecated("Legacy chatbot requires Firebase identity; API redesign pending", level = DeprecationLevel.WARNING)
class SendMessageToChatbotUseCase(private val chatbotRepository: ChatbotRepository) {
    suspend operator fun invoke(request: ChatbotRequest): ChatbotResponse = chatbotRepository.sendMessage(request)
}
