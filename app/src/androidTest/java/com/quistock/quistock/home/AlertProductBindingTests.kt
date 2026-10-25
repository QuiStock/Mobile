package com.quistock.quistock.home

import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.AuthenticatedSessionRule
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.AlertRisk
import com.quistock.quistock.domain.model.PriorityAlert
import com.quistock.quistock.domain.model.StockFlow
import com.quistock.quistock.presentation.activity.MainActivity
import com.quistock.quistock.presentation.common.AlertProductNavigation
import com.quistock.quistock.presentation.home.PriorityAlertsAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class AlertProductBindingTests {
    @get:Rule val authenticatedSession = AuthenticatedSessionRule()

    @Test
    fun mediumHidesPromotionAndRecycledLowCardRestoresItWithoutLosingFefo() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val product = product()
                var actionCalled = false
                val adapter = PriorityAlertsAdapter({ actionCalled = true }, {})
                val holder = adapter.onCreateViewHolder(FrameLayout(activity), 0)
                holder.bind(PriorityAlert(product.copy(flow = StockFlow.MEDIUM), AlertRisk.EXPIRY))
                val primary = holder.itemView.findViewById<View>(R.id.btnPromocao)
                assertEquals(View.GONE, primary.visibility)
                primary.performClick()
                assertFalse(actionCalled)
                assertEquals(View.VISIBLE, holder.itemView.findViewById<View>(R.id.btnFefo).visibility)

                holder.bind(PriorityAlert(product, AlertRisk.OVERSTOCK))
                assertEquals(View.VISIBLE, primary.visibility)
            }
        }
    }

    @Test
    fun navigationKeepsDecimalAndSelectedAnalysisIdentityExactly() {
        val original = product()
        val arguments = AlertProductNavigation.arguments(original)
        val restored = AlertProductNavigation.product(arguments)

        assertNotNull(restored)
        assertEquals(original, restored)
        arguments.putString("priority_alert_stock", "2.3751")
        assertNull(AlertProductNavigation.product(arguments))
    }

    private fun product() = AlertProduct(
        "product:store", "Fixture product", "Category", BigDecimal("2.375"), 2, StockFlow.LOW,
        "analysis", Instant.parse("2026-10-20T10:00:00Z"),
    )
}
