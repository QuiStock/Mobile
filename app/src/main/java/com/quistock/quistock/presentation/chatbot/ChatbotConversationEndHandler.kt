package com.quistock.quistock.presentation.chatbot

import com.quistock.quistock.domain.model.ChatbotConversation
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.usecase.SendMessageToChatbotUseCase

internal class ChatbotConversationEndHandler(private val chatbot: SendMessageToChatbotUseCase) {
    suspend fun end(conversationId: String, expected: SessionSnapshot): Result<List<ChatbotConversation>>? {
        val endResult = chatbotResult { chatbot.end(conversationId, expected) }
        if (endResult.isFailure) {
            val history = chatbotResult { chatbot.history(conversationId, expected) }
            if (!history.getOrNull()?.status.equals("ended", ignoreCase = true)) return null
        }
        return chatbotResult { chatbot.listEnded(expected) }
    }
}
