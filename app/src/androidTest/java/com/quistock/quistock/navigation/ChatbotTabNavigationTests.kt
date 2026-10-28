package com.quistock.quistock.navigation

import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.R
import com.quistock.quistock.presentation.activity.MainActivity
import com.quistock.quistock.presentation.chatbot.ChatMessage
import com.quistock.quistock.presentation.chatbot.ChatbotUiState
import com.quistock.quistock.presentation.chatbot.ChatbotViewModel
import com.quistock.quistock.presentation.common.SkeletonLoadingScreen
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class ChatbotTabNavigationTests {
    @get:Rule val authenticated = AuthenticatedSessionRule()
    private lateinit var previous: ChatbotViewModel
    private var skeleton = true

    @Before fun installConversationFixture() {
        val koin = GlobalContext.get()
        previous = koin.get()
        val model = mockk<ChatbotViewModel>(relaxed = true)
        val messages = List(MESSAGE_COUNT) { ChatMessage("Fixture message $it", it % 2 == 0) }
        every { model.messages } returns MutableLiveData(messages)
        every { model.uiState } returns MutableLiveData<ChatbotUiState>(ChatbotUiState.Idle)
        koin.declare(model, allowOverride = true)
        skeleton = SkeletonLoadingScreen.enabled
        SkeletonLoadingScreen.enabled = false
    }

    @After fun restoreConversationFixture() {
        GlobalContext.get().declare(previous, allowOverride = true)
        SkeletonLoadingScreen.enabled = skeleton
    }

    @Test fun conversationAndPositionSurviveSwitchAndRecreationWithoutDuplicateMessages() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitNavigationReady(scenario, R.id.homeFragment)
            onView(withId(R.id.navigation_chatbot)).perform(click())
            awaitNavigationReady(scenario, R.id.chatbotFragment)
            var position = 0
            scenario.onActivity { activity ->
                val scroll = activity.findViewById<ScrollView>(R.id.scrollMensagens)
                scroll.scrollTo(0, SAVED_POSITION)
                position = scroll.scrollY
            }
            onView(withId(R.id.navigation_products)).perform(click())
            awaitNavigationReady(scenario, R.id.productControlFragment)
            onView(withId(R.id.navigation_chatbot)).perform(click())
            awaitNavigationReady(scenario, R.id.chatbotFragment)
            assertConversation(scenario, position)
            scenario.recreate()
            awaitNavigationReady(scenario, R.id.chatbotFragment)
            assertConversation(scenario, position)
            onView(withId(R.id.navigation_chatbot)).perform(click())
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<ScrollView>(R.id.scrollMensagens).scrollY > position)
            }
        }
    }

    private fun assertConversation(scenario: ActivityScenario<MainActivity>, position: Int) {
        scenario.onActivity { activity ->
            assertEquals(MESSAGE_COUNT, activity.findViewById<LinearLayout>(R.id.containerMensagens).childCount)
            assertEquals(position, activity.findViewById<ScrollView>(R.id.scrollMensagens).scrollY)
        }
    }

    private companion object {
        const val MESSAGE_COUNT = 30
        const val SAVED_POSITION = 100
    }
}
