package com.example.engine

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartSmsParserEngine @Inject constructor(
  private val preFilter: SmsPreFilter,
  private val nanoEngine: GeminiNanoEngine,
  private val mediaPipeEngine: MediaPipeGemmaEngine,
  private val tfliteEngine: TFLiteNerEngine,
  private val deviceChecker: DeviceCapabilityChecker,
  private val correctionEngine: CorrectionLearningEngine
) : SmsParserEngine {

  override suspend fun parse(
    rawSms: String,
    senderNumber: String?,
    receivedAt: Long
  ): ParsedSms {
    Log.d("SmartSmsParser", "Parsing message from: $senderNumber")

    // STEP 0: pre-filter <1ms
    val preFilterResult = preFilter.analyze(rawSms)
    if (!preFilterResult.mightBeBanking) {
      Log.d("SmartSmsParser", "PreFilter REJECT: Not banking or filtered")
      return ParsedSms(
        isBanking = false,
        bankName = null,
        amount = null,
        currency = null,
        merchantName = null,
        transactionType = TransactionType.OTHER,
        transactionDate = null,
        balanceAfter = null,
        cardLastDigits = null,
        confidence = 0.0f,
        color = SmsColor.GRAY,
        rawSms = rawSms,
        engineUsed = ParserEngine.TFLITE_NER
      )
    }

    if (preFilterResult.isDefinitelyOtp) {
      Log.d("SmartSmsParser", "PreFilter OTP: Direct fast OTP building")
      return buildOtpResult(rawSms, senderNumber)
    }

    // STEP 1: Pre-inference local corrections / overrides memory
    val localOverrides = correctionEngine.applyPreInferenceOverrides(rawSms, senderNumber)

    // Base parser execution logic cascade
    var finalResult: ParsedSms? = null

    // Layer 1: Gemini Nano
    if (deviceChecker.isGeminiNanoAvailable()) {
      try {
        val res = nanoEngine.parse(rawSms, senderNumber)
        if (res != null && res.confidence > 0.70f) {
          finalResult = res.copy(engineUsed = ParserEngine.GEMINI_NANO)
        }
      } catch (e: Exception) {
        Log.e("SmartSmsParser", "Gemini Nano layer failed, falling through: ${e.message}")
      }
    }

    // Layer 2: MediaPipe Gemma
    if (finalResult == null && deviceChecker.isMediaPipeReady()) {
      try {
        val res = mediaPipeEngine.parse(rawSms, senderNumber)
        if (res != null && res.confidence > 0.70f) {
          finalResult = res.copy(engineUsed = ParserEngine.GEMMA_MEDIAPIPE)
        }
      } catch (e: Exception) {
        Log.e("SmartSmsParser", "MediaPipe Gemma layer failed, falling through: ${e.message}")
      }
    }

    // Layer 3: TFLite NER (always on-device, zero-cost, guaranteed)
    if (finalResult == null) {
      finalResult = tfliteEngine.parse(rawSms, senderNumber)
    }

    // STEP 4: Merge local override fields if any are present from user corrections database
    var mergedResult = finalResult
    if (localOverrides.bankName != null || localOverrides.merchantName != null || localOverrides.amount != null) {
      mergedResult = mergedResult.copy(
        bankName = localOverrides.bankName ?: mergedResult.bankName,
        merchantName = localOverrides.merchantName ?: mergedResult.merchantName,
        amount = localOverrides.amount ?: mergedResult.amount,
        confidence = 1.0f // Set to 100% confidence because it is explicitly approved by user overrides
      )
      Log.d("SmartSmsParser", "Applied high-confidence user corrections learning overlay automatically.")
    }

    return mergedResult
  }

  private fun buildOtpResult(rawSms: String, senderNumber: String?): ParsedSms {
    val otpRegex = Regex("\\b\\d{4,6}\\b")
    val code = otpRegex.find(rawSms)?.value
    return ParsedSms(
      isBanking = true,
      bankName = "SMS OTP Verification",
      amount = null,
      currency = null,
      merchantName = if (code != null) "OTP Code: $code" else "Secure Verification",
      transactionType = TransactionType.OTP,
      transactionDate = null,
      balanceAfter = null,
      cardLastDigits = null,
      confidence = 1.0f,
      color = SmsColor.GRAY,
      rawSms = rawSms,
      engineUsed = ParserEngine.TFLITE_NER
    )
  }
}
