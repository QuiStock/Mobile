package com.quistock.quistock.app.di

import android.content.Context
import com.quistock.quistock.R
import com.quistock.quistock.data.remote.auth.AuthApi
import com.quistock.quistock.data.remote.auth.AuthCookieCustody
import com.quistock.quistock.data.remote.auth.AuthCookieStore
import com.quistock.quistock.data.remote.auth.AuthHttpAttempt
import com.quistock.quistock.data.remote.auth.CoreSessionInterceptor
import com.quistock.quistock.data.remote.auth.RetrofitAuthRepository
import com.quistock.quistock.data.remote.core.HttpProfilePhotoRepository
import com.quistock.quistock.data.remote.core.ProfileApi
import com.quistock.quistock.data.remote.core.PriorityAlertsApi
import com.quistock.quistock.data.remote.core.RetrofitPriorityAlertsRepository
import com.quistock.quistock.data.remote.core.RetrofitProfileRepository
import com.quistock.quistock.data.remote.internal.chatbot.ChatbotApi
import com.quistock.quistock.data.remote.internal.chatbot.RetrofitChatbotRepository
import com.quistock.quistock.data.secrets.EncryptedAuthCookieStore
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.ChatbotRepository
import com.quistock.quistock.domain.port.Logger
import com.quistock.quistock.domain.port.ProfilePhotoRepository
import com.quistock.quistock.domain.port.ProfileRepository
import com.quistock.quistock.domain.port.PriorityAlertsRepository
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.QualifierValue
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

private object LegacyChatbotApi : Qualifier {
    override val value: QualifierValue get() = "api-legacy-chatbot"
}

val retrofitSdkModule = module {
    single { createJson() }

    // Keep the legacy chatbot transport independent from the authenticated Core client.
    single(qualifier = LegacyChatbotApi) {
        createRetrofit(
            baseUrl = get<Context>().getString(R.string.auth_base_url),
            json = get(),
        )
    }

    single<ChatbotApi> { createChatbotApi(get(qualifier = LegacyChatbotApi)) }
}

val retrofitModule = module {
    single<PriorityAlertsApi> {
        Retrofit.Builder()
            .baseUrl(get<Context>().getString(R.string.core_base_url))
            .client(get<OkHttpClient>(named("CoreHttpClient")))
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build().create(PriorityAlertsApi::class.java)
    }
    single<PriorityAlertsRepository> { RetrofitPriorityAlertsRepository(get(), get()) }
    single<ProfilePhotoRepository> { HttpProfilePhotoRepository() }
    single<ProfileApi> {
        Retrofit.Builder()
            .baseUrl(get<Context>().getString(R.string.core_base_url))
            .client(get<OkHttpClient>(named("CoreHttpClient")))
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build().create(ProfileApi::class.java)
    }
    single<ProfileRepository> { RetrofitProfileRepository(get(), get()) }
    single<AuthCookieStore> { EncryptedAuthCookieStore(get()) }
    single { AuthCookieCustody(get(), get<Context>().getString(R.string.auth_base_url).trim().toHttpUrl()) }
    single<AuthRepository> {
        val baseUrl = get<Context>().getString(R.string.auth_base_url).trim().toHttpUrl()
        val client = OkHttpClient()
        RetrofitAuthRepository(
            baseUrl,
            get(),
            createApi = { jar ->
                createAuthApi(baseUrl, jar, client)
            },
            logger = get(),
        )
    }

    single(named("CoreHttpClient")) {
        createCoreHttpClient(get<Context>().getString(R.string.core_base_url), get(), get(), get())
    }
    singleOf(::RetrofitChatbotRepository) {
        bind<ChatbotRepository>()
    }
}

internal fun createJson(): Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

internal fun createRetrofit(baseUrl: String, json: Json): Retrofit = Retrofit.Builder()
    .baseUrl(baseUrl)
    .addConverterFactory(
        json.asConverterFactory("application/json".toMediaType()),
    )
    .build()

internal fun createChatbotApi(retrofit: Retrofit): ChatbotApi = retrofit.create(ChatbotApi::class.java)

internal fun createCoreHttpClient(
    baseUrl: String,
    session: SessionUseCase,
    custody: AuthCookieCustody,
    logger: Logger? = null,
): OkHttpClient = OkHttpClient.Builder()
    .cookieJar(CookieJar.NO_COOKIES)
    .addInterceptor(CoreSessionInterceptor(baseUrl.toHttpUrl(), session, custody, logger))
    .retryOnConnectionFailure(false)
    .followRedirects(false)
    .followSslRedirects(false)
    .build()

internal fun createAuthApi(baseUrl: HttpUrl, jar: CookieJar, client: OkHttpClient = OkHttpClient()): AuthApi =
    Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            client.newBuilder().cookieJar(jar).retryOnConnectionFailure(false)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder().tag(AuthHttpAttempt::class.java, AuthHttpAttempt()).build(),
                    )
                }
                .addNetworkInterceptor { chain ->
                    val attempt = requireNotNull(chain.request().tag(AuthHttpAttempt::class.java))
                    attempt.claim()
                    chain.proceed(chain.request()).also { attempt.status = it.code }
                }
                .followRedirects(false).followSslRedirects(false).build(),
        )
        .addConverterFactory(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }.asConverterFactory("application/json".toMediaType()),
        )
        .build().create(AuthApi::class.java)
