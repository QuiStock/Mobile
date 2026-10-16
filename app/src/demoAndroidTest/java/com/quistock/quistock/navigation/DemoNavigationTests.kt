package com.quistock.quistock.navigation

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.R
import com.quistock.quistock.app.navigation.demo.DemoActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoNavigationTests {
    @Test
    fun entryAndOperationalJourneysReachEveryOperationalPageWithoutInput() {
        ActivityScenario.launch(DemoActivity::class.java).use { scenario ->
            awaitDestination(scenario, R.id.onboardingFragment)
            tap(scenario, R.id.onboarding_continue_button, R.id.loginFragment)
            tap(scenario, R.id.demo_register, R.id.cadastroPessoalFragment)
            tap(scenario, R.id.bt_prox_cadastro, R.id.loginFragment)
            tap(scenario, R.id.bt_entrar_login, R.id.loadingFragment)
            awaitDestination(scenario, R.id.homeFragment)
            tap(scenario, R.id.btnMovimentacoes, R.id.interferencesFragment)
            tap(scenario, R.id.cardPedidoEstoque, R.id.orderFragment)
            tap(scenario, R.id.btnSubmitOrder, R.id.orderFragment)
            tap(scenario, R.id.confirmationYes, R.id.orderSentFragment, dialog = true)
            tap(scenario, R.id.btnVoltarInterferencias, R.id.interferencesFragment)
            tap(scenario, R.id.cardPromocao, R.id.promotionFragment)
            tap(scenario, R.id.btnSubmitPromotion, R.id.promotionFragment)
            tap(scenario, R.id.confirmationYes, R.id.promoSentFragment, dialog = true)
            tap(scenario, R.id.btnVoltarInterferencias, R.id.interferencesFragment)
            tap(scenario, R.id.btnProdutos, R.id.productControlFragment)
            tap(scenario, R.id.cardProdutoFluxoAlto, R.id.productDetailFragment)
            tap(scenario, R.id.btnVoltarControle, R.id.productControlFragment)
            tap(scenario, R.id.btnFolha, R.id.chatbotFragment)
            tap(scenario, R.id.btnNotificacoesChatbot, R.id.notificationsFragment)
            tap(scenario, R.id.notifications_back_control, R.id.chatbotFragment)
            tap(scenario, R.id.btnHomeChatbot, R.id.homeFragment)
            employeeAndProfileJourneys(scenario)
            scenario.onActivity {
                assertEquals(Application::class.java, it.application.javaClass)
                assertEquals(PackageManager.PERMISSION_DENIED, it.checkSelfPermission(Manifest.permission.INTERNET))
            }
        }
    }

    private fun employeeAndProfileJourneys(scenario: ActivityScenario<DemoActivity>) {
        tap(scenario, R.id.btnFuncionarios, R.id.employeeFragment)
        tap(scenario, R.id.btnCadastrarFuncionario, R.id.employeeDetailFragment)
        tap(scenario, R.id.btnCadastrar, R.id.employeeFragment)
        tap(scenario, R.id.cardBrunaAlves, R.id.employeeProfileFragment)
        tap(scenario, R.id.btnEditarFuncionario, R.id.editEmployeeFragment)
        tap(scenario, R.id.btnSalvarAlteracoes, R.id.employeeProfileFragment)
        tap(scenario, R.id.btnVoltarPerfilFuncionario, R.id.employeeFragment)
        tap(scenario, R.id.btnHomeFuncionarios, R.id.homeFragment)
        tap(scenario, R.id.btnPerfil, R.id.profileFragment)
        tap(scenario, R.id.profile_edit_button, R.id.editProfileFragment)
        tap(scenario, R.id.profile_save_changes_button, R.id.profileFragment)
        tap(scenario, R.id.profile_help_row, R.id.faqFragment)
        tap(scenario, R.id.faq_contact_link, R.id.contactFragment)
        tap(scenario, R.id.contact_back_control, R.id.faqFragment)
        tap(scenario, R.id.faq_back_control, R.id.profileFragment)
        tap(scenario, R.id.profile_back_control, R.id.homeFragment)
    }

    @Test
    fun managerPromotionCompletesWithoutKeepingTheReviewInTheBackStack() {
        ActivityScenario.launch(DemoActivity::class.java).use { scenario ->
            tap(scenario, R.id.demo_role, R.id.homeFragment)
            tap(scenario, R.id.btnMovimentacoes, R.id.managerInterferencesFragment)
            tap(scenario, R.id.btnAnalisarPromocao, R.id.managerPromotionDetailFragment)
            tap(scenario, R.id.btnAprovarPromocao, R.id.managerPromotionDetailFragment)
            tap(scenario, R.id.confirmationYes, R.id.managerPromoApprovedFragment, dialog = true)
            scenario.onActivity {
                it.onBackPressedDispatcher.onBackPressed()
                assertEquals(R.id.managerInterferencesFragment, it.nav.currentDestination?.id)
            }
        }
    }

    private fun tap(
        scenario: ActivityScenario<DemoActivity>,
        control: Int,
        destination: Int,
        dialog: Boolean = false,
    ) {
        if (dialog) {
            onView(withId(control)).perform(click())
        } else {
            scenario.onActivity { assertTrue(it.findViewById<View>(control).performClick()) }
        }
        scenario.onActivity { assertEquals(destination, it.nav.currentDestination?.id) }
    }

    private fun awaitDestination(scenario: ActivityScenario<DemoActivity>, destination: Int) {
        val deadline = SystemClock.uptimeMillis() + 5000
        var arrived = false
        while (!arrived && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { arrived = it.nav.currentDestination?.id == destination }
            if (!arrived) SystemClock.sleep(50)
        }
        assertTrue("Destination was not reached: $destination", arrived)
    }

    @Test
    fun homeTabsAndRoleSurviveRecreation() {
        ActivityScenario.launch(DemoActivity::class.java).use { scenario ->
            onView(withId(R.id.demo_role)).perform(click())
            scenario.onActivity { assertEquals(R.id.homeFragment, it.nav.currentDestination?.id) }
            onView(withId(R.id.btnMovimentacoes)).perform(click())
            scenario.onActivity { assertEquals(R.id.managerInterferencesFragment, it.nav.currentDestination?.id) }
            scenario.recreate()
            scenario.onActivity { assertEquals(true, it.manager) }
            onView(withId(R.id.btnAnalisarEstoque)).perform(click())
            scenario.onActivity { assertEquals(R.id.managerOrderDetailFragment, it.nav.currentDestination?.id) }
            onView(withId(R.id.btnAprovarPedido)).perform(click())
            onView(withId(R.id.confirmationNo)).perform(click())
            scenario.onActivity { assertEquals(R.id.managerOrderDetailFragment, it.nav.currentDestination?.id) }
            onView(withId(R.id.btnAprovarPedido)).perform(click())
            onView(withId(R.id.confirmationYes)).perform(click())
            scenario.onActivity { assertEquals(R.id.managerOrderApprovedFragment, it.nav.currentDestination?.id) }
            onView(withId(R.id.btnVoltarInterferencias)).perform(click())
            scenario.onActivity {
                assertEquals(R.id.managerInterferencesFragment, it.nav.currentDestination?.id)
                it.nav.popBackStack()
                assertEquals(R.id.homeFragment, it.nav.currentDestination?.id)
            }
        }
    }

    @Test
    fun repeatedHomeTabDoesNotDuplicateHome() {
        ActivityScenario.launch(DemoActivity::class.java).use { scenario ->
            onView(withId(R.id.demo_role)).perform(click())
            repeat(2) { onView(withId(R.id.btnHome)).perform(click()) }
            scenario.onActivity { assertEquals(null, it.nav.previousBackStackEntry) }
        }
    }
}
