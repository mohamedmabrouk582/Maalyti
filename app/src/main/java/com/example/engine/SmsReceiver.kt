package com.example.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.room.Room
import com.example.data.local.MaalytiDatabase
import com.example.data.model.TransactionEntity
import com.example.data.repository.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    private val TAG = "SmsReceiver"
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (msg in messages) {
                val sender = msg.displayOriginatingAddress ?: "Unknown"
                val body = msg.displayMessageBody ?: continue

                Log.d(TAG, "Intercepted SMS alert from: $sender. Text: $body")

                // Run Parser and save to Database in background
                scope.launch {
                    try {
                        val db = Room.databaseBuilder(
                            context.applicationContext,
                            MaalytiDatabase::class.java,
                            "maalyti_secure_db"
                        ).build()

                        val repository = TransactionRepository(
                            db.transactionDao(),
                            db.accountDao(),
                            db.cardDao(),
                            db.budgetDao()
                        )

                        // Run smart parser on-device!
                        val preFilter = SmsPreFilter()
                        val checker = DeviceCapabilityChecker(context)
                        val nano = GeminiNanoEngine(context)
                        val mediaPipe = MediaPipeGemmaEngine(context)
                        val tflite = TFLiteNerEngine(context)
                        val correction = CorrectionLearningEngine(db.parsedSmsDao())
                        val smartParser = SmartSmsParserEngine(
                            preFilter, nano, mediaPipe, tflite, checker, correction
                        )

                        val parsedResult = smartParser.parse(body, sender, System.currentTimeMillis())

                        if (parsedResult.isBanking) {
                            // 1. Insert into parsed_sms historical tracking
                            val parsedSmsEntity = com.example.data.model.ParsedSmsEntity(
                                isBanking = parsedResult.isBanking,
                                bankName = parsedResult.bankName,
                                amount = parsedResult.amount,
                                currency = parsedResult.currency,
                                merchantName = parsedResult.merchantName,
                                transactionType = parsedResult.transactionType.name,
                                transactionDate = parsedResult.transactionDate,
                                balanceAfter = parsedResult.balanceAfter,
                                cardLastDigits = parsedResult.cardLastDigits,
                                confidence = parsedResult.confidence,
                                color = parsedResult.color.name,
                                rawSms = parsedResult.rawSms,
                                senderNumber = sender,
                                engineUsed = parsedResult.engineUsed.name
                            )
                            db.parsedSmsDao().insert(parsedSmsEntity)

                            // 2. Map transaction category
                            val typeString = when (parsedResult.transactionType) {
                                TransactionType.WITHDRAW, TransactionType.TRANSFER_OUT -> "withdraw"
                                TransactionType.DEPOSIT, TransactionType.TRANSFER_IN, TransactionType.REFUND -> "deposit"
                                TransactionType.OTP -> "otp"
                                else -> "other"
                            }

                            // 3. Populate standard viewable transactions list
                            val entity = TransactionEntity(
                                bankName = parsedResult.bankName ?: "Unknown Bank",
                                amount = parsedResult.amount ?: 0.0,
                                currency = parsedResult.currency ?: "SAR",
                                merchant = parsedResult.merchantName ?: "Merchant",
                                balance = parsedResult.balanceAfter,
                                cardLast4 = parsedResult.cardLastDigits,
                                dateString = parsedResult.transactionDate ?: "now",
                                type = typeString,
                                rawBody = parsedResult.rawSms,
                                sender = sender,
                                confidence = parsedResult.confidence.toDouble(),
                                wasFallbackUsed = parsedResult.confidence < 0.40f
                            )

                            repository.insertTransaction(entity)
                            Log.d(TAG, "SmsReceiver successfully processed on-device SMS transaction: ${parsedResult.amount} ${parsedResult.currency}")
                        } else {
                            Log.d(TAG, "SmsReceiver skipped: message classified as non-banking")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing SMS in receiver", e)
                    }
                }
            }
        }
    }
}
