package com.quistock.quistock.login

import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.usecase.LoginUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import com.quistock.quistock.presentation.activity.MainActivity
import com.quistock.quistock.presentation.login.LoginViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.launch
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class SessionNavigationTests {
    @Test fun protocolFailureHidesProtectedContentAcrossRecreationUntilManualRecovery() {
        coEvery { auth.refresh(any()) } returns com.quistock.quistock.domain.model.AuthRefreshError.ProtocolError
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.session_recovery_message)).check(matches(withText(R.string.session_renewal_failed)))
            onView(withId(R.id.session_recovery)).check(matches(isDisplayed()))
            onView(withId(R.id.nav_host_fragment)).check(matches(not(isDisplayed())))
            scenario.recreate()
            onView(withId(R.id.session_recovery)).check(matches(isDisplayed()))
            onView(withId(R.id.nav_host_fragment)).check(matches(not(isDisplayed())))
            coVerify(exactly = 1) { auth.refresh(any()) }
            coEvery { auth.refresh(any()) } returns AuthResult.Success
            onView(withId(R.id.session_recovery_retry)).perform(click())
            onView(withId(R.id.tvNumeroVencimento)).check(matches(isDisplayed()))
            coVerify(exactly = 2) { auth.refresh(any()) }
        }
    }

    private val auth = mockk<AuthRepository>(relaxed = true)
    private lateinit var session: SessionUseCase

    @Before fun setup() {
        coEvery { auth.restore(any()) } returns com.quistock.quistock.domain.port.StoredSession.AVAILABLE
        io.mockk.every { auth.hasAccess(any()) } returns true
        coEvery { auth.refresh(any()) } returns AuthResult.Success
        session = SessionUseCase(auth)
        GlobalContext.get().declare(session, allowOverride = true)
        GlobalContext.get().declare(
            LoginViewModel(LoginUseCase(session, GlobalContext.get().get())),
            allowOverride = true,
        )
    }

    @Test fun recoveryOpensHomeAndExpirationRemovesProtectedBackStack() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(
                withId(R.id.tvNumeroVencimento),
            ).check(matches(withText(org.hamcrest.Matchers.any(String::class.java))))
            scenario.onActivity { activity ->
                val nav = (
                    activity.supportFragmentManager.findFragmentById(
                        R.id.nav_host_fragment,
                    ) as NavHostFragment
                    ).navController
                assertEquals(R.id.homeFragment, nav.currentDestination?.id)
                activity.lifecycleScope.launch { session.expire(session.snapshot()) }
            }
            onView(withId(R.id.txt_erro_login)).check(matches(withText(R.string.session_expired)))
            onView(withId(R.id.email_login)).perform(typeText("synthetic@example.com"), closeSoftKeyboard())
            scenario.recreate()
            onView(withId(R.id.email_login)).check(matches(withText("synthetic@example.com")))
            onView(withId(R.id.txt_erro_login)).check(matches(withText(R.string.session_expired)))
            scenario.onActivity { activity ->
                val nav = (
                    activity.supportFragmentManager.findFragmentById(
                        R.id.nav_host_fragment,
                    ) as NavHostFragment
                    ).navController
                assertEquals(R.id.loginFragment, nav.currentDestination?.id)
                assertFalse(nav.popBackStack())
            }
            coVerify(exactly = 1) { auth.clear(1) }
        }
    }
}
