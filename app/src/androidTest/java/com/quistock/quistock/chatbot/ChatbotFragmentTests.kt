package com.quistock.quistock.chatbot

import androidx.lifecycle.MutableLiveData
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE
import androidx.test.espresso.matcher.ViewMatchers.Visibility.VISIBLE
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.R
import com.quistock.quistock.app.navigation.NavGraph
import com.quistock.quistock.awaitDestination
import com.quistock.quistock.domain.model.ChatbotConversation
import com.quistock.quistock.presentation.activity.MainActivity
import com.quistock.quistock.presentation.chatbot.ChatMessage
import com.quistock.quistock.presentation.chatbot.ChatbotUiState
import com.quistock.quistock.presentation.chatbot.ChatbotViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.Matchers.allOf
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
        every { viewModel.endedConversations } returns MutableLiveData(emptyList<ChatbotConversation>())
        every { viewModel.sendMessage(any()) } answers {
            messages.value = messages.value.orEmpty() + ChatMessage(firstArg(), true)
            true
        }
        every { viewModel.newConversation() } answers { messages.value = emptyList() }
        GlobalContext.get().declare<ChatbotViewModel>(instance = viewModel, allowOverride = true)
    }

    @After fun restoreViewModel() {
        GlobalContext.get().declare(previousViewModel, allowOverride = true)
    }

    @Test
    fun menuShouldOpenCloseAndStartANewConversation() = runInstrumented { _ ->
        onView(withId(R.id.btnMenuChatbot)).perform(click())
        onView(withId(R.id.chatMenuOverlay)).check(matches(withEffectiveVisibility(VISIBLE)))
        onView(withId(R.id.btnNewChat)).perform(click())
        onView(withId(R.id.chatMenuOverlay)).check(matches(withEffectiveVisibility(GONE)))
        verify(exactly = 1) { viewModel.newConversation() }
    }

    @Test
    fun sendingMessageShouldRenderUserMessage() = runInstrumented { _ ->
        val message = "Quais produtos estão acabando?"
        onView(withId(R.id.etPerguntaChatbot)).perform(replaceText(message), closeSoftKeyboard())
        onView(withId(R.id.btnEnviarPergunta)).perform(click())
        onView(allOf(withText(message), withParent(withId(R.id.containerMensagens)))).check(matches(isDisplayed()))
        verify(exactly = 1) { viewModel.sendMessage(message) }
    }

    @Test
    fun blockedSendKeepsDraftInInput() = runInstrumented { _ ->
        val message = "Ainda carregando perfil"
        every { viewModel.sendMessage(any()) } returns false

        onView(withId(R.id.etPerguntaChatbot)).perform(replaceText(message), closeSoftKeyboard())
        onView(withId(R.id.btnEnviarPergunta)).perform(click())

        onView(withId(R.id.etPerguntaChatbot)).check(matches(withText(message)))
        verify(exactly = 1) { viewModel.sendMessage(message) }
    }

    @Test
    fun emptyMessageShowsValidationError() = runInstrumented { _ ->
        onView(withId(R.id.btnEnviarPergunta)).perform(click())
        onView(withId(R.id.statusPopupMessage)).check(matches(withText(R.string.chatbot_empty_message)))
    }

    @Test
    fun uncertainSendWarnsBeforeExplicitRetry() = runInstrumented { _ ->
        emitUiState(ChatbotUiState.Error.SEND_UNCERTAIN)
        onView(withText(R.string.chatbot_retry_may_duplicate)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText(R.string.chatbot_retry)).inRoot(isDialog()).perform(click())
        verify(exactly = 1) { viewModel.retryLastMessage() }
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
        InstrumentationRegistry.getInstrumentation().runOnMainSync { uiState.value = state }
    }
}
