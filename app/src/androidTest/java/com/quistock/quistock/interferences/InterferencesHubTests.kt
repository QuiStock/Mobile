package com.quistock.quistock.interferences

import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.R
import com.quistock.quistock.awaitDestination
import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionListResult
import com.quistock.quistock.domain.model.ActionStatus
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.InterferenceAction
import com.quistock.quistock.domain.port.InterferenceRepository
import com.quistock.quistock.presentation.activity.MainActivity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class InterferencesHubTests {
    @get:Rule val authenticatedSession = AuthenticatedSessionRule()
    private lateinit var previousActions: InterferenceRepository
    private val actions = mockk<InterferenceRepository>()
    private val persisted = InterferenceAction(
        id = "action-47",
        flowId = "flow-47",
        productId = "product-47",
        storeId = 1,
        type = ActionType.ORDER,
        status = ActionStatus.GENERATED,
        values = ActionDraft(quantity = "4"),
        productName = "Refrigerante Guaraná",
    )

    @Before
    fun setup() {
        val koin = GlobalContext.get()
        previousActions = koin.get()
        coEvery { actions.list(any(), any(), any()) } returns ActionListResult.Loaded(emptyList())
        koin.declare(actions, allowOverride = true)
    }

    @After
    fun cleanup() {
        GlobalContext.get().declare(previousActions, allowOverride = true)
    }

    @Test
    fun hubShowsLoadingThenRealActionsAndCanReturnWithoutWritingAgain() {
        val pending = CompletableDeferred<ActionListResult>()
        coEvery { actions.list(any(), any(), any()) } coAnswers { pending.await() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openHub(scenario)
            onView(withText(R.string.creation_actions_loading)).check(matches(isDisplayed()))

            pending.complete(ActionListResult.Loaded(listOf(persisted)))
            onView(withText(containsString("Refrigerante Guaraná"))).check(matches(isDisplayed()))
            onView(withText(containsString("product-47"))).check(doesNotExist())
            onView(withText(containsString("Refrigerante Guaraná"))).perform(click())
            awaitDestination(scenario, R.id.orderFragment)
            onView(withId(R.id.creationSummary)).check(matches(withText(containsString("Quantidade: 4"))))
            onView(withId(R.id.creationSummary)).check(matches(withText(containsString("Refrigerante Guaraná"))))
            onView(withId(R.id.creationForward)).check(matches(isDisplayed()))
            onView(withId(R.id.creationForward)).perform(click())
            onView(withText(containsString("Produto: Refrigerante Guaraná")))
                .inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.creation_cancel)).inRoot(isDialog()).perform(click())

            onView(withId(R.id.creationReturn)).perform(click())
            awaitDestination(scenario, R.id.interferencesFragment)
            onView(withText(containsString("Refrigerante Guaraná"))).check(matches(isDisplayed()))
            coVerify(exactly = 0) { actions.generate(any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { actions.forward(any(), any()) }
        }
    }

    @Test
    fun hubShowsEmptyStateWithoutExampleActions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openHub(scenario)
            onView(withText(R.string.creation_no_actions)).check(matches(isDisplayed()))
            onView(withText(containsString("Verão"))).check(doesNotExist())
        }
    }

    @Test
    fun hubShowsReadErrorAndRetryLoadsActions() {
        coEvery { actions.list(any(), any(), any()) } returns ActionListResult.Failed andThen
            ActionListResult.Loaded(listOf(persisted))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openHub(scenario)
            onView(withText(R.string.creation_list_error)).check(matches(isDisplayed()))
            onView(withId(R.id.interferenceListRetry)).perform(click())
            onView(withText(containsString("Refrigerante Guaraná"))).check(matches(isDisplayed()))
            onView(withText(containsString("product-47"))).check(doesNotExist())
            coVerify(exactly = 0) { actions.generate(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    fun hubUsesProductNameReturnedByActions() {
        coEvery { actions.list(any(), any(), any()) } returns ActionListResult.Loaded(listOf(persisted))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openHub(scenario)
            onView(withText(containsString("Refrigerante Guaraná"))).check(matches(isDisplayed()))
            onView(withText(containsString("product-47"))).check(doesNotExist())
        }
    }

    @Test
    fun hubUsesFriendlyFallbackWhenProductNameIsMissing() {
        coEvery { actions.list(any(), any(), any()) } returns
            ActionListResult.Loaded(listOf(persisted.copy(productName = null)))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openHub(scenario)
            onView(withText(containsString("Produto não identificado"))).check(matches(isDisplayed()))
            onView(withText(containsString("product-47"))).check(doesNotExist())
        }
    }

    private fun openHub(scenario: ActivityScenario<MainActivity>) {
        awaitDestination(scenario, R.id.homeFragment)
        scenario.onActivity {
            it.binding.navHostFragment.findNavController().navigate(R.id.interferencesFragment)
        }
        awaitDestination(scenario, R.id.interferencesFragment)
    }
}
