package com.quistock.quistock.app.di

import android.content.Context
import com.quistock.quistock.BuildConfig
import com.quistock.quistock.R
import com.quistock.quistock.data.preferences.AndroidChatbotConversationStore
import com.quistock.quistock.data.preferences.SqliteHomeSummaryCache
import com.quistock.quistock.data.remote.auth.AuthApi
import com.quistock.quistock.data.remote.auth.AuthCookieCustody
import com.quistock.quistock.data.remote.auth.AuthCookieStore
import com.quistock.quistock.data.remote.auth.AuthHttpAttempt
import com.quistock.quistock.data.remote.auth.CoreSessionInterceptor
import com.quistock.quistock.data.remote.auth.RetrofitAuthRepository
import com.quistock.quistock.data.remote.core.DashboardApi
import com.quistock.quistock.data.remote.core.HttpProfilePhotoRepository
import com.quistock.quistock.data.remote.core.PriorityAlertsApi
import com.quistock.quistock.data.remote.core.ProfileApi
import com.quistock.quistock.data.remote.core.ProfileEditApi
import com.quistock.quistock.data.remote.core.ProfileWriteAttempt
import com.quistock.quistock.data.remote.core.RetrofitHomeSummaryRepository
import com.quistock.quistock.data.remote.core.RetrofitPriorityAlertsRepository
import com.quistock.quistock.data.remote.core.RetrofitProfileEditRepository
import com.quistock.quistock.data.remote.core.RetrofitProfileRepository
import com.quistock.quistock.data.remote.core.RetrofitStoreLocationRepository
import com.quistock.quistock.data.remote.core.RetrofitTeamRepository
import com.quistock.quistock.data.remote.core.StoreLocationApi
import com.quistock.quistock.data.remote.core.TeamApi
import com.quistock.quistock.data.remote.internal.chatbot.ChatbotApi
import com.quistock.quistock.data.remote.internal.chatbot.RetrofitChatbotRepository
import com.quistock.quistock.data.secrets.EncryptedAuthCookieStore
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.ChatbotConversationStore
import com.quistock.quistock.domain.port.ChatbotRepository
import com.quistock.quistock.domain.port.HomeSummaryCache
import com.quistock.quistock.domain.port.HomeSummaryRepository
import com.quistock.quistock.domain.port.Logger
import com.quistock.quistock.domain.port.PriorityAlertsRepository
import com.quistock.quistock.domain.port.ProfileEditRepository
import com.quistock.quistock.domain.port.ProfilePhotoRepository
import com.quistock.quistock.domain.port.ProfileRepository
import com.quistock.quistock.domain.port.StoreLocationRepository
import com.quistock.quistock.domain.port.TeamRepository
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

val retrofitSdkModule = module {
    single { createJson() }
}

val retrofitModule = module {
    single<DashboardApi> {
        Retrofit.Builder().baseUrl(get<Context>().getString(R.string.core_base_url).trim())
            .client(get<OkHttpClient>(named("CoreHttpClient"))).build().create(DashboardApi::class.java)
    }
    single<HomeSummaryRepository> { RetrofitHomeSummaryRepository(get()) }
    single<HomeSummaryCache> { SqliteHomeSummaryCache(get()) }

    single<TeamApi> {
        Retrofit.Builder()
            .baseUrl(get<Context>().getString(R.string.core_base_url))
            .client(get<OkHttpClient>(named("CoreHttpClient")))
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build().create(TeamApi::class.java)
    }
    single<TeamRepository> { RetrofitTeamRepository(get(), BuildConfig.EMPLOYEE_INITIAL_PASSWORD) }
    single<ProfileEditApi> {
        Retrofit.Builder()
            .baseUrl(get<Context>().getString(R.string.core_base_url))
            .client(get<OkHttpClient>(named("CoreHttpClient")))
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build().create(ProfileEditApi::class.java)
    }
    single<ProfileEditRepository> { RetrofitProfileEditRepository(get()) }
    single<PriorityAlertsApi> {
        Retrofit.Builder()
            .baseUrl(get<Context>().getString(R.string.core_base_url))
            .client(get<OkHttpClient>(named("CoreHttpClient")))
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build().create(PriorityAlertsApi::class.java)
    }
    single<PriorityAlertsRepository> { RetrofitPriorityAlertsRepository(get(), get()) }
    single<StoreLocationApi> {
        Retrofit.Builder()
            .baseUrl(get<Context>().getString(R.string.core_base_url))
            .client(get<OkHttpClient>(named("CoreHttpClient")))
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build().create(StoreLocationApi::class.java)
    }
    single<StoreLocationRepository> { RetrofitStoreLocationRepository(get(), get()) }
    single<ProfilePhotoRepository> { HttpProfilePhotoRepository() }
    single<ProfileApi> {
        Retrofit.Builder()
            .baseUrl(get<Context>().getString(R.string.core_base_url))
            .client(get<OkHttpClient>(named("CoreHttpClient")))
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build().create(ProfileApi::class.java)
    }
    single<ProfileRepository> { RetrofitProfileRepository(get(), get()) }
    single<ChatbotApi> {
        Retrofit.Builder().baseUrl(get<Context>().getString(R.string.core_base_url).trim())
            .client(get<OkHttpClient>(named("ChatbotCoreHttpClient")))
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build().create(ChatbotApi::class.java)
    }
    single<ChatbotRepository> { RetrofitChatbotRepository(get(), get()) }
    single<ChatbotConversationStore> { AndroidChatbotConversationStore(get()) }
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
    single(named("ChatbotCoreHttpClient")) {
        createCoreHttpClient(
            get<Context>().getString(R.string.core_base_url),
            get(),
            get(),
            get(),
            replayWrites = false,
        )
    }
}

internal fun createJson(): Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

internal fun createCoreHttpClient(
    baseUrl: String,
    session: SessionUseCase,
    custody: AuthCookieCustody,
    logger: Logger? = null,
    replayWrites: Boolean = true,
): OkHttpClient = OkHttpClient.Builder()
    .cookieJar(CookieJar.NO_COOKIES)
    .addInterceptor { chain ->
        val request = chain.request()
        val tagged = if (request.method == "PATCH") {
            request.newBuilder().tag(ProfileWriteAttempt::class.java, ProfileWriteAttempt()).build()
        } else {
            request
        }
        chain.proceed(tagged)
    }
    .addInterceptor(CoreSessionInterceptor(baseUrl.toHttpUrl(), session, custody, logger, replayWrites))
    .addNetworkInterceptor { chain ->
        chain.request().tag(ProfileWriteAttempt::class.java)?.send(chain) ?: chain.proceed(chain.request())
    }
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
