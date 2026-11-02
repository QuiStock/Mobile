package com.quistock.quistock.order

import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasErrorText
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
        onView(withId(R.id.creationSubmit)).perform(scrollTo(), click())
        val quantityError = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.creation_quantity_error)
        onView(withId(R.id.creationQuantity)).check(matches(hasErrorText(quantityError)))
        fill(R.id.creationQuantity, "20")
        onView(withId(R.id.btnPickOrderDate)).perform(scrollTo(), click())
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        onView(withId(R.id.creationSubmit)).perform(scrollTo(), click())
        onView(withText(R.string.creation_cancel)).inRoot(isDialog()).perform(click())
        scenario.recreate()
        awaitDestination(scenario, R.id.orderFragment)
        onView(withId(R.id.creationQuantity)).check(matches(withText("20")))
        coVerify(exactly = 0) { actions.generate(any(), any(), any(), any(), any()) }
    }

    @Test
    fun creationCardFormatterRendersSpannablesCorrectly() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val productCard = com.quistock.quistock.presentation.interferences.CreationCardFormatter.formatProductCard(context, product)
        org.junit.Assert.assertTrue(productCard.contains("Fixture product"))
        org.junit.Assert.assertTrue(productCard.contains("Category"))

        val orderDraft = com.quistock.quistock.domain.model.ActionDraft(quantity = "10", orderDate = "2026-11-10")
        val orderReview = com.quistock.quistock.presentation.interferences.CreationCardFormatter.formatReviewSpannable(
            context,
            com.quistock.quistock.domain.model.ActionType.ORDER,
            product,
            orderDraft,
        )
        org.junit.Assert.assertTrue(orderReview.contains("Quantidade:"))

        val promoDraft = com.quistock.quistock.domain.model.ActionDraft(discount = "15", start = "2026-11-10", end = "2026-11-20")
        val promoReview = com.quistock.quistock.presentation.interferences.CreationCardFormatter.formatReviewSpannable(
            context,
            com.quistock.quistock.domain.model.ActionType.PROMOTION,
            product,
            promoDraft,
        )
        org.junit.Assert.assertTrue(promoReview.contains("Desconto:"))

        val blankPromoDraft = com.quistock.quistock.domain.model.ActionDraft()
        val blankReview = com.quistock.quistock.presentation.interferences.CreationCardFormatter.formatReviewSpannable(
            context,
            com.quistock.quistock.domain.model.ActionType.PROMOTION,
            product,
            blankPromoDraft,
        )
        org.junit.Assert.assertTrue(blankReview.contains("Desconto:"))

        val sentOrder = com.quistock.quistock.presentation.interferences.CreationCardFormatter.formatSentDetails(
            context,
            "Produto: Arroz\nQuantidade: 20\nData: 2026-11-10",
            "",
        )
        org.junit.Assert.assertTrue(sentOrder.contains("Quantidade"))

        val sentPromo = com.quistock.quistock.presentation.interferences.CreationCardFormatter.formatSentDetails(
            context,
            "Produto: Arroz\nDesconto: 10%\nInício: 2026-11-10\nFim: 2026-11-20\nStatus: Pendente",
            "",
        )
        org.junit.Assert.assertTrue(sentPromo.contains("Início"))

        val sentBlank = com.quistock.quistock.presentation.interferences.CreationCardFormatter.formatSentDetails(
            context,
            "",
            "Linha sem dois pontos",
        )
        org.junit.Assert.assertTrue(sentBlank.contains("Linha"))

        val formattedDate = com.quistock.quistock.presentation.interferences.CreationCardFormatter.formatAnalysisDate("2026-10-20T10:00:00Z")
        org.junit.Assert.assertTrue(formattedDate.contains("2026"))
        org.junit.Assert.assertEquals("", com.quistock.quistock.presentation.interferences.CreationCardFormatter.formatAnalysisDate(null))
        org.junit.Assert.assertEquals("20-10-2026", com.quistock.quistock.presentation.interferences.CreationCardFormatter.formatAnalysisDate("20-10-2026"))
    }

    @Test
    fun promotionHasExplicitDatesAndCancelDoesNotWrite() = runForm(R.id.promotionFragment) { _ ->
        fill(R.id.creationDiscount, "15,25")
        fill(R.id.creationStart, today())
        fill(R.id.creationEnd, today())
        onView(withId(R.id.creationSubmit)).perform(scrollTo(), click())
        onView(withText(R.string.creation_cancel)).inRoot(isDialog()).perform(click())
        onView(withId(R.id.creationDiscount)).check(matches(withText("15,25")))
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

    private fun today(): String = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString()
}
