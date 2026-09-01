package com.quistock.quistock.app.di

import com.google.firebase.auth.FirebaseAuth
import com.quistock.quistock.data.remote.firebase.auth.FirebaseAuthenticationPort
import com.quistock.quistock.domain.port.AuthenticationPort
import org.koin.core.module.dsl.bind
import org.koin.dsl.module
import org.koin.core.module.dsl.singleOf

val firebaseSdkModule = module {
    single { FirebaseAuth.getInstance() }
}

val firebaseModule = module {
    singleOf(::FirebaseAuthenticationPort) {
        bind<AuthenticationPort>()
    }
}
