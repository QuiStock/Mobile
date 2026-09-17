package com.quistock.quistock.presentation.chatbot

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.ChatbotRequest
import com.quistock.quistock.domain.model.ChatbotResponse
import com.quistock.quistock.domain.usecase.SendMessageToChatbotUseCase
import kotlinx.coroutines.launch

class ChatbotViewModel(private val sendMessageToChatbot: SendMessageToChatbotUseCase) : ViewModel() {
    private val _answer = MutableLiveData<ChatbotResponse?>(null)
    val answer: LiveData<ChatbotResponse?> = _answer

    private val _uiState = MutableLiveData<ChatbotUiState>(ChatbotUiState.Idle)
    val uiState: LiveData<ChatbotUiState> = _uiState

    @Suppress("RethrowCaughtException")
    fun sendMessage(message: String) {
        if (_uiState.value == ChatbotUiState.Loading) return

        val request = ChatbotRequest(
            userId = "", // TODO: recover from shared preferences
            message = message,
        )

        _uiState.value = ChatbotUiState.Loading
        viewModelScope.launch {
            val response = sendMessageToChatbot(request)
            _answer.value = response
            _uiState.value = ChatbotUiState.Answered
        }

        _uiState.value = ChatbotUiState.Idle
    }
}
