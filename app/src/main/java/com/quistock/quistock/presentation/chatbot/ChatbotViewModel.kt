package com.quistock.quistock.presentation.chatbot

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.ChatbotConversation
import com.quistock.quistock.domain.model.ChatbotHttpException
import com.quistock.quistock.domain.model.ChatbotProductCard
import com.quistock.quistock.domain.model.ChatbotRequest
import com.quistock.quistock.domain.model.PriorityAlert
import com.quistock.quistock.domain.model.ProfileRejection
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.port.ChatbotConversationStore
import com.quistock.quistock.domain.usecase.ProfileUseCase
import com.quistock.quistock.domain.usecase.SendMessageToChatbotUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import java.io.IOException
import kotlin.coroutines.coroutineContext

private const val HTTP_CLIENT_ERROR_MIN = 400
private const val HTTP_CLIENT_ERROR_MAX = 499

private data class PreparedSend(val conversationId: String, val expected: SessionSnapshot, val account: String)

private fun isCurrentSession(session: SessionUseCase, expected: SessionSnapshot): Boolean =
    session.snapshot().generation == expected.generation && session.state.value == SessionState.Active

private fun profileFailureState(state: ProfileState): ChatbotUiState.Error = when {
    state is ProfileState.Rejected && state.reason == ProfileRejection.NO_STORE ->
        if (state.role == UserRole.MANAGER) {
            ChatbotUiState.Error.PROFILE_NO_STORE_MANAGER
        } else {
            ChatbotUiState.Error.PROFILE_NO_STORE_EMPLOYEE
        }

    else -> ChatbotUiState.Error.PROFILE_FAILED
}

private fun chatbotAccount(
    session: SessionUseCase,
    profile: ProfileUseCase,
    onUnavailable: (ChatbotUiState.Error) -> Unit,
): String? {
    if (session.state.value != SessionState.Active) {
        onUnavailable(ChatbotUiState.Error.NOT_LOGGED_IN)
    } else {
        val account = session.local.binding()?.userId
        when (val state = profile.state.value) {
            ProfileState.Empty, ProfileState.Loading -> onUnavailable(ChatbotUiState.Error.PROFILE_LOADING)

            is ProfileState.Failure, is ProfileState.Rejected -> onUnavailable(profileFailureState(state))

            is ProfileState.Ready -> if (account == null || state.generation != session.snapshot().generation ||
                state.profile.id != account
            ) {
                onUnavailable(ChatbotUiState.Error.PROFILE_FAILED)
            } else {
                return account
            }
        }
    }
    return null
}

internal suspend fun <T> chatbotResult(operation: suspend () -> T): Result<T> = try {
    Result.success(operation())
} catch (cancel: CancellationException) {
    throw cancel
} catch (error: IOException) {
    Result.failure(error)
} catch (error: SerializationException) {
    Result.failure(error)
}

