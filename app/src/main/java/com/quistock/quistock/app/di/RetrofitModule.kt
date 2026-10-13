package com.quistock.quistock.app.di

import android.content.Context
import com.quistock.quistock.R
import com.quistock.quistock.data.remote.auth.AuthCookieCustody
import com.quistock.quistock.data.remote.auth.AuthCookieStore
import com.quistock.quistock.data.remote.auth.CoreSessionInterceptor
import com.quistock.quistock.data.remote.auth.RetrofitAuthRepository
import com.quistock.quistock.data.remote.internal.chatbot.ChatbotApi
import com.quistock.quistock.data.remote.internal.chatbot.RetrofitChatbotRepository
import com.quistock.quistock.data.secrets.EncryptedAuthCookieStore
import com.quistock.quistock.domain.port.AuthRepository
import com.quistock.quistock.domain.port.ChatbotRepository
import com.quistock.quistock.domain.usecase.SessionUseCase
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@OptIn(ExperimentalSerializationApi::class)
val retrofitSdkModule = module {
    single { createJson() }

    single {
        createRetrofit(
            baseUrl = get<Context>().getString(R.string.backend_base_url),
            json = get(),
        )
    }

    single<ChatbotApi> { createChatbotApi(get()) }
}

val retrofitModule = module {
    single<AuthCookieStore> { EncryptedAuthCookieStore(get()) }
    single { AuthCookieCustody(get(), get<Context>().getString(R.string.auth_base_url).toHttpUrl()) }
    single<AuthRepository> {
        RetrofitAuthRepository(get<Context>().getString(R.string.auth_base_url).toHttpUrl(), get())
    }

    single(named("CoreHttpClient")) {
        createCoreHttpClient(get<Context>().getString(R.string.core_base_url), get(), get())
    }
    singleOf(::RetrofitChatbotRepository) {
        bind<ChatbotRepository>()
    }
}

@OptIn(ExperimentalSerializationApi::class)
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

internal fun createCoreHttpClient(baseUrl: String, session: SessionUseCase, custody: AuthCookieCustody): OkHttpClient =
    OkHttpClient.Builder()
        .addInterceptor(CoreSessionInterceptor(baseUrl.toHttpUrl(), session, custody))
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
