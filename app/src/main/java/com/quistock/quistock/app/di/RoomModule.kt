package com.quistock.quistock.app.di

import androidx.room.Room
import com.quistock.quistock.data.local.AppDatabase
import com.quistock.quistock.data.local.repository.RoomBigNumbersRepository
import com.quistock.quistock.domain.port.CachedBigNumbersRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val roomSdkModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "quistock.db",
        ).build()
    }

    single { get<AppDatabase>().bigNumbersDao() }
}

val roomModule = module {
    singleOf(::RoomBigNumbersRepository) {
        bind<CachedBigNumbersRepository>()
    }
}
