package com.quistock.quistock.app.di

import com.quistock.quistock.presentation.activity.SessionViewModel
import com.quistock.quistock.presentation.chatbot.ChatbotViewModel
import com.quistock.quistock.presentation.control.ProductControlViewModel
import com.quistock.quistock.presentation.employee.TeamViewModel
import com.quistock.quistock.presentation.home.HomeViewModel
import com.quistock.quistock.presentation.login.LoginViewModel
import com.quistock.quistock.presentation.profile.AlertPreferencesViewModel
import com.quistock.quistock.presentation.profile.StoreLocationViewModel
import com.quistock.quistock.presentation.profile.UserLocationViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    viewModelOf(::HomeViewModel)
    viewModelOf(::StoreLocationViewModel)
    viewModelOf(::UserLocationViewModel)
    viewModel { ChatbotViewModel(get(), get(), get(), get()) }
    viewModelOf(::LoginViewModel)
    viewModel { SessionViewModel(get(), get(), get(), get(), getOrNull()) }
    viewModelOf(::TeamViewModel)
    viewModelOf(::AlertPreferencesViewModel)
}
