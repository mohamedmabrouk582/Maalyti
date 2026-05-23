package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "parsed_sms")
data class ParsedSmsEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val isBanking: Boolean,
  val bankName: String?,
  val amount: Double?,
  val currency: String?,
  val merchantName: String?,
  val transactionType: String,
  val transactionDate: String?,
  val balanceAfter: Double?,
  val cardLastDigits: String?,
  val confidence: Float,
  val color: String,
  val rawSms: String,
  val senderNumber: String?,
  val engineUsed: String,
  val isUserCorrected: Boolean = false,
  val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_corrections")
data class UserCorrectionEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val rawSms: String,
  val senderNumber: String?,
  val fieldName: String,
  val wrongValue: String?,
  val correctValue: String,
  val engineUsed: String,
  val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val merchantPattern: String,  // lowercase normalized
  val category: String,
  val confidence: Float,
  val confirmedCount: Int
)

@Entity(tableName = "sender_overrides")
data class SenderOverrideEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val senderPattern: String,
  val fieldName: String,
  val extractionHint: String
)
