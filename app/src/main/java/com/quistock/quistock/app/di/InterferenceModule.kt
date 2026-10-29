package com.quistock.quistock.app.di

import android.content.Context
import com.quistock.quistock.R
import com.quistock.quistock.data.remote.core.InterferenceApi
import com.quistock.quistock.data.remote.core.InterferenceWriteAttempt
import com.quistock.quistock.data.remote.core.RetrofitInterferenceRepository
import com.quistock.quistock.domain.port.InterferenceRepository
import com.quistock.quistock.domain.usecase.CreateInterferenceUseCase
import com.quistock.quistock.presentation.interferences.CreateInterferenceViewModel
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

val interferenceModule = module {
    single<InterferenceApi> {
        val baseUrl = get<Context>().getString(R.string.core_base_url)
        val client = createCoreHttpClient(baseUrl, get(), get(), get(), replayWrites = false).newBuilder()
            .callTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .tag(InterferenceWriteAttempt::class.java, InterferenceWriteAttempt()).build()
                chain.proceed(request)
            }
            .addNetworkInterceptor { chain ->
                requireNotNull(chain.request().tag(InterferenceWriteAttempt::class.java)).send(chain)
            }.build()
        Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build().create(InterferenceApi::class.java)
    }
    single<InterferenceRepository> { RetrofitInterferenceRepository(get()) }
    factory { CreateInterferenceUseCase(get(), get(), get(), get()) }
    viewModel { CreateInterferenceViewModel(get(), get(), get()) }
}

private const val WRITE_TIMEOUT_SECONDS = 30L
