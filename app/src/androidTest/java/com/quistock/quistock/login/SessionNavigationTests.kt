package com.quistock.quistock.login

import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quistock.quistock.R
import com.quistock.quistock.domain.model.AccessToken
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.AuthTokens
import com.quistock.quistock.domain.model.RefreshToken
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.CachedBigNumbersRepository
import com.quistock.quistock.domain.port.SecretStorage
import com.quistock.quistock.domain.usecase.LoginUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import com.quistock.quistock.presentation.activity.MainActivity
import com.quistock.quistock.presentation.login.LoginViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class SessionNavigationTests {
    private val auth = mockk<AuthRepository>()
    private val storage = mockk<SecretStorage>(relaxed = true)
    private val cache = mockk<CachedBigNumbersRepository>(relaxed = true)
    private lateinit var session: SessionUseCase
    private val tokens = AuthTokens(AccessToken("synthetic"), RefreshToken("opaque"))

    @Before fun setup() {
        coEvery { storage.read(RefreshToken::class) } returns tokens.refreshToken
        coEvery { auth.refresh(tokens.refreshToken) } returns AuthResult.Success(tokens)
        session = SessionUseCase(auth, storage, cache)
        GlobalContext.get().declare(session, allowOverride = true)
        GlobalContext.get().declare<CachedBigNumbersRepository>(cache, allowOverride = true)
        GlobalContext.get().declare(LoginViewModel(LoginUseCase(auth, session)), allowOverride = true)
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
            coVerify(exactly = 1) { cache.clear() }
            coVerify(exactly = 1) { storage.delete(RefreshToken::class) }
            coVerify(exactly = 1) { storage.delete(AccessToken::class) }
        }
    }
}
