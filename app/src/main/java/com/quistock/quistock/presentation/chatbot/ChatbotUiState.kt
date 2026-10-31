package com.quistock.quistock.presentation.chatbot

interface ChatbotUiState {
    object Idle : ChatbotUiState
    object Loading : ChatbotUiState
    object LoadingConversation : ChatbotUiState
    object Ending : ChatbotUiState
    object Answered : ChatbotUiState
    object Rejected : ChatbotUiState
    enum class Error : ChatbotUiState {
        NOT_LOGGED_IN,
        SEND_UNCERTAIN,
        REQUEST_FAILED,
        HISTORY_FAILED,
        HISTORY_REFRESH_FAILED,
        END_UNCERTAIN,
    }
}
