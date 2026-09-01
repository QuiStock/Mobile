package com.quistock.quistock.app

import android.app.Application
import com.quistock.quistock.app.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class QuiStockApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@QuiStockApplication)
            modules(appModule)
        }
    }
}
