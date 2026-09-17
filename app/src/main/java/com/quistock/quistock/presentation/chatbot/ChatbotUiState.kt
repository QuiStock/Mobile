package com.quistock.quistock.presentation.chatbot

interface ChatbotUiState {
    object Idle : ChatbotUiState
    object Loading : ChatbotUiState
    object Answered : ChatbotUiState
}
