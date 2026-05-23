package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountName: String,
    val bankName: String,
    val balance: Double,
    val currency: String,
    val role: String, // "primary", "monitor", "transfer"
    val timestamp: Long = System.currentTimeMillis()
)
