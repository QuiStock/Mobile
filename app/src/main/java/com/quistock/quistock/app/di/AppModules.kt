package com.quistock.quistock.app.di

import org.koin.dsl.module

val externalSdksModule = module {
    includes(firebaseSdkModule)
}

val appInternalModule = module {
    includes(
        firebaseModule,
        domainModule,
        presentationModule,
        observabilityModule,
        preferencesModule,
    )
}

val appModule = module {
    includes(
        externalSdksModule,
        appInternalModule,
    )
}
