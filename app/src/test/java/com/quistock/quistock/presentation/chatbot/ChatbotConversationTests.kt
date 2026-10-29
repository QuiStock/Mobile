package com.quistock.quistock.presentation.chatbot

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.quistock.quistock.MainDispatcherRule
import com.quistock.quistock.domain.model.ChatbotResponse
import com.quistock.quistock.domain.model.SessionSnapshot
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.domain.port.UserPreferences
import com.quistock.quistock.domain.usecase.SendMessageToChatbotUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatbotConversationTests {
    @get:Rule val instant = InstantTaskExecutorRule()

    @get:Rule val main = MainDispatcherRule()

    @Test fun conversationRetainsQuestionsAndAnswersUntilExplicitClear() = runTest {
        val send = mockk<SendMessageToChatbotUseCase>()
        val preferences = mockk<UserPreferences>()
        every { preferences.getUserId() } returns "fixture-user"
        coEvery { send(any()) } returns ChatbotResponse("fixture answer", "fixture", emptyList())
        val model = ChatbotViewModel(send, preferences)
        model.rememberQuestion("fixture question")
        model.sendMessage("fixture question")
        runCurrent()

        val expected = listOf(ChatMessage("fixture question", true), ChatMessage("fixture answer", false))
        assertEquals(expected, model.messages.value)
        model.clearConversation()
        assertTrue(model.messages.value.orEmpty().isEmpty())
    }

    @Test fun expirationAndNewSessionDiscardConversation() = runTest {
        val session = mockk<SessionUseCase>()
        val state = MutableStateFlow<SessionState>(SessionState.Active)
        var generation = 1L
        every { session.state } returns state
        every { session.snapshot() } answers { SessionSnapshot(generation, true) }
        val model = ChatbotViewModel(mockk(), mockk(), session)
        runCurrent()
        model.rememberQuestion("old session")
        state.value = SessionState.Expired
        runCurrent()
        assertTrue(model.messages.value.orEmpty().isEmpty())

        generation = 2
        state.value = SessionState.Active
        runCurrent()
        assertTrue(model.messages.value.orEmpty().isEmpty())
        model.rememberQuestion("second session")
        generation = 3
        model.synchronizeSession()
        assertTrue(model.messages.value.orEmpty().isEmpty())
    }
}
