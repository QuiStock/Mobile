package com.quistock.quistock.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.quistock.quistock.data.local.converter.InstantConverters
import com.quistock.quistock.data.local.dao.BigNumbersDao
import com.quistock.quistock.data.local.entity.BigNumbersEntity

@Database(entities = [BigNumbersEntity::class], version = 2)
@TypeConverters(InstantConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bigNumbersDao(): BigNumbersDao
}
