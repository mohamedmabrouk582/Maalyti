package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankName: String,
    val amount: Double,
    val currency: String,
    val merchant: String,
    val balance: Double?,
    val cardLast4: String?,
    val dateString: String,
    val type: String, // "withdraw", "deposit", "otp", "other"
    val rawBody: String,
    val sender: String = "90000000",
    val confidence: Double,
    val wasFallbackUsed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
