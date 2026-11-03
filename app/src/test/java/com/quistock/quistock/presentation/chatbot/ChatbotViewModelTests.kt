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
import com.quistock.quistock.domain.model.ProfileRejection
import com.quistock.quistock.domain.model.ProfileState
import com.quistock.quistock.domain.model.SessionBinding
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.model.StockFlow
import com.quistock.quistock.domain.model.UserProfile
import com.quistock.quistock.domain.model.UserRole
import com.quistock.quistock.domain.model.UserStatus
import com.quistock.quistock.domain.port.ChatbotConversationStore
import com.quistock.quistock.domain.usecase.ProfileUseCase
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
import kotlinx.serialization.SerializationException
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class ChatbotViewModelTests {
    @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `serialization failures are returned as chatbot operation failures`() = runTest {
        val error = SerializationException("invalid response")

        chatbotResult { throw error }.exceptionOrNull() shouldBe error
    }

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
        val viewModel = ChatbotViewModel(send, store, session, readyProfile())

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
    fun `load without an active session does not call chatbot services`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        val state = MutableStateFlow<SessionState>(SessionState.LoggingOut)
        val viewModel = ChatbotViewModel(send, store, activeSession(state), readyProfile())

        viewModel.load()

        viewModel.uiState.value shouldBe ChatbotUiState.Error.NOT_LOGGED_IN
        coVerify(exactly = 0) { send(any(), any()) }
        coVerify(exactly = 0) { send.listEnded(any()) }
        verify(exactly = 0) { store.current(any()) }
    }

    @Test
    fun `send distinguishes an absent session from profile loading and failure`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        val absent = ChatbotViewModel(
            send,
            store,
            activeSession(MutableStateFlow(SessionState.SignedOut)),
            readyProfile(),
        )
        val loadingState = MutableStateFlow<ProfileState>(ProfileState.Loading)
        val profile = mockk<ProfileUseCase> { every { state } returns loadingState }
        val loading = ChatbotViewModel(send, store, activeSession(), profile)
        val bindingPending = ChatbotViewModel(send, store, activeSession(binding = null), profile)

        absent.sendMessage("draft") shouldBe false
        absent.uiState.value shouldBe ChatbotUiState.Error.NOT_LOGGED_IN
        loading.sendMessage("draft") shouldBe false
        loading.uiState.value shouldBe ChatbotUiState.Error.PROFILE_LOADING
        bindingPending.sendMessage("draft") shouldBe false
        bindingPending.uiState.value shouldBe ChatbotUiState.Error.PROFILE_LOADING

        loadingState.value = ProfileState.Failure()
        loading.sendMessage("draft") shouldBe false
        loading.uiState.value shouldBe ChatbotUiState.Error.PROFILE_FAILED
        loadingState.value = ProfileState.Rejected(ProfileRejection.NO_STORE, UserRole.EMPLOYEE)
        bindingPending.sendMessage("draft") shouldBe false
        bindingPending.uiState.value shouldBe ChatbotUiState.Error.PROFILE_NO_STORE_EMPLOYEE
        coVerify(exactly = 0) { send(any(), any()) }
        verify(exactly = 0) { store.getOrCreate(any()) }
    }

    @Test
    fun `profile becoming ready never sends a held message and preserves FEFO context`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        val profileState = MutableStateFlow<ProfileState>(ProfileState.Loading)
        val profile = mockk<ProfileUseCase> { every { state } returns profileState }
        every { store.current("user-a") } returns null
        every { store.getOrCreate("user-a") } returns "stable-uuid"
        every { store.setActive("user-a", "stable-uuid") } returns Unit
        coEvery { send(any(), any()) } returns ChatbotReply("stable-uuid", "answer", "completed")
        val viewModel = ChatbotViewModel(send, store, activeSession(), profile)
        viewModel.offerFefo(
            PriorityAlert(
                AlertProduct(
                    "product-id",
                    "Leite",
                    "Laticínios",
                    BigDecimal.ONE,
                    2,
                    StockFlow.MEDIUM,
                    risks = setOf(CreationRisk.NEAR_EXPIRY),
                ),
                AlertRisk.EXPIRY,
            ),
        )

        viewModel.sendMessage("draft") shouldBe false
        profileState.value = ProfileState.Ready(1, testProfile("user-a"))
        advanceUntilIdle()

        coVerify(exactly = 0) { send(any(), any()) }
        viewModel.sendMessage("draft") shouldBe true
        advanceUntilIdle()
        coVerify(exactly = 1) {
            send(
                ChatbotRequest("stable-uuid", "draft", productCard = ChatbotProductCard("Leite", "Laticínios")),
                SessionSnapshot(1, true),
            )
        }
    }

    @Test
    fun `repeated load is ignored while the first request is active`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        val state = MutableStateFlow<SessionState>(SessionState.Active)
        every { store.current("user-a") } returns null
        coEvery { send.listEnded(SessionSnapshot(1, true)) } coAnswers { awaitCancellation() }
        val viewModel = ChatbotViewModel(send, store, activeSession(state), readyProfile())

        viewModel.load()
        viewModel.load()
        runCurrent()

        verify(exactly = 1) { store.current("user-a") }
        coVerify(exactly = 1) { send.listEnded(SessionSnapshot(1, true)) }
        state.value = SessionState.LoggingOut
        runCurrent()
    }

    @Test
    fun `uncertain send is not retried implicitly and explicit retry keeps the same UUID`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns null
        every { store.getOrCreate("user-a") } returns "stable-uuid"
        every { store.setActive("user-a", "stable-uuid") } returns Unit
        coEvery { send(any(), any()) } throws IOException("offline")
        val viewModel = ChatbotViewModel(send, store, activeSession(), readyProfile())

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
        val viewModel = ChatbotViewModel(send, store, activeSession(), readyProfile())

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
        val viewModel = ChatbotViewModel(send, store, activeSession(), readyProfile())

        viewModel.sendMessage("invalid question")
        advanceUntilIdle()
        viewModel.uiState.value shouldBe ChatbotUiState.Error.REQUEST_FAILED
        viewModel.retryLastMessage()

        coVerify(exactly = 1) { send(any(), any()) }
    }

    @Test
    fun `FEFO help attaches selected product name and category to the current conversation`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns null
        every { store.getOrCreate("user-a") } returns "stable-uuid"
        every { store.setActive("user-a", "stable-uuid") } returns Unit
        coEvery { send(any(), any()) } returns ChatbotReply("stable-uuid", "answer", "completed")
        val viewModel = ChatbotViewModel(send, store, activeSession(), readyProfile())
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

        viewModel.offerFefo(alert.product)
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
    fun `offering FEFO after account change attaches product to the new account conversation`() = runTest {
        var accountBinding = binding("user-a")
        var generation = 1L
        val state = MutableStateFlow<SessionState>(SessionState.Active)
        val session = activeSession(state, accountBinding, generation) { accountBinding to generation }
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns "first-id"
        every { store.current("user-b") } returns "second-id"
        every { store.setActive("user-b", "second-id") } returns Unit
        coEvery { send.history("first-id", SessionSnapshot(1, true)) } returns
            ChatbotConversationHistory("first-id", "First", "active", emptyList())
        coEvery { send.listEnded(SessionSnapshot(1, true)) } returns emptyList()
        coEvery {
            send(
                ChatbotRequest("second-id", "new account", productCard = ChatbotProductCard("Arroz", "Grãos")),
                SessionSnapshot(2, true),
            )
        } returns ChatbotReply("second-id", "answer", "completed")
        val viewModel = ChatbotViewModel(send, store, session)
        viewModel.load()
        advanceUntilIdle()

        accountBinding = binding("user-b")
        generation = 2
        viewModel.offerFefo(
            AlertProduct(
                id = "product-b",
                name = "Arroz",
                category = "Grãos",
                stock = BigDecimal.ONE,
                expirationDays = null,
                flow = null,
            ),
        )
        viewModel.sendMessage("new account")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            send(
                ChatbotRequest("second-id", "new account", productCard = ChatbotProductCard("Arroz", "Grãos")),
                SessionSnapshot(2, true),
            )
        }
        verify { store.current("user-b") }
        coVerify(exactly = 0) { send.end("first-id", any()) }
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
        val profileState = MutableStateFlow<ProfileState>(ProfileState.Ready(1, testProfile("user-a")))
        val profileUseCase = mockk<ProfileUseCase>()
        every { profileUseCase.state } returns profileState
        val model = ChatbotViewModel(send, store, session, profileUseCase)
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
        val viewModel = ChatbotViewModel(send, store, activeSession(), readyProfile())
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
        val viewModel = ChatbotViewModel(send, store, activeSession(), readyProfile())
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
        val viewModel = ChatbotViewModel(send, store, activeSession(), readyProfile())
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
        val viewModel = ChatbotViewModel(send, store, activeSession(), readyProfile())
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
        val profileState = MutableStateFlow<ProfileState>(ProfileState.Ready(1, testProfile("user-a")))
        val profile = mockk<ProfileUseCase>()
        every { profile.state } returns profileState
        val model = ChatbotViewModel(send, store, session, profile)

        model.sendMessage("in flight")
        runCurrent()
        model.sendMessage("duplicate")
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
        val profileState = MutableStateFlow<ProfileState>(ProfileState.Ready(1, testProfile("user-a")))
        val profile = mockk<ProfileUseCase>()
        every { profile.state } returns profileState
        val model = ChatbotViewModel(send, store, session, profile)
        model.load()
        advanceUntilIdle()

        binding = binding("user-b")
        generation = 2
        profileState.value = ProfileState.Ready(2, testProfile("user-b"))
        state.value = SessionState.Active
        model.synchronizeSession()
        model.messages.value shouldBe emptyList()
        model.sendMessage("second account")
        model.uiState.value shouldBe ChatbotUiState.Loading
        verify { store.current("user-b") }
    }

    @Test
    fun `load keeps history error while still publishing the ended list`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns "active-id"
        val ended = ChatbotConversation("ended-id", "Past", "2026-10-31T10:00:00Z")
        coEvery { send.history("active-id", SessionSnapshot(1, true)) } throws IOException("offline")
        coEvery { send.listEnded(SessionSnapshot(1, true)) } returns listOf(ended)
        val viewModel = ChatbotViewModel(send, store, activeSession(), readyProfile())

        viewModel.load()
        advanceUntilIdle()

        viewModel.uiState.value shouldBe ChatbotUiState.Error.HISTORY_FAILED
        viewModel.endedConversations.value shouldBe listOf(ended)
    }

    @Test
    fun `opening history reports failure and preserves the current active conversation`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns "active-id"
        coEvery { send.history("past-id", SessionSnapshot(1, true)) } throws IOException("offline")
        val viewModel = ChatbotViewModel(send, store, activeSession(), readyProfile())

        viewModel.openHistory("past-id")
        advanceUntilIdle()

        viewModel.uiState.value shouldBe ChatbotUiState.Error.HISTORY_FAILED
        verify(exactly = 0) { store.clear("user-a") }
    }

    @Test
    fun `sending from a loaded past conversation ends and replaces the prior active conversation`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns "active-id"
        every { store.clear("user-a") } returns Unit
        every { store.setActive("user-a", "past-id") } returns Unit
        coEvery { send.history("active-id", SessionSnapshot(1, true)) } returns
            ChatbotConversationHistory("active-id", "Active", "active", emptyList())
        coEvery { send.history("past-id", SessionSnapshot(1, true)) } returns
            ChatbotConversationHistory("past-id", "Past", "ended", emptyList())
        coEvery { send.listEnded(SessionSnapshot(1, true)) } returns emptyList()
        coEvery { send.end("active-id", SessionSnapshot(1, true)) } returns Unit
        coEvery {
            send(ChatbotRequest("past-id", "continue", true), SessionSnapshot(1, true))
        } returns ChatbotReply("past-id", "answer", "completed")
        val viewModel = ChatbotViewModel(send, store, activeSession(), readyProfile())
        viewModel.load()
        advanceUntilIdle()

        viewModel.openHistory("past-id")
        advanceUntilIdle()
        viewModel.sendMessage("continue")
        advanceUntilIdle()

        coVerifyOrder {
            send.end("active-id", SessionSnapshot(1, true))
            store.clear("user-a")
            store.setActive("user-a", "past-id")
            send(ChatbotRequest("past-id", "continue", true), SessionSnapshot(1, true))
        }
        viewModel.uiState.value shouldBe ChatbotUiState.Answered
    }

    @Test
    fun `sending from history without a saved active id activates the resumed conversation`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns null
        every { store.setActive("user-a", "past-id") } returns Unit
        coEvery { send.listEnded(SessionSnapshot(1, true)) } returns emptyList()
        coEvery { send.history("past-id", SessionSnapshot(1, true)) } returns
            ChatbotConversationHistory("past-id", "Past", "ended", emptyList())
        coEvery {
            send(ChatbotRequest("past-id", "continue", true), SessionSnapshot(1, true))
        } returns ChatbotReply("past-id", "answer", "completed")
        val viewModel = ChatbotViewModel(send, store, activeSession(), readyProfile())
        viewModel.load()
        advanceUntilIdle()

        viewModel.openHistory("past-id")
        advanceUntilIdle()
        viewModel.sendMessage("continue")
        advanceUntilIdle()

        verifyOrder {
            store.setActive("user-a", "past-id")
        }
        coVerify(exactly = 1) {
            send(ChatbotRequest("past-id", "continue", true), SessionSnapshot(1, true))
        }
    }

    @Test
    fun `starting a conversation without an active id clears a pending FEFO card`() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns null
        every { store.getOrCreate("user-a") } returns "new-id"
        every { store.setActive("user-a", "new-id") } returns Unit
        coEvery { send.listEnded(SessionSnapshot(1, true)) } returns emptyList()
        coEvery { send(ChatbotRequest("new-id", "new conversation"), SessionSnapshot(1, true)) } returns
            ChatbotReply("new-id", "answer", "completed")
        val viewModel = ChatbotViewModel(send, store, activeSession(), readyProfile())
        viewModel.load()
        advanceUntilIdle()
        viewModel.offerFefo(
            PriorityAlert(
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
            ),
        )

        viewModel.newConversation()
        viewModel.sendMessage("new conversation")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            send(ChatbotRequest("new-id", "new conversation"), SessionSnapshot(1, true))
        }
    }

    private fun activeSession(
        state: MutableStateFlow<SessionState> = MutableStateFlow(SessionState.Active),
        binding: SessionBinding? = binding("user-a"),
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

    private fun readyProfile() = mockk<ProfileUseCase> {
        every { state } returns MutableStateFlow(ProfileState.Ready(1, testProfile("user-a")))
    }

    private fun testProfile(id: String) = UserProfile(
        id = id,
        name = "Test User",
        email = "test@example.test",
        role = UserRole.EMPLOYEE,
        status = UserStatus.ACTIVE,
        photoUrl = null,
        storeId = 1,
        storeName = "Test Store",
    )
}
