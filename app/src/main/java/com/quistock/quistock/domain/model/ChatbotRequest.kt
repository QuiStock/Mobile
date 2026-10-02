package com.quistock.quistock.domain.model

@Deprecated("Legacy chatbot requires Firebase identity; API redesign pending", level = DeprecationLevel.WARNING)
data class ChatbotRequest(val userId: String, val message: String)
