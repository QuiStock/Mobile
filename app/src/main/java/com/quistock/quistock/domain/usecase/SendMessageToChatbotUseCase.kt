package com.quistock.quistock.domain.usecase

import com.quistock.quistock.domain.model.ChatbotConversation
import com.quistock.quistock.domain.model.ChatbotConversationHistory
import com.quistock.quistock.domain.model.ChatbotReply
import com.quistock.quistock.domain.model.ChatbotRequest
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.port.ChatbotRepository

class SendMessageToChatbotUseCase(private val repository: ChatbotRepository) {
    suspend operator fun invoke(request: ChatbotRequest, expected: SessionSnapshot): ChatbotReply =
        repository.sendMessage(request, expected)

    suspend fun listEnded(expected: SessionSnapshot): List<ChatbotConversation> = repository.listEnded(expected)
    suspend fun history(conversationId: String, expected: SessionSnapshot): ChatbotConversationHistory =
        repository.history(conversationId, expected)
    suspend fun end(conversationId: String, expected: SessionSnapshot) = repository.end(conversationId, expected)
}
