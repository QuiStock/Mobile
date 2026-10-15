package com.quistock.quistock.app.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.quistock.quistock.data.remote.internal.chatbot.ChatbotApi
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.CachedBigNumbersRepository
import com.quistock.quistock.domain.port.Logger
import com.quistock.quistock.domain.usecase.LoginUseCase
import com.quistock.quistock.domain.usecase.SessionUseCase
import io.mockk.mockk
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
            ),
        )
    }

    @Test
    fun `session and cookie repository resolve without dependency cycles`() {
        val context = mockk<Context>()
        io.mockk.every { context.getString(com.quistock.quistock.R.string.auth_base_url) } returns
            "https://auth.example.test/"
        io.mockk.every { context.getString(com.quistock.quistock.R.string.core_base_url) } returns
            "https://core.example.test/"
        val application = koinApplication {
            modules(
                domainModule,
                retrofitModule,
                module {
                    single<Context> { context }
                    single<com.quistock.quistock.data.remote.auth.AuthCookieStore> {
                        com.quistock.quistock.data.remote.auth.MemoryCookieStore()
                    }
                    single<CachedBigNumbersRepository> { mockk(relaxed = true) }
                    single<Logger> { mockk(relaxed = true) }
                },
            )
        }
        try {
            application.koin.get<LoginUseCase>()
            application.koin.get<SessionUseCase>()
            application.koin.get<okhttp3.OkHttpClient>(org.koin.core.qualifier.named("CoreHttpClient"))
            org.junit.Assert.assertTrue(
                application.koin.get<AuthRepository>() is
                    com.quistock.quistock.data.remote.auth.RetrofitAuthRepository,
            )
        } finally {
            application.close()
        }
    }
}
