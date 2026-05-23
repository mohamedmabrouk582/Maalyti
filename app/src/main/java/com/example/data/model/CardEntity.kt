package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardName: String,
    val limitAmount: Double,
    val outstandingAmount: Double,
    val dueDateMillis: Long,
    val cashbackPercentage: Double,
    val annualFeeFlag: Boolean = false,
    val feeAlertActive: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)
