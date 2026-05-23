package com.example.engine

import kotlinx.serialization.Serializable

@Serializable
data class ParsedSms(
  val isBanking: Boolean,
  val bankName: String?,
  val amount: Double?,
  val currency: String?,
  val merchantName: String?,
  val transactionType: TransactionType,
  val transactionDate: String?,
  val balanceAfter: Double?,
  val cardLastDigits: String?,
  val confidence: Float,
  val color: SmsColor,
  val rawSms: String,
  val engineUsed: ParserEngine
)

enum class TransactionType {
  WITHDRAW,
  DEPOSIT,
  OTP,
  BALANCE_INQUIRY,
  TRANSFER_OUT,
  TRANSFER_IN,
  REFUND,
  OTHER
}

enum class SmsColor {
  RED,    // money leaving: WITHDRAW, TRANSFER_OUT
  GREEN,  // money entering: DEPOSIT, TRANSFER_IN, REFUND
  GRAY,   // no money movement: OTP, BALANCE_INQUIRY
  YELLOW  // low confidence: needs user review
}

enum class ParserEngine {
  GEMINI_NANO,      // AICore on-device
  GEMMA_MEDIAPIPE,  // MediaPipe local model
  TFLITE_NER,       // bundled TFLite models
  MANUAL,           // user entered manually
  GEMINI_3_5_FLASH  // Gemini 3.5 Flash Cloud engine
}

interface SmsParserEngine {
  suspend fun parse(
    rawSms: String,
    senderNumber: String?,
    receivedAt: Long
  ): ParsedSms
}
