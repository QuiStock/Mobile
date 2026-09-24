package com.quistock.quistock.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

@Entity(tableName = "big_numbers")
data class BigNumbersEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "near_expiration_product_count") val nearExpirationProductCount: Int,
    @ColumnInfo(name = "critical_analyzed_flow_count") val criticalAnalyzedFlowCount: Int,
    @ColumnInfo(name = "active_action_count") val activeActionCount: Int,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)
