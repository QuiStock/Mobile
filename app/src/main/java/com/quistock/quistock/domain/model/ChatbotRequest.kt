package com.quistock.quistock.domain.model

import java.io.IOException

data class ChatbotRequest(
    val conversationId: String,
    val message: String,
    val isResumingConversation: Boolean = false,
    val productCard: ChatbotProductCard? = null,
)

data class ChatbotProductCard(val productName: String, val categoryName: String)

data class ChatbotReply(val conversationId: String, val response: String, val status: String)

data class ChatbotConversation(val id: String, val title: String, val updatedAt: String)

data class ChatbotHistoryMessage(val id: String, val role: String, val content: String, val createdAt: String)

data class ChatbotConversationHistory(
    val conversationId: String,
    val title: String,
    val status: String,
    val messages: List<ChatbotHistoryMessage>,
)

class ChatbotHttpException(val status: Int) : IOException("Chatbot request failed: $status")
