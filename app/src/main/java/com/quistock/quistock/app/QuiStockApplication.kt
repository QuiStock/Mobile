package com.quistock.quistock.app

import android.app.Application
import com.quistock.quistock.app.di.appModule
import com.quistock.quistock.presentation.common.ThemeManager
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class QuiStockApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        ThemeManager.init(this)

        startKoin {
            androidLogger()
            androidContext(this@QuiStockApplication)
            modules(appModule)
        }
    }
}
