package com.quistock.quistock.presentation.chatbot

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.quistock.quistock.MainDispatcherRule
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.AlertRisk
import com.quistock.quistock.domain.model.ChatbotConversation
import com.quistock.quistock.domain.model.ChatbotConversationHistory
import com.quistock.quistock.domain.model.ChatbotHttpException
import com.quistock.quistock.domain.model.ChatbotProductCard
import com.quistock.quistock.domain.model.ChatbotReply
import com.quistock.quistock.domain.model.ChatbotRequest
import com.quistock.quistock.domain.model.CreationRisk
import com.quistock.quistock.domain.model.PriorityAlert
import com.quistock.quistock.domain.model.SessionBinding
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.StockFlow
import com.quistock.quistock.domain.port.ChatbotConversationStore
import com.quistock.quistock.domain.usecase.SendMessageToChatbotUseCase
import com.quistock.quistock.domain.usecase.SessionLocalAccess
import com.quistock.quistock.domain.usecase.SessionUseCase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class ChatbotViewModelTests {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `conversation UUID is persisted before the first write and confirmed response is shown`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        val session = activeSession()
        every { store.current("user-a") } returns null
        every { store.getOrCreate("user-a") } returns "stable-uuid"
        every { store.setActive("user-a", "stable-uuid") } returns Unit
        coEvery { send(ChatbotRequest("stable-uuid", "question"), SessionSnapshot(1, true)) } returns
            ChatbotReply("stable-uuid", "confirmed answer", "completed")
        val viewModel = ChatbotViewModel(send, store, session)

        viewModel.sendMessage("question")
        advanceUntilIdle()

        viewModel.messages.value shouldBe listOf(ChatMessage("question", true), ChatMessage("confirmed answer", false))
        viewModel.uiState.value shouldBe ChatbotUiState.Answered
        verifyOrder {
            store.getOrCreate("user-a")
            store.setActive("user-a", "stable-uuid")
        }
        coVerify(exactly = 1) { send(ChatbotRequest("stable-uuid", "question"), SessionSnapshot(1, true)) }
    }

    @Test
    fun `uncertain send is not retried implicitly and explicit retry keeps the same UUID`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns null
        every { store.getOrCreate("user-a") } returns "stable-uuid"
        every { store.setActive("user-a", "stable-uuid") } returns Unit
        coEvery { send(any(), any()) } throws IOException("offline")
        val viewModel = ChatbotViewModel(send, store, activeSession())

        viewModel.sendMessage("question")
        advanceUntilIdle()
        viewModel.uiState.value shouldBe ChatbotUiState.Error.SEND_UNCERTAIN
        coVerify(exactly = 1) { send(any(), any()) }
        viewModel.retryLastMessage()
        advanceUntilIdle()
        coVerify(exactly = 2) { send(ChatbotRequest("stable-uuid", "question"), any()) }
        viewModel.messages.value shouldBe listOf(ChatMessage("question", true))
    }

    @Test
    fun `rejected response is displayed and cannot be retried`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns null
        every { store.getOrCreate("user-a") } returns "stable-uuid"
        every { store.setActive("user-a", "stable-uuid") } returns Unit
        coEvery { send(any(), any()) } returns ChatbotReply("stable-uuid", "Pergunta não aceita.", "rejected")
        val viewModel = ChatbotViewModel(send, store, activeSession())

        viewModel.sendMessage("question")
        advanceUntilIdle()
        viewModel.uiState.value shouldBe ChatbotUiState.Rejected
        viewModel.messages.value shouldBe
            listOf(ChatMessage("question", true), ChatMessage("Pergunta não aceita.", false))
        viewModel.retryLastMessage()
        coVerify(exactly = 1) { send(any(), any()) }
    }

    @Test
    fun `definitive client error is shown without enabling a retry`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns null
        every { store.getOrCreate("user-a") } returns "stable-uuid"
        every { store.setActive("user-a", "stable-uuid") } returns Unit
        coEvery { send(any(), any()) } throws ChatbotHttpException(422)
        val viewModel = ChatbotViewModel(send, store, activeSession())

        viewModel.sendMessage("invalid question")
        advanceUntilIdle()
        viewModel.uiState.value shouldBe ChatbotUiState.Error.REQUEST_FAILED
        viewModel.retryLastMessage()

        coVerify(exactly = 1) { send(any(), any()) }
    }

    @Test
    fun `FEFO help attaches only the alert product name and category to the request`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns null
        every { store.getOrCreate("user-a") } returns "stable-uuid"
        every { store.setActive("user-a", "stable-uuid") } returns Unit
        coEvery { send(any(), any()) } returns ChatbotReply("stable-uuid", "answer", "completed")
        val viewModel = ChatbotViewModel(send, store, activeSession())
        val alert = PriorityAlert(
            AlertProduct(
                id = "product-id",
                name = "Leite",
                category = "Laticínios",
                stock = BigDecimal.ONE,
                expirationDays = 2,
                flow = StockFlow.MEDIUM,
                risks = setOf(CreationRisk.NEAR_EXPIRY),
            ),
            AlertRisk.EXPIRY,
        )

        viewModel.offerFefo(alert)
        viewModel.sendMessage("Ajude com este alerta")
        advanceUntilIdle()

        coVerify {
            send(
                ChatbotRequest(
                    "stable-uuid",
                    "Ajude com este alerta",
                    productCard = ChatbotProductCard("Leite", "Laticínios"),
                ),
                SessionSnapshot(1, true),
            )
        }
    }

    @Test
    fun `logout cancels pending work and clears visible conversation`() = runTest {
        val state = MutableStateFlow<SessionState>(SessionState.Active)
        val session = activeSession(state)
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns null
        every { store.getOrCreate("user-a") } returns "stable-uuid"
        every { store.setActive("user-a", any()) } returns Unit
        coEvery { send(any(), any()) } coAnswers { awaitCancellation() }
        val model = ChatbotViewModel(send, store, session)
        model.sendMessage("question")
        runCurrent()
        model.uiState.value shouldBe ChatbotUiState.Loading
        state.value = SessionState.LoggingOut
        runCurrent()
        model.messages.value shouldBe emptyList()
        model.sendMessage("after logout")
        model.uiState.value shouldBe ChatbotUiState.Error.NOT_LOGGED_IN
        coVerify(exactly = 1) { send(any(), any()) }
    }

    @Test
    fun `ending the current conversation is confirmed before its saved id is cleared`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns "old-id"
        every { store.clear("user-a") } returns Unit
        coEvery { send.end("old-id", SessionSnapshot(1, true)) } returns Unit
        coEvery { send.history("old-id", SessionSnapshot(1, true)) } returns
            ChatbotConversationHistory("old-id", "Ativa", "active", emptyList())
        coEvery { send.listEnded(SessionSnapshot(1, true)) } returns
            listOf(ChatbotConversation("old-id", "Encerrada agora", "2026-10-31T10:00:00Z"))
        val viewModel = ChatbotViewModel(send, store, activeSession())
        viewModel.load()

        advanceUntilIdle()
        viewModel.newConversation()
        advanceUntilIdle()

        coVerifyOrder {
            send.end("old-id", SessionSnapshot(1, true))
            store.clear("user-a")
        }
        viewModel.endedConversations.value shouldBe
            listOf(ChatbotConversation("old-id", "Encerrada agora", "2026-10-31T10:00:00Z"))
        viewModel.messages.value shouldBe emptyList()
    }

    @Test
    fun `uncertain end keeps the conversation and exposes a recoverable error`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns "old-id"
        coEvery { send.end("old-id", SessionSnapshot(1, true)) } throws IOException("offline")
        coEvery { send.history("old-id", SessionSnapshot(1, true)) } returns
            ChatbotConversationHistory("old-id", "Title", "active", emptyList())
        coEvery { send.listEnded(SessionSnapshot(1, true)) } returns emptyList()
        val viewModel = ChatbotViewModel(send, store, activeSession())
        viewModel.load()
        advanceUntilIdle()

        viewModel.newConversation()
        advanceUntilIdle()

        viewModel.uiState.value shouldBe ChatbotUiState.Error.END_UNCERTAIN
        verify(exactly = 0) { store.clear("user-a") }
        coVerify(exactly = 1) { send.listEnded(SessionSnapshot(1, true)) }
    }

    @Test
    fun `failed end is accepted when history confirms the conversation already ended`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns "old-id"
        every { store.clear("user-a") } returns Unit
        coEvery { send.end("old-id", SessionSnapshot(1, true)) } throws IOException("response lost")
        coEvery { send.history("old-id", SessionSnapshot(1, true)) } returns
            ChatbotConversationHistory("old-id", "Title", "ended", emptyList())
        var listCalls = 0
        val ended = ChatbotConversation("old-id", "Encerrada", "2026-10-31T10:00:00Z")
        coEvery { send.listEnded(SessionSnapshot(1, true)) } coAnswers {
            if (listCalls++ == 0) emptyList() else listOf(ended)
        }
        val viewModel = ChatbotViewModel(send, store, activeSession())
        viewModel.load()
        advanceUntilIdle()

        viewModel.newConversation()
        advanceUntilIdle()

        viewModel.uiState.value shouldBe ChatbotUiState.Idle
        viewModel.endedConversations.value shouldBe listOf(ended)
        verify(exactly = 1) { store.clear("user-a") }
    }

    @Test
    fun `confirmed end with failed history refresh clears local state and reports refresh failure`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns "old-id"
        every { store.clear("user-a") } returns Unit
        coEvery { send.end("old-id", SessionSnapshot(1, true)) } returns Unit
        var listCalls = 0
        coEvery { send.history("old-id", SessionSnapshot(1, true)) } returns
            ChatbotConversationHistory("old-id", "Title", "active", emptyList())
        coEvery { send.listEnded(SessionSnapshot(1, true)) } coAnswers {
            if (listCalls++ == 0) emptyList() else throw IOException("offline")
        }
        val viewModel = ChatbotViewModel(send, store, activeSession())
        viewModel.load()
        advanceUntilIdle()

        viewModel.newConversation()
        advanceUntilIdle()

        viewModel.uiState.value shouldBe ChatbotUiState.Error.HISTORY_REFRESH_FAILED
        verify(exactly = 1) { store.clear("user-a") }
        viewModel.messages.value shouldBe emptyList()
    }

    @Test
    fun `history and new conversation do not cancel an in flight send`() = runTest {
        val state = MutableStateFlow<SessionState>(SessionState.Active)
        val session = activeSession(state)
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns null
        every { store.getOrCreate("user-a") } returns "stable-id"
        every { store.setActive("user-a", "stable-id") } returns Unit
        coEvery { send(any(), any()) } coAnswers { awaitCancellation() }
        val model = ChatbotViewModel(send, store, session)

        model.sendMessage("in flight")
        runCurrent()
        model.openHistory("past-id")
        model.newConversation()
        runCurrent()

        coVerify(exactly = 1) { send(any(), any()) }
        coVerify(exactly = 0) { send.history(any(), any()) }
        coVerify(exactly = 0) { send.end(any(), any()) }
        state.value = SessionState.LoggingOut
        runCurrent()
        model.messages.value shouldBe emptyList()
    }

    @Test
    fun `conversation and history are cleared when account changes`() = runTest {
        var binding = binding("user-a")
        var generation = 1L
        val state = MutableStateFlow<SessionState>(SessionState.Active)
        val session = activeSession(state, binding, generation) { binding to generation }
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns "first-id"
        every { store.current("user-b") } returns "second-id"
        every { store.setActive("user-b", "second-id") } returns Unit
        coEvery { send.history("first-id", SessionSnapshot(1, true)) } returns
            ChatbotConversationHistory("first-id", "Conversa", "active", emptyList())
        coEvery { send.listEnded(SessionSnapshot(1, true)) } returns emptyList()
        coEvery { send(ChatbotRequest("second-id", "second account"), SessionSnapshot(2, true)) } returns
            ChatbotReply("second-id", "reply", "completed")
        val model = ChatbotViewModel(send, store, session)
        model.load()
        advanceUntilIdle()

        binding = binding("user-b")
        generation = 2
        state.value = SessionState.Active
        model.synchronizeSession()
        model.messages.value shouldBe emptyList()
        model.sendMessage("second account")
        model.uiState.value shouldBe ChatbotUiState.Loading
        verify { store.current("user-b") }
    }

    private fun activeSession(
        state: MutableStateFlow<SessionState> = MutableStateFlow(SessionState.Active),
        binding: SessionBinding = binding("user-a"),
        generation: Long = 1,
        current: (() -> Pair<SessionBinding, Long>)? = null,
    ): SessionUseCase {
        val local = mockk<SessionLocalAccess>()
        val session = mockk<SessionUseCase>()
        every { session.state } returns state
        every { session.snapshot() } answers { SessionSnapshot(current?.invoke()?.second ?: generation, true) }
        every { session.local } returns local
        every { local.binding() } answers { current?.invoke()?.first ?: binding }
        return session
    }

    private fun binding(userId: String) = SessionBinding("session-$userId", userId, 1, Long.MAX_VALUE)
}