class ChatbotViewModel(
    private val chatbot: SendMessageToChatbotUseCase,
    private val conversations: ChatbotConversationStore,
    private val session: SessionUseCase,
    private val profile: ProfileUseCase,
) : ViewModel() {
    private val _answer = MutableLiveData<String?>(null)
    val answer: LiveData<String?> = _answer
    private val _uiState = MutableLiveData<ChatbotUiState>(ChatbotUiState.Idle)
    val uiState: LiveData<ChatbotUiState> = _uiState
    private val mutableMessages = MutableLiveData<List<ChatMessage>>(emptyList())
    val messages: LiveData<List<ChatMessage>> = mutableMessages
    private val mutableEndedConversations = MutableLiveData<List<ChatbotConversation>>(emptyList())
    val endedConversations: LiveData<List<ChatbotConversation>> = mutableEndedConversations

    private var loadJob: Job? = null
    private var sendJob: Job? = null
    private var endJob: Job? = null
    private var userId: String? = null
    private var conversationId: String? = null
    private var displayedConversationId: String? = null
    private var isResuming = false
    private var lastMessage: String? = null
    private var retryCard: ChatbotProductCard? = null
    private var retryAvailable = false
    private var pendingCard: ChatbotProductCard? = null
    private var generation = session.snapshot().generation
    private var loadRequested = false
    private val endHandler = ChatbotConversationEndHandler(chatbot)

    init {
        viewModelScope.launch {
            session.state.collect { synchronizeSession() }
        }
        viewModelScope.launch {
            profile.state.collect { state ->
                if (!loadRequested) return@collect
                when (state) {
                    ProfileState.Empty, ProfileState.Loading -> if (
                        _uiState.value == ChatbotUiState.Error.PROFILE_FAILED
                    ) {
                        _uiState.value = ChatbotUiState.Error.PROFILE_LOADING
                    }

                    is ProfileState.Failure, is ProfileState.Rejected ->
                        if (_uiState.value == ChatbotUiState.Error.PROFILE_LOADING) {
                            _uiState.value = if (state is ProfileState.Rejected) {
                                profileFailureState(state)
                            } else {
                                ChatbotUiState.Error.PROFILE_FAILED
                            }
                        }

                    is ProfileState.Ready -> if (
                        _uiState.value in
                        setOf(ChatbotUiState.Error.PROFILE_LOADING, ChatbotUiState.Error.PROFILE_FAILED)
                    ) {
                        load()
                    }
                }
            }
        }
    }

    fun load() {
        synchronizeSession()
        loadRequested = true
        val id = chatbotAccount(session, profile) { _uiState.value = it }
        if (id != null && loadJob?.isActive != true && sendJob?.isActive != true) {
            loadJob = viewModelScope.launch {
                val expected = session.snapshot()
                val active = conversations.current(id)
                userId = id
                conversationId = active
                _uiState.value = ChatbotUiState.LoadingConversation
                if (active == null) {
                    mutableMessages.value = emptyList()
                } else {
                    loadConversation(active, expected)
                }
                val jobContext = coroutineContext
                chatbotResult { chatbot.listEnded(expected) }.fold(
                    onSuccess = { ended ->
                        jobContext.ensureActive()
                        if (isCurrentSession(session, expected)) mutableEndedConversations.value = ended
                    },
                    onFailure = {
                        if (isCurrentSession(session, expected)) _uiState.value = ChatbotUiState.Error.HISTORY_FAILED
                    },
                )
                if (isCurrentSession(session, expected) && _uiState.value == ChatbotUiState.LoadingConversation) {
                    _uiState.value = ChatbotUiState.Idle
                }
            }
        }
    }

    private suspend fun loadConversation(id: String, expected: SessionSnapshot) {
        val jobContext = coroutineContext
        chatbotResult { chatbot.history(id, expected) }.fold(
            onSuccess = { history ->
                jobContext.ensureActive()
                if (!isCurrentSession(session, expected)) return@fold
                displayedConversationId = id
                isResuming = history.status.equals("ended", true)
                mutableMessages.value = history.messages.mapNotNull { message ->
                    when (message.role.lowercase()) {
                        "user" -> ChatMessage(message.content, true)
                        "assistant" -> ChatMessage(message.content, false)
                        else -> null
                    }
                }
            },
            onFailure = {
                if (isCurrentSession(session, expected)) _uiState.value = ChatbotUiState.Error.HISTORY_FAILED
            },
        )
    }

    fun openHistory(id: String) {
        synchronizeSession()
        val activeAccount = chatbotAccount(session, profile) { _uiState.value = it }
        if (activeAccount != null && _uiState.value !in
            setOf(ChatbotUiState.Loading, ChatbotUiState.LoadingConversation, ChatbotUiState.Ending)
        ) {
            loadJob?.cancel()
            val expected = session.snapshot()
            _uiState.value = ChatbotUiState.LoadingConversation
            loadJob = viewModelScope.launch {
                try {
                    val history = chatbot.history(id, expected)
                    coroutineContext.ensureActive()
                    if (!isCurrentSession(session, expected)) return@launch
                    displayedConversationId = id
                    isResuming = history.status.equals("ended", true)
                    mutableMessages.value = history.messages.mapNotNull { message ->
                        when (message.role.lowercase()) {
                            "user" -> ChatMessage(message.content, true)
                            "assistant" -> ChatMessage(message.content, false)
                            else -> null
                        }
                    }
                    _uiState.value = ChatbotUiState.Idle
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    if (isCurrentSession(session, expected)) _uiState.value = ChatbotUiState.Error.HISTORY_FAILED
                }
            }
        }
    }

    fun offerFefo(product: AlertProduct) {
        synchronizeSession()
        pendingCard = ChatbotProductCard(product.name, product.category)
    }

    fun sendMessage(message: String): Boolean = send(message, pendingCard, retry = false)

    fun retryLastMessage() {
        if (retryAvailable) lastMessage?.let { send(it, retryCard, retry = true) }
    }

    private fun send(message: String, card: ChatbotProductCard?, retry: Boolean): Boolean {
        synchronizeSession()
        val prepared = if (_uiState.value !in
            setOf(ChatbotUiState.Loading, ChatbotUiState.LoadingConversation, ChatbotUiState.Ending)
        ) {
            prepareSend(message, card, retry)
        } else {
            null
        }
        if (prepared != null) {
            sendJob = viewModelScope.launch {
                val result = chatbotResult {
                    if (conversationId != null && conversationId != prepared.conversationId) {
                        chatbot.end(requireNotNull(conversationId), prepared.expected)
                        conversations.clear(prepared.account)
                        conversations.setActive(prepared.account, prepared.conversationId)
                        conversationId = prepared.conversationId
                    } else if (conversationId == null) {
                        conversations.setActive(prepared.account, prepared.conversationId)
                        conversationId = prepared.conversationId
                    }
                    chatbot(
                        ChatbotRequest(prepared.conversationId, message, isResuming, card),
                        prepared.expected,
                    )
                }
                coroutineContext.ensureActive()
                finishSend(result, prepared.expected, prepared.account)
            }
        }
        return prepared != null
    }

    private fun prepareSend(message: String, card: ChatbotProductCard?, retry: Boolean): PreparedSend? {
        val expected = session.snapshot()
        val account = chatbotAccount(session, profile) { _uiState.value = it } ?: return null
        if (account != userId) {
            userId = account
            conversationId = conversations.current(account)
            mutableMessages.value = emptyList()
        }
        val id =
            displayedConversationId ?: conversationId ?: conversations.getOrCreate(account).also { conversationId = it }
        val isRetry = retry && retryAvailable && message == lastMessage && retryCard == card
        if (!isRetry) mutableMessages.value = mutableMessages.value.orEmpty() + ChatMessage(message, true)
        retryAvailable = false
        lastMessage = message
        retryCard = card
        pendingCard = null
        displayedConversationId = id
        _uiState.value = ChatbotUiState.Loading
        return PreparedSend(id, expected, account)
    }

    private fun finishSend(
        result: Result<com.quistock.quistock.domain.model.ChatbotReply>,
        expected: SessionSnapshot,
        account: String,
    ) {
        if (!isCurrentSession(session, expected)) return
        result.fold(
            onSuccess = { response ->
                conversationId = response.conversationId
                _answer.value = response.response
                mutableMessages.value = mutableMessages.value.orEmpty() + ChatMessage(response.response, false)
                conversations.setActive(account, response.conversationId)
                isResuming = false
                retryCard = null
                retryAvailable = false
                lastMessage = null
                _uiState.value = if (response.status.equals("rejected", true)) {
                    ChatbotUiState.Rejected
                } else {
                    ChatbotUiState.Answered
                }
            },
            onFailure = { error ->
                _uiState.value = if (error is ChatbotHttpException &&
                    error.status in HTTP_CLIENT_ERROR_MIN..HTTP_CLIENT_ERROR_MAX
                ) {
                    ChatbotUiState.Error.REQUEST_FAILED
                } else {
                    retryAvailable = true
                    ChatbotUiState.Error.SEND_UNCERTAIN
                }
            },
        )
    }

    fun newConversation() {
        synchronizeSession()
        val activeAccount = chatbotAccount(session, profile) { _uiState.value = it }
        if (activeAccount != null && _uiState.value !in
            setOf(ChatbotUiState.Loading, ChatbotUiState.LoadingConversation, ChatbotUiState.Ending)
        ) {
            val id = conversationId
            val account = userId
            if (id == null) {
                displayedConversationId = null
                lastMessage = null
                retryCard = null
                retryAvailable = false
                pendingCard = null
                _answer.value = null
                mutableMessages.value = emptyList()
                _uiState.value = ChatbotUiState.Idle
            } else if (account != null) {
                val expected = session.snapshot()
                _uiState.value = ChatbotUiState.Ending
                endJob = viewModelScope.launch {
                    val result = endHandler.end(id, expected)
                    coroutineContext.ensureActive()
                    if (!isCurrentSession(session, expected)) return@launch
                    if (result == null) {
                        _uiState.value = ChatbotUiState.Error.END_UNCERTAIN
                        return@launch
                    }
                    conversations.clear(account)
                    conversationId = null
                    isResuming = false
                    displayedConversationId = null
                    lastMessage = null
                    retryCard = null
                    retryAvailable = false
                    pendingCard = null
                    _answer.value = null
                    mutableMessages.value = emptyList()
                    result.fold(
                        onSuccess = {
                            mutableEndedConversations.value = it
                            _uiState.value = ChatbotUiState.Idle
                        },
                        onFailure = { _uiState.value = ChatbotUiState.Error.HISTORY_REFRESH_FAILED },
                    )
                }
            }
        }
    }

    fun synchronizeSession() {
        val currentGeneration = session.snapshot().generation
        val account = if (session.state.value == SessionState.Active) session.local.binding()?.userId else null
        if (currentGeneration != generation || (userId != null && account != userId)) {
            loadJob?.cancel()
            sendJob?.cancel()
            endJob?.cancel()
            generation = currentGeneration
            userId = account
            conversationId = account?.let(conversations::current)
            displayedConversationId = conversationId
            isResuming = false
            mutableEndedConversations.value = emptyList()
            displayedConversationId = null
            lastMessage = null
            retryCard = null
            retryAvailable = false
            pendingCard = null
            _answer.value = null
            mutableMessages.value = emptyList()
            _uiState.value = ChatbotUiState.Idle
            loadRequested = false
        }
    }
}

data class ChatMessage(val text: String, val isUser: Boolean)

fun ChatbotViewModel.offerFefo(alert: PriorityAlert) = offerFefo(alert.product)
