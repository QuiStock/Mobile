package com.quistock.quistock.control

import android.graphics.Rect
import android.view.View
import android.widget.ScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.R
import com.quistock.quistock.awaitDestination
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.StockFlow
import com.quistock.quistock.domain.port.PriorityAlertsRepository
import com.quistock.quistock.navigation.awaitNavigationReady
import com.quistock.quistock.presentation.activity.MainActivity
import com.quistock.quistock.presentation.chatbot.ChatbotViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class ProductControlFragmentTests {
    @get:Rule val authenticatedSession = AuthenticatedSessionRule()

    private lateinit var previousProducts: PriorityAlertsRepository
    private lateinit var previousChatbot: ChatbotViewModel
    private lateinit var products: PriorityAlertsRepository
    private lateinit var chatbot: ChatbotViewModel
    private val fixtures = listOf(
        product("rice", "Arroz", StockFlow.LOW),
        product("milk", "Leite integral", StockFlow.MEDIUM),
        product("coffee", "Café", StockFlow.LOW),
    )

    @Before
    fun installFixtures() {
        val koin = GlobalContext.get()
        previousProducts = koin.get()
        previousChatbot = koin.get()
        products = mockk()
        coEvery { products.read(any(), any()) } returns AlertProductsResult.Success(fixtures, 0)
        chatbot = mockk(relaxed = true)
        koin.declare(products, allowOverride = true)
        koin.declare(chatbot, allowOverride = true)
    }

    @After
    fun restoreDependencies() {
        GlobalContext.get().declare(previousProducts, allowOverride = true)
        GlobalContext.get().declare(previousChatbot, allowOverride = true)
    }

    @Test
    fun listSearchAndFlowFilterOpenTheSelectedProductDetail() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openProductControl(scenario)

            onView(withText("Arroz")).perform(scrollToProduct()).check(matches(isDisplayed()))
            onView(withText("Leite integral")).perform(scrollToProduct()).check(matches(isDisplayed()))
            onView(withText("Café")).perform(scrollToProduct()).check(matches(isDisplayed()))
            coVerify(atLeast = 1) { products.read(1, any()) }

            onView(withId(R.id.etBuscaProduto)).perform(scrollTo(), replaceText("leite"), closeSoftKeyboard())
            awaitProducts(scenario, 1)
            onView(withText("Leite integral")).perform(scrollToProduct()).check(matches(isDisplayed()))
            onView(withText("Arroz")).check(doesNotExist())

            onView(withId(R.id.etBuscaProduto)).perform(scrollTo(), replaceText(""), closeSoftKeyboard())
            awaitProducts(scenario, fixtures.size)
            onView(withId(R.id.dropdownFiltroFluxo)).perform(scrollTo(), click())
            onView(withText(R.string.product_control_low)).perform(click())
            awaitProducts(scenario, 2)
            onView(withText("Arroz")).perform(scrollToProduct()).check(matches(isDisplayed()))
            onView(withText("Café")).perform(scrollToProduct()).check(matches(isDisplayed()))
            onView(withText("Leite integral")).check(doesNotExist())

            onView(withText("Arroz")).perform(scrollToProduct(), click())
            awaitDestination(scenario, R.id.productDetailFragment)
            onView(withId(R.id.detailProductTitle)).check(matches(withText("Arroz")))
        }
    }

    @Test
    fun fefoOpensChatbotWithTheSelectedProductContext() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openProductControl(scenario)
            onView(withText("Leite integral")).perform(scrollToProduct(), click())
            awaitDestination(scenario, R.id.productDetailFragment)
            onView(withId(R.id.detailProductTitle)).check(matches(withText("Leite integral")))

            onView(withId(R.id.btnRecomendacaoFefo)).perform(scrollTo(), click())
            awaitDestination(scenario, R.id.chatbotFragment)

            verify(exactly = 1) { chatbot.offerFefo(fixtures[1]) }
        }
    }

    private fun openProductControl(scenario: ActivityScenario<MainActivity>) {
        awaitDestination(scenario, R.id.homeFragment)
        onView(withId(R.id.navigation_products)).perform(click())
        awaitDestination(scenario, R.id.productControlFragment)
        scenario.onActivity { activity ->
            // These journeys assert content and navigation, independently of row move animations.
            activity.findViewById<RecyclerView>(R.id.rvProducts).itemAnimator = null
        }
        awaitProducts(scenario, fixtures.size)
    }

    private fun awaitProducts(scenario: ActivityScenario<MainActivity>, count: Int) {
        awaitNavigationReady(scenario, R.id.productControlFragment) { activity ->
            val products = activity.findViewById<RecyclerView>(R.id.rvProducts)
            products.adapter?.itemCount == count && !products.hasPendingAdapterUpdates() &&
                !products.isComputingLayout && !products.isLayoutRequested && products.itemAnimator?.isRunning != true
        }
    }

    private fun scrollToProduct(): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)
        override fun getDescription(): String = "scroll the outer product page to the selected row"
        override fun perform(uiController: UiController, view: View) {
            var ancestor = view.parent
            while (ancestor != null && ancestor !is ScrollView) ancestor = ancestor.parent
            val scroll = requireNotNull(ancestor as? ScrollView)
            val rect = Rect(0, 0, view.width, view.height)
            scroll.offsetDescendantRectToMyCoords(view, rect)
            // RecyclerView intercepts requestRectangleOnScreen; target the owning page directly.
            scroll.scrollTo(0, rect.top)
            uiController.loopMainThreadUntilIdle()
        }
    }

    private fun product(id: String, name: String, flow: StockFlow) = AlertProduct(
        id = id,
        name = name,
        category = "Mercearia",
        stock = BigDecimal("12.5"),
        expirationDays = 9,
        flow = flow,
    )
}
