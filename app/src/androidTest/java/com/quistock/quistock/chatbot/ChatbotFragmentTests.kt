package com.quistock.quistock.chatbot

import androidx.lifecycle.MutableLiveData
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.R
import com.quistock.quistock.app.navigation.NavGraph
import com.quistock.quistock.awaitDestination
import com.quistock.quistock.domain.model.ChatbotResponse
import com.quistock.quistock.presentation.activity.MainActivity
import com.quistock.quistock.presentation.chatbot.ChatMessage
import com.quistock.quistock.presentation.chatbot.ChatbotUiState
import com.quistock.quistock.presentation.chatbot.ChatbotViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class ChatbotFragmentTests {
    @get:Rule val authenticatedSession = AuthenticatedSessionRule()

    private lateinit var viewModel: ChatbotViewModel
    private lateinit var uiState: MutableLiveData<ChatbotUiState>
    private lateinit var messages: MutableLiveData<List<ChatMessage>>
    private lateinit var previousViewModel: ChatbotViewModel

    @Before
    fun setup() {
        uiState = MutableLiveData(ChatbotUiState.Idle)
        previousViewModel = GlobalContext.get().get()
        messages = MutableLiveData(emptyList())
        viewModel = mockk(relaxed = true)
        every { viewModel.uiState } returns uiState
        every { viewModel.messages } returns messages
        every { viewModel.rememberQuestion(any()) } answers {
            messages.value = messages.value.orEmpty() + ChatMessage(firstArg(), true)
        }
        every { viewModel.clearConversation() } answers { messages.value = emptyList() }
        GlobalContext.get().declare<ChatbotViewModel>(instance = viewModel, allowOverride = true)
    }

    @After fun restoreViewModel() {
        GlobalContext.get().declare(previousViewModel, allowOverride = true)
    }

    @Test
    fun menu_shouldOpenCloseAndStartANewConversation() = runInstrumented { _ ->
        onView(withId(R.id.etPerguntaChatbot)).perform(replaceText("Pergunta antiga"), closeSoftKeyboard())
        onView(withId(R.id.btnMenuChatbot)).perform(click())
        onView(withId(R.id.chatMenuOverlay)).check(matches(withEffectiveVisibility(VISIBLE)))

        onView(withId(R.id.btnNewChat)).perform(click())

        onView(withId(R.id.chatMenuOverlay)).check(matches(withEffectiveVisibility(GONE)))
        onView(withId(R.id.tvTituloChatbot)).check(matches(withText(R.string.chatbot_welcome_title)))
        onView(withId(R.id.etPerguntaChatbot)).check(matches(withText("")))
        verify(exactly = 1) { viewModel.clearConversation() }
    }

    @Test
    fun sendingMessage_shouldRenderUserAndAssistantMessages() = runInstrumented { _ ->
        val message = "Quais produtos estão acabando?"
        val response = ChatbotResponse("Há dois produtos com estoque baixo.", "inventory", emptyList())

        onView(withId(R.id.etPerguntaChatbot)).perform(replaceText(message), closeSoftKeyboard())
        onView(withId(R.id.btnEnviarPergunta)).perform(click())
        onView(withText(message)).check(matches(isDisplayed()))
        verify(exactly = 1) { viewModel.sendMessage(message) }

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            messages.value = messages.value.orEmpty() + ChatMessage(response.answer, false)
            uiState.value = ChatbotUiState.Answered
        }

        onView(withText(response.answer)).check(matches(isDisplayed()))
    }

    @Test
    fun emptyMessage_shouldShowValidationError() = runInstrumented { _ ->
        onView(withId(R.id.btnEnviarPergunta)).perform(click())
        onView(withId(R.id.statusPopupMessage)).check(matches(withText(R.string.chatbot_empty_message)))
    }

    @Test
    fun notLoggedInError_shouldShowLoginMessage() = runInstrumented { _ ->
        emitUiState(ChatbotUiState.Error.NOT_LOGGED_IN)
        onView(withId(R.id.statusPopupMessage)).check(matches(withText(R.string.chatbot_login_required)))
    }

    @Test
    fun requestFailedError_shouldShowRetryMessage() = runInstrumented { _ ->
        emitUiState(ChatbotUiState.Error.REQUEST_FAILED)
        onView(withId(R.id.statusPopupMessage)).check(matches(withText(R.string.chatbot_request_failed)))
    }

    @Test
    fun networkError_shouldOfferRetryForTheLastMessage() = runInstrumented { _ ->
        val message = "Consultar estoque"
        onView(withId(R.id.etPerguntaChatbot)).perform(replaceText(message), closeSoftKeyboard())
        onView(withId(R.id.btnEnviarPergunta)).perform(click())

        emitUiState(ChatbotUiState.Error.NETWORK)
        onView(withId(R.id.retryNoConnection)).inRoot(isDialog()).check(matches(isDisplayed())).perform(click())

        verify(exactly = 2) { viewModel.sendMessage(message) }

        emitUiState(ChatbotUiState.Error.NETWORK)
        onView(withId(R.id.closeNoConnection)).inRoot(isDialog()).perform(click())
        verify(exactly = 2) { viewModel.sendMessage(message) }
    }

    @Test
    fun startingANewConversation_shouldRemoveExistingMessages() = runInstrumented { _ ->
        val message = "Mensagem anterior"
        onView(withId(R.id.etPerguntaChatbot)).perform(replaceText(message), closeSoftKeyboard())
        onView(withId(R.id.btnEnviarPergunta)).perform(click())
        onView(withText(message)).check(matches(isDisplayed()))

        onView(withId(R.id.btnMenuChatbot)).perform(click())
        onView(withId(R.id.btnNewChat)).perform(click())

        onView(withText(message)).check(doesNotExist())
    }

    private fun runInstrumented(test: (NavController) -> Unit) {
        lateinit var navController: NavController

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitDestination(scenario, NavGraph.Destinations.HOME)
            scenario.onActivity { activity ->
                navController = activity.binding.navHostFragment.findNavController()
                navController.navigate(NavGraph.Destinations.CHATBOT)
            }
            awaitDestination(scenario, NavGraph.Destinations.CHATBOT)
            test(navController)
        }
    }

    private fun emitUiState(state: ChatbotUiState) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            uiState.value = state
        }
    }
}
