package com.quistock.quistock.order

import android.view.View
import android.widget.DatePicker
import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasErrorText
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.R
import com.quistock.quistock.awaitDestination
import com.quistock.quistock.domain.model.ActionListResult
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.AlertProductsResult
import com.quistock.quistock.domain.model.CreationRisk
import com.quistock.quistock.domain.model.StockFlow
import com.quistock.quistock.domain.port.InterferenceRepository
import com.quistock.quistock.domain.port.PriorityAlertsRepository
import com.quistock.quistock.presentation.activity.MainActivity
import com.quistock.quistock.presentation.interferences.CreationDate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class OrderAndPromotionFragmentTests {
    @get:Rule val authenticatedSession = AuthenticatedSessionRule()
    private lateinit var previousProducts: PriorityAlertsRepository
    private lateinit var previousActions: InterferenceRepository
    private val actions = mockk<InterferenceRepository>()
    private val product = AlertProduct(
        "product",
        "Fixture product",
        "Category",
        BigDecimal("2.375"),
        null,
        StockFlow.MEDIUM,
        "flow",
        risks = setOf(CreationRisk.STOCKOUT, CreationRisk.EXPIRATION),
    )

    @Before
    fun setup() {
        val koin = GlobalContext.get()
        previousProducts = koin.get()
        previousActions = koin.get()
        val products = mockk<PriorityAlertsRepository>()
        coEvery { products.read(any(), any()) } returns AlertProductsResult.Success(listOf(product), 0)
        coEvery { actions.list(any(), any(), any()) } returns ActionListResult.Loaded(emptyList())
        koin.declare(products, allowOverride = true)
        koin.declare(actions, allowOverride = true)
    }

    @After
    fun cleanup() {
        GlobalContext.get().declare(previousProducts, allowOverride = true)
        GlobalContext.get().declare(previousActions, allowOverride = true)
    }

    @Test
    fun orderValidationAndCancellationPreserveDraftWithoutWriting() = runForm(R.id.orderFragment) { scenario ->
        val today = today()
        onView(withId(R.id.creationSubmit)).perform(scrollTo(), click())
        val quantityError = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.creation_quantity_error)
        onView(withId(R.id.creationQuantity)).check(matches(hasErrorText(quantityError)))
        fill(R.id.creationQuantity, "20")
        selectDate(R.id.creationOrderDate, today)
        onView(withId(R.id.creationSubmit)).perform(scrollTo(), click())
        onView(withText(R.string.creation_cancel)).inRoot(isDialog()).perform(click())
        scenario.recreate()
        awaitDestination(scenario, R.id.orderFragment)
        onView(withId(R.id.creationQuantity)).check(matches(withText("20")))
        onView(withId(R.id.creationOrderDate)).check(matches(withText(CreationDate.display(today.toString()))))
        coVerify(exactly = 0) { actions.generate(any(), any(), any(), any(), any()) }
    }

    @Test
    fun promotionHasExplicitDatesAndCancelDoesNotWrite() = runForm(R.id.promotionFragment) { _ ->
        val start = today()
        val end = start.plusDays(1)
        fill(R.id.creationDiscount, "15,25")
        selectDate(R.id.creationStart, start)
        selectDate(R.id.creationEnd, end)
        onView(withId(R.id.creationSubmit)).perform(scrollTo(), click())
        onView(withId(android.R.id.message)).inRoot(isDialog()).check(
            matches(
                withText(
                    allOf(
                        containsString("15,25%"),
                        containsString("Início: ${CreationDate.display(start.toString())}"),
                        containsString("Fim: ${CreationDate.display(end.toString())}"),
                    ),
                ),
            ),
        )
        onView(withText(R.string.creation_cancel)).inRoot(isDialog()).perform(click())
        onView(withId(R.id.creationDiscount)).check(matches(withText("15,25")))
        onView(withId(R.id.creationStart)).check(matches(withText(CreationDate.display(start.toString()))))
        onView(withId(R.id.creationEnd)).check(matches(withText(CreationDate.display(end.toString()))))
        coVerify(exactly = 0) { actions.generate(any(), any(), any(), any(), any()) }
    }

    private fun runForm(destination: Int, test: (ActivityScenario<MainActivity>) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitDestination(scenario, R.id.homeFragment)
            scenario.onActivity {
                it.binding.navHostFragment.findNavController().navigate(destination)
            }
            awaitDestination(scenario, destination)
            onView(withText(containsString("Fixture product"))).perform(scrollTo(), click())
            test(scenario)
        }
    }

    private fun fill(id: Int, value: String) {
        onView(withId(id)).perform(scrollTo(), replaceText(value), closeSoftKeyboard())
    }

    private fun selectDate(id: Int, date: LocalDate) {
        onView(withId(id)).perform(scrollTo(), click())
        onView(isAssignableFrom(DatePicker::class.java)).perform(setDate(date))
        onView(withId(android.R.id.button1)).perform(click())
    }

    private fun setDate(date: LocalDate): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(DatePicker::class.java)

        override fun getDescription(): String = "select $date in the date picker"

        override fun perform(uiController: UiController, view: View) {
            (view as DatePicker).updateDate(date.year, date.monthValue - 1, date.dayOfMonth)
        }
    }

    private fun today(): LocalDate = LocalDate.now(ZoneId.of("America/Sao_Paulo"))
}
