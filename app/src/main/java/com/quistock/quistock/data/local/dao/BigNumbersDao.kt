package com.quistock.quistock.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.quistock.quistock.data.local.entity.BigNumbersEntity

@Dao
interface BigNumbersDao {
    @Query("SELECT * FROM big_numbers ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatest(): BigNumbersEntity?

    @Insert
    suspend fun insert(bigNumbers: BigNumbersEntity)

    @Delete
    suspend fun delete(bigNumbers: BigNumbersEntity)

    @Transaction
    suspend fun replaceLatest(bigNumbers: BigNumbersEntity) {
        getLatest()?.let { delete(it) }
        insert(bigNumbers)
    }
}
