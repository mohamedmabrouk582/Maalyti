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

                        // Run TFLite multi-lingual parser on-device!
                        val onDeviceParsed = TFLiteNERAgent.parse(sender, body)

                        val entity = TransactionEntity(
                            bankName = onDeviceParsed.bankName,
                            amount = onDeviceParsed.amount,
                            currency = onDeviceParsed.currency,
                            merchant = onDeviceParsed.merchant,
                            balance = onDeviceParsed.balance,
                            cardLast4 = onDeviceParsed.cardLast4,
                            dateString = onDeviceParsed.dateString,
                            type = onDeviceParsed.type,
                            rawBody = onDeviceParsed.rawBody,
                            sender = sender,
                            confidence = onDeviceParsed.confidence,
                            wasFallbackUsed = false
                        )

                        repository.insertTransaction(entity)
                        Log.d(TAG, "SmsReceiver successfully processed and inserted transaction: ${onDeviceParsed.amount} ${onDeviceParsed.currency}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing SMS in receiver", e)
                    }
                }
            }
        }
    }
}
