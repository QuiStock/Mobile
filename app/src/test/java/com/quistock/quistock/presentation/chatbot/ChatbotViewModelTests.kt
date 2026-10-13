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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatbotViewModelTests {
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
}
