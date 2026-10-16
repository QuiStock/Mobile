package com.quistock.quistock.presentation.chatbot

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.quistock.quistock.MainDispatcherRule
import com.quistock.quistock.domain.model.ChatbotRequest
import com.quistock.quistock.domain.model.ChatbotResponse
import com.quistock.quistock.domain.port.UserPreferences
import com.quistock.quistock.domain.usecase.SendMessageToChatbotUseCase
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ChatbotViewModelTests {
    @Test
    fun `logout clears conversation and cancels pending legacy work`() = runTest {
        val state = kotlinx.coroutines.flow.MutableStateFlow<com.quistock.quistock.domain.model.SessionState>(
            com.quistock.quistock.domain.model.SessionState.Active,
        )
        val session = mockk<com.quistock.quistock.domain.usecase.SessionUseCase>()
        every { session.state } returns state
        every { session.snapshot() } returns com.quistock.quistock.domain.model.SessionSnapshot(1, true)
        every { userPreferences.getUserId() } returns "synthetic-user"
        coEvery { sendMessageToChatbot(any()) } coAnswers { awaitCancellation() }
        val guarded = ChatbotViewModel(sendMessageToChatbot, userPreferences, session)
        guarded.sendMessage("synthetic-message")
        runCurrent()
        guarded.uiState.value shouldBe ChatbotUiState.Loading
        state.value = com.quistock.quistock.domain.model.SessionState.LoggingOut
        runCurrent()
        guarded.answer.value shouldBe null
        guarded.uiState.value shouldBe ChatbotUiState.Idle
        guarded.sendMessage("another-message")
        guarded.uiState.value shouldBe ChatbotUiState.Error.NOT_LOGGED_IN
        coVerify(exactly = 1) { sendMessageToChatbot(any()) }
    }

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sendMessageToChatbot = mockk<SendMessageToChatbotUseCase>()
    private val userPreferences = mockk<UserPreferences>()
    private val viewModel = ChatbotViewModel(sendMessageToChatbot, userPreferences)

    @Test
    fun `initial state should be idle with no answer`() = runTest {
        viewModel.uiState.value shouldBe ChatbotUiState.Idle
        viewModel.answer.value shouldBe null
    }

    @Test
    fun `sending a message without a stored user should report not logged in`() = runTest {
        every { userPreferences.getUserId() } returns null

        viewModel.sendMessage("Quais produtos estão acabando?")

        viewModel.uiState.value shouldBe ChatbotUiState.Error.NOT_LOGGED_IN
        viewModel.answer.value shouldBe null
        coVerify(exactly = 0) { sendMessageToChatbot(any()) }
    }

    @Test
    fun `sending a message with a stored user should expose the chatbot response`() = runTest {
        val userId = "user-123"
        val message = "Quais produtos estão acabando?"
        val response = ChatbotResponse(
            answer = "Há dois produtos com estoque baixo.",
            responsibleAgent = "inventory",
            referencedData = emptyList(),
        )
        every { userPreferences.getUserId() } returns userId
        coEvery { sendMessageToChatbot(ChatbotRequest(userId, message)) } returns response

        viewModel.sendMessage(message)
        advanceUntilIdle()

        viewModel.answer.value shouldBe response
        viewModel.uiState.value shouldBe ChatbotUiState.Answered
        coVerify(exactly = 1) { sendMessageToChatbot(ChatbotRequest(userId, message)) }
    }

    @Test
    fun `sending a message when the request fails should report a network error`() = runTest {
        val userId = "user-123"
        val message = "Quais produtos estão acabando?"
        every { userPreferences.getUserId() } returns userId
        coEvery { sendMessageToChatbot(ChatbotRequest(userId, message)) } throws IOException("offline")

        viewModel.sendMessage(message)
        advanceUntilIdle()

        viewModel.uiState.value shouldBe ChatbotUiState.Error.NETWORK
        coVerify(exactly = 1) { sendMessageToChatbot(ChatbotRequest(userId, message)) }
    }

    @Test
    fun `sending a message when the request throws an unexpected exception should report request failure`() = runTest {
        val userId = "user-123"
        val message = "Quais produtos estão acabando?"
        every { userPreferences.getUserId() } returns userId
        coEvery { sendMessageToChatbot(any()) } throws IllegalStateException("unexpected")

        viewModel.sendMessage(message)
        advanceUntilIdle()

        viewModel.uiState.value shouldBe ChatbotUiState.Error.REQUEST_FAILED
        coVerify(exactly = 1) { sendMessageToChatbot(ChatbotRequest(userId, message)) }
    }

    @Test
    fun `sending while loading should ignore the new message and clearing cancels the pending request`() = runTest {
        every { userPreferences.getUserId() } returns "user-123"
        coEvery { sendMessageToChatbot(any()) } coAnswers { awaitCancellation() }

        viewModel.sendMessage("first message")
        runCurrent()

        viewModel.uiState.value shouldBe ChatbotUiState.Loading
        viewModel.sendMessage("second message")
        coVerify(exactly = 1) { sendMessageToChatbot(any()) }

        viewModel.clearConversation()
        advanceUntilIdle()

        viewModel.answer.value shouldBe null
        viewModel.uiState.value shouldBe ChatbotUiState.Idle
    }
}
