package com.example.engine

import android.util.Log
import com.example.data.local.ParsedSmsDao
import com.example.data.model.MerchantRuleEntity
import com.example.data.model.SenderOverrideEntity
import com.example.data.model.UserCorrectionEntity
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CorrectionLearningEngine @Inject constructor(
  private val parsedSmsDao: ParsedSmsDao
) {

  // Record a user correction and trigger pattern learning
  suspend fun learnCorrection(
    rawSms: String,
    senderNumber: String?,
    fieldName: String,
    wrongValue: String?,
    correctValue: String,
    engineUsed: String
  ) {
    try {
      val correction = UserCorrectionEntity(
        rawSms = rawSms,
        senderNumber = senderNumber,
        fieldName = fieldName,
        wrongValue = wrongValue,
        correctValue = correctValue,
        engineUsed = engineUsed
      )
      parsedSmsDao.insertCorrection(correction)

      // Learn patterns: check if we have 3 or more corrections for the same sender and field
      if (senderNumber != null) {
        val sameSenderCorrections = parsedSmsDao.getCorrectionsForSenderAndField(senderNumber, fieldName)
        if (sameSenderCorrections.size >= 3) {
          // Find the most frequent correct value or compile a rule
          // e.g. "always extract correctValue as the fieldName for this sender"
          val compiledRule = sameSenderCorrections.map { it.correctValue }
            .groupBy { it }
            .maxByOrNull { it.value.size }?.key ?: correctValue

          val override = SenderOverrideEntity(
            senderPattern = senderNumber,
            fieldName = fieldName,
            extractionHint = compiledRule
          )
          parsedSmsDao.insertSenderOverride(override)
          Log.d("CorrectionLearning", "Pattern Learning: Compiled SenderOverride rule for $senderNumber -> field $fieldName = $compiledRule")
        }
      }
    } catch (e: Exception) {
      Log.e("CorrectionLearning", "Error during learnCorrection: ${e.message}")
    }
  }

  // Record merchant category memory
  suspend fun learnMerchantCategory(merchantName: String, category: String) {
    try {
      val pattern = merchantName.lowercase().trim()
      if (pattern.isEmpty()) return

      val existing = parsedSmsDao.getMerchantRule(pattern)
      if (existing != null) {
        val count = existing.confirmedCount + 1
        val confidence = if (count >= 5) 1.0f else 0.8f
        val updated = existing.copy(
          category = category,
          confirmedCount = count,
          confidence = confidence
        )
        parsedSmsDao.insertMerchantRule(updated)
      } else {
        val newRule = MerchantRuleEntity(
          merchantPattern = pattern,
          category = category,
          confidence = 0.6f,
          confirmedCount = 1
        )
        parsedSmsDao.insertMerchantRule(newRule)
      }
    } catch (e: Exception) {
      Log.e("CorrectionLearning", "Error during learnMerchantCategory: ${e.message}")
    }
  }

  // Pre-inference override check: fast extraction before model inference
  suspend fun applyPreInferenceOverrides(
    rawSms: String,
    senderNumber: String?
  ): PreInferenceResult {
    var overridenBank: String? = null
    var overridenCategory: String? = null
    var overridenAmount: Double? = null
    var overridenMerchant: String? = null

    try {
      if (senderNumber != null) {
        // Fetch specific sender overrides for this sender
        val override = parsedSmsDao.getSenderOverride(senderNumber)
        if (override != null) {
          when (override.fieldName) {
            "bankName" -> overridenBank = override.extractionHint
            "merchantName" -> overridenMerchant = override.extractionHint
            "amount" -> overridenAmount = override.extractionHint.toDoubleOrNull()
          }
        }
      }

      // Check merchant database rules to see if we have confirmed categories
      val words = rawSms.lowercase().split(Regex("\\s+"))
      for (word in words) {
        val cleanWord = word.replace(Regex("[^a-z0-9]"), "")
        if (cleanWord.length > 2) {
          val rule = parsedSmsDao.getMerchantRule(cleanWord)
          if (rule != null && rule.confirmedCount >= 5) {
            overridenCategory = rule.category
            break
          }
        }
      }
    } catch (e: Exception) {
      Log.e("CorrectionLearning", "Error in applyPreInferenceOverrides: ${e.message}")
    }

    return PreInferenceResult(
      bankName = overridenBank,
      merchantCategory = overridenCategory,
      amount = overridenAmount,
      merchantName = overridenMerchant
    )
  }
}

data class PreInferenceResult(
  val bankName: String?,
  val merchantCategory: String?,
  val amount: Double?,
  val merchantName: String?
)
