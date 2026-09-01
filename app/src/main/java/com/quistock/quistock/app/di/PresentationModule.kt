package com.quistock.quistock.app.di

import com.quistock.quistock.presentation.login.LoginViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    viewModelOf(::LoginViewModel)
}