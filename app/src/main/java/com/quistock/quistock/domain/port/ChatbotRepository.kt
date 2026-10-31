package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.ChatbotConversation
import com.quistock.quistock.domain.model.ChatbotConversationHistory
import com.quistock.quistock.domain.model.ChatbotReply
import com.quistock.quistock.domain.model.ChatbotRequest
import com.quistock.quistock.domain.model.SessionSnapshot

interface ChatbotRepository {
    suspend fun sendMessage(request: ChatbotRequest, expected: SessionSnapshot): ChatbotReply
    suspend fun listEnded(expected: SessionSnapshot): List<ChatbotConversation>
    suspend fun history(conversationId: String, expected: SessionSnapshot): ChatbotConversationHistory
    suspend fun end(conversationId: String, expected: SessionSnapshot)
}
