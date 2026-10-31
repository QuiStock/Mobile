package com.quistock.quistock.domain.port

interface ChatbotConversationStore {
    fun current(userId: String): String?
    fun getOrCreate(userId: String): String
    fun setActive(userId: String, conversationId: String)
    fun clear(userId: String)
}
