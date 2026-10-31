package com.quistock.quistock.presentation.chatbot

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.quistock.quistock.MainDispatcherRule
import com.quistock.quistock.domain.model.ChatbotConversationHistory
import com.quistock.quistock.domain.model.ChatbotHistoryMessage
import com.quistock.quistock.domain.model.SessionBinding
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.port.ChatbotConversationStore
import com.quistock.quistock.domain.usecase.SendMessageToChatbotUseCase
import com.quistock.quistock.domain.usecase.SessionLocalAccess
import com.quistock.quistock.domain.usecase.SessionUseCase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatbotConversationTests {
    @get:Rule val instant = InstantTaskExecutorRule()

    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `startup restores confirmed messages for the persisted active conversation`() = runTest {
        val useCase = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns "conversation-a"
        coEvery { useCase.history("conversation-a", SessionSnapshot(1, true)) } returns history("conversation-a")
        coEvery { useCase.listEnded(SessionSnapshot(1, true)) } returns emptyList()
        val viewModel = ChatbotViewModel(useCase, store, session())

        viewModel.load()
        advanceUntilIdle()

        assertEquals(
            listOf(ChatMessage("question", true), ChatMessage("answer", false)),
            viewModel.messages.value,
        )
    }

    @Test
    fun `opening a history conversation loads its real messages without changing active id`() = runTest {
        val useCase = mockk<SendMessageToChatbotUseCase>()
        val store = mockk<ChatbotConversationStore>()
        every { store.current("user-a") } returns "active-id"
        coEvery { useCase.history("past-id", SessionSnapshot(1, true)) } returns history("past-id")
        val viewModel = ChatbotViewModel(useCase, store, session())

        viewModel.openHistory("past-id")
        advanceUntilIdle()

        viewModel.messages.value shouldBe listOf(ChatMessage("question", true), ChatMessage("answer", false))
    }

    private fun history(id: String) = ChatbotConversationHistory(
        id,
        "Title",
        "ended",
        listOf(
            ChatbotHistoryMessage("m1", "user", "question", "2026-10-31T10:00:00Z"),
            ChatbotHistoryMessage("m2", "assistant", "answer", "2026-10-31T10:01:00Z"),
        ),
    )

    private fun session(): SessionUseCase {
        val local = mockk<SessionLocalAccess>()
        every { local.binding() } returns SessionBinding("sid", "user-a", 1, Long.MAX_VALUE)
        val session = mockk<SessionUseCase>()
        every { session.state } returns MutableStateFlow(SessionState.Active)
        every { session.snapshot() } returns SessionSnapshot(1, true)
        every { session.local } returns local
        return session
    }
}
