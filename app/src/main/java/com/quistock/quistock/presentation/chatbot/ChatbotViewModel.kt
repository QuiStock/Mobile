package com.quistock.quistock.presentation.chatbot

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.ChatbotRequest
import com.quistock.quistock.domain.model.ChatbotResponse
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.port.UserPreferences
import com.quistock.quistock.domain.usecase.SendMessageToChatbotUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.coroutines.coroutineContext

@Deprecated("Legacy chatbot requires Firebase identity; API redesign pending", level = DeprecationLevel.WARNING)
class ChatbotViewModel(
    private val sendMessageToChatbot: SendMessageToChatbotUseCase,
    private val userPreferences: UserPreferences,
    private val session: SessionUseCase? = null,
) : ViewModel() {
    private val _answer = MutableLiveData<ChatbotResponse?>(null)
    val answer: LiveData<ChatbotResponse?> = _answer

    private val _uiState = MutableLiveData<ChatbotUiState>(ChatbotUiState.Idle)
    val uiState: LiveData<ChatbotUiState> = _uiState
    private var sendJob: Job? = null
    private val mutableMessages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = mutableMessages
    private var conversationGeneration: Long? = session?.snapshot()?.generation

    init {
        session?.let { current ->
            viewModelScope.launch {
                current.state.collect { state ->
                    val generation = current.snapshot().generation
                    if (generation != conversationGeneration || clearsConversation(state)) {
                        clearConversation()
                        conversationGeneration = generation
                    }
                }
            }
        }
    }

    fun clearConversation() {
        sendJob?.cancel()
        sendJob = null
        _answer.value = null
        _uiState.value = ChatbotUiState.Idle
        mutableMessages.value = emptyList()
    }

    fun rememberQuestion(message: String) {
        synchronizeSession()
        mutableMessages.value = mutableMessages.value.orEmpty() + ChatMessage(message, true)
    }

    fun synchronizeSession() {
        val generation = session?.snapshot()?.generation
        if (generation != conversationGeneration) {
            clearConversation()
            conversationGeneration = generation
        }
    }

    @Suppress("RethrowCaughtException")
    fun sendMessage(message: String) {
        synchronizeSession()
        if (_uiState.value == ChatbotUiState.Loading) return
        val generation = session?.snapshot()?.generation
        val userId = if (session == null || session.state.value == SessionState.Active) {
            userPreferences.getUserId()
        } else {
            null
        }
        if (userId == null) {
            _uiState.value = ChatbotUiState.Error.NOT_LOGGED_IN
            return
        }

        val request = ChatbotRequest(
            userId = userId,
            message = message,
        )

        _uiState.value = ChatbotUiState.Loading
        sendJob = viewModelScope.launch {
            try {
                val response = sendMessageToChatbot(request)
                coroutineContext.ensureActive()
                if (!isCurrent(generation)) return@launch
                _answer.value = response
                mutableMessages.value = mutableMessages.value.orEmpty() + ChatMessage(response.answer, false)
                _uiState.value = ChatbotUiState.Answered
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: IOException) {
                coroutineContext.ensureActive()
                if (isCurrent(generation)) _uiState.value = ChatbotUiState.Error.NETWORK
            } catch (_: Exception) {
                coroutineContext.ensureActive()
                if (isCurrent(generation)) _uiState.value = ChatbotUiState.Error.REQUEST_FAILED
            }
        }
    }

    private fun isCurrent(generation: Long?): Boolean =
        session == null || (session.snapshot().generation == generation && session.state.value == SessionState.Active)

    private fun clearsConversation(state: SessionState): Boolean = when (state) {
        SessionState.Active, SessionState.Restoring, is SessionState.Failure -> false
        else -> true
    }
}

data class ChatMessage(val text: String, val isUser: Boolean)
