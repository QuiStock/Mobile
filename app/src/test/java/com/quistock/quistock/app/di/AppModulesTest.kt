package com.quistock.quistock.app.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.quistock.quistock.data.local.dao.BigNumbersDao
import com.quistock.quistock.data.remote.internal.chatbot.ChatbotApi
import com.quistock.quistock.domain.model.AccessToken
import com.quistock.quistock.domain.model.AuthResult
import com.quistock.quistock.domain.model.RefreshToken
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.CachedBigNumbersRepository
import com.quistock.quistock.domain.port.SecretStorage
import com.quistock.quistock.domain.usecase.LoginUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify

class AppModulesTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `internal dependency graph should be valid`() {
        appInternalModule.verify(
            extraTypes = listOf(
                Context::class,
                FirebaseAuth::class,
                FirebaseCrashlytics::class,
                ChatbotApi::class,
                BigNumbersDao::class,
            ),
        )
    }

    @Test
    fun `token login resolves and persists a simulated session without Firebase SDK`() = runBlocking {
        val storage = mockk<SecretStorage>(relaxed = true)
        val application = koinApplication {
            modules(
                domainModule,
                module {
                    single<SecretStorage> { storage }
                    single<CachedBigNumbersRepository> { mockk(relaxed = true) }
                },
            )
        }
        try {
            val login = application.koin.get<LoginUseCase>()
            val result = login("synthetic@example.com", "synthetic-password")
            assertTrue(result is AuthResult.Success)
            val tokens = (result as AuthResult.Success).tokens
            assertEquals(tokens.accessToken, application.koin.get<SessionUseCase>().snapshot().accessToken)
            coVerify(exactly = 1) { storage.save(tokens.refreshToken, RefreshToken::class) }
            coVerify(exactly = 1) { storage.save(tokens.accessToken, AccessToken::class) }
            assertTrue(application.koin.get<AuthRepository>().refresh(tokens.refreshToken) is AuthResult.Success)
        } finally {
            application.close()
        }
    }
}
