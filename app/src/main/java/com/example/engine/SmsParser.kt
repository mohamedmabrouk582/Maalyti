package com.example.engine

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class ParsedSMS(
    val bankName: String,
    val amount: Double,
    val currency: String,
    val merchant: String,
    val balance: Double?,
    val cardLast4: String?,
    val dateString: String,
    val type: String, // "withdraw", "deposit", "otp", "other"
    val confidence: Double,
    val rawBody: String
)

object TFLiteNERAgent {

    // Multilingual NER token regex matching for Amount & Currency
    // Currencies include: SAR, EGP, AED, USD, KWD, BHD, OMR, QAR, EUR, GBP, RY, etc.
    // Arabic currencies: ريال, جنيه, درهم, دينار, دولار, ر.س, ج.م, د.إ
    private val CURRENCY_PATTERNS = listOf(
        "SAR", "EGP", "AED", "USD", "EUR", "GBP", "KWD", "OMR", "BHD", "QAR",
        "ريال", "جنيه", "درهم", "دينار", "دولار", "ر\\.س", "ج\\.م", "د\\.إ", "جنيه مصري", "ريال سعودي"
    )

    fun parse(senderId: String, smsBody: String): ParsedSMS {
        val cleanBody = smsBody.trim()
        val lowerBody = cleanBody.lowercase(Locale.ROOT)

        // 1. Detect Bank Name from Context and Sender ID first
        var bankName = detectBankName(senderId, cleanBody)

        // 2. Identify Transaction Type
        val type = detectTransactionType(cleanBody)

        // 3. Extract Card Last 4 digits
        val cardLast4 = extractCardLast4(cleanBody)

        // 4. Extract Amount and Currency
        val amountAndCurrency = extractAmountAndCurrency(cleanBody)
        val amount = amountAndCurrency.first
        val currency = amountAndCurrency.second

        // 5. Extract Merchant
        val merchant = extractMerchant(cleanBody, type)

        // 6. Extract Balance if present
        val balance = extractBalance(cleanBody)

        // 7. Extract or format Date
        val dateString = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

        // 8. Confidence Heuristic (Confidence Score)
        // If we can parse type (+15), amount (>0) (+40), currency (+15), bank context (+15), merchant (+15)
        var confidenceScore = 0.0
        if (type != "other") confidenceScore += 0.20
        if (amount > 0.0) confidenceScore += 0.40
        if (currency.isNotEmpty()) confidenceScore += 0.15
        if (bankName != "Unknown Bank") confidenceScore += 0.15
        if (merchant.isNotEmpty() && merchant != "Unspecified") confidenceScore += 0.15

        // Cap confidence at 1.0 (100%)
        val finalConfidence = minOf(1.0, confidenceScore)

        return ParsedSMS(
            bankName = bankName,
            amount = amount,
            currency = currency,
            merchant = merchant,
            balance = balance,
            cardLast4 = cardLast4,
            dateString = dateString,
            type = type,
            confidence = finalConfidence,
            rawBody = cleanBody
        )
    }

    private fun detectBankName(senderId: String, body: String): String {
        // Clean sender ID
        val sender = senderId.uppercase(Locale.ROOT).replace(Regex("[^A-Z-0-9]"), "")
        if (sender.length > 3 && !sender.contains("VERIFY") && !sender.contains("SMS") && !sender.contains("OTP")) {
            return sender
        }

        // Search body keywords in Arabic and English
        val bodyUpper = body.uppercase(Locale.ROOT)
        val bankKeywords = mapOf(
            "AL RAJHI" to listOf("RAJHI", "الراجحي"),
            "SNB" to listOf("SNB", "الأهلي", "ALAHLI"),
            "RIYAD BANK" to listOf("RIYAD", "الرياض"),
            "ALINMA" to listOf("ALINMA", "الإنماء"),
            "SAB" to listOf("SAB", "ساب"),
            "ADIB" to listOf("ADIB", "أبوظبي الإسلامي"),
            "ENBD" to listOf("ENBD", "الإمارات دبي"),
            "CIB" to listOf("CIB", "التجاري الدولي"),
            "BANQUE MISR" to listOf("BANQUE MISR", "بنك مصر"),
            "AL AHLY EGYPT" to listOf("AL AHLY", "البنك الأهلي المصري", "NBE"),
            "QNB" to listOf("QNB", "قطر الوطني")
        )

        for ((bank, keywords) in bankKeywords) {
            for (key in keywords) {
                if (bodyUpper.contains(key.uppercase(Locale.ROOT))) {
                    return bank
                }
            }
        }

        return "Unknown Bank"
    }

    private fun detectTransactionType(body: String): String {
        val otpKeywords = listOf("otp", "verification code", "رمز التحقق", "أوت بي", "تفعيل", "رمزك السري")
        val depositKeywords = listOf("deposit", "received", "credited", "إيداع", "تم إيداع", "اضافة", "مضاف", "دخلت")
        val withdrawKeywords = listOf(
            "withdrawal", "withdrawn", "debited", "purchase", "spent", "paid", "declined",
            "خصم", "سحب", "شراء", "مدفوع", "تم خصم"
        )

        val text = body.lowercase(Locale.ROOT)
        for (otp in otpKeywords) {
            if (text.contains(otp.lowercase(Locale.ROOT))) return "otp"
        }
        for (deposit in depositKeywords) {
            if (text.contains(deposit.lowercase(Locale.ROOT))) return "deposit"
        }
        for (withdraw in withdrawKeywords) {
            if (text.contains(withdraw.lowercase(Locale.ROOT))) return "withdraw"
        }
        return "other"
    }

    private fun extractCardLast4(body: String): String? {
        // Find ending with x1234 or *1234 or 4 digits near card terms
        // Arabic pattern for card digits: بطاقة تنتهي بـ 1234
        val patterns = listOf(
            Pattern.compile("(?:card|ending|card ending with|account|بطاقة|حساب|بـ)\\D*(\\d{4})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[*xX-](\\d{4})"),
            Pattern.compile("\\b\\d{4}\\b") // Standalone 4 digits as a fallback
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val group = matcher.group(1)
                if (group != null && group.length == 4) return group
            }
        }
        return null
    }

    private fun extractAmountAndCurrency(body: String): Pair<Double, String> {
        // Standard Arabic/English amount-currency extraction logic
        var detectedAmount = 0.0
        var detectedCurrency = "SAR" // Default in SA/GCC first

        // Let's search for standard currency tags first
        for (curr in CURRENCY_PATTERNS) {
            // Regex to find decimal numbers around the currency tag
            // e.g., "123.45 SAR", "SAR 123", "SAR123", "ريال ٥٠٠"
            val patternEnglish = Pattern.compile(
                "(?:$curr\\s*[:\\-]?\\s*([\\d,]+\\.?\\d*))|(?:([\\d,]+\\.?\\d*)\\s*$curr)",
                Pattern.CASE_INSENSITIVE
            )
            val matcher = patternEnglish.matcher(body)
            if (matcher.find()) {
                val amtStr1 = matcher.group(1)
                val amtStr2 = matcher.group(2)
                val finalAmtStr = amtStr1 ?: amtStr2
                if (finalAmtStr != null) {
                    val parsed = finalAmtStr.replace(",", "").toDoubleOrNull()
                    if (parsed != null && parsed > 0) {
                        detectedAmount = parsed
                        detectedCurrency = curr.replace("\\", "")
                        break
                    }
                }
            }
        }

        // Fallback to searching any decimal number if no currency matched
        if (detectedAmount == 0.0) {
            val genericNumberPattern = Pattern.compile("\\b([\\d,]+\\.\\d{2})\\b")
            val matcher = genericNumberPattern.matcher(body)
            if (matcher.find()) {
                val amtStr = matcher.group(1)
                if (amtStr != null) {
                    detectedAmount = amtStr.replace(",", "").toDoubleOrNull() ?: 0.0
                }
            }
        }

        // Clean currency representation
        val cleanCurrency = when (detectedCurrency.uppercase(Locale.ROOT)) {
            "SAR", "ر.س", "ريال", "ريال سعودي" -> "SAR"
            "EGP", "ج.م", "جنيه", "جنيه مصري" -> "EGP"
            "AED", "د.إ", "درهم" -> "AED"
            "USD", "دولار", "$" -> "USD"
            "EUR" -> "EUR"
            "GBP" -> "GBP"
            "KWD", "دينار" -> "KWD"
            else -> detectedCurrency.uppercase(Locale.ROOT)
        }

        return Pair(detectedAmount, cleanCurrency)
    }

    private fun extractMerchant(body: String, type: String): String {
        if (type == "otp") return "SMS Security System"

        // Search for prepositions like "at", "at", " لدى ", " في ", "purchased from", "to"
        val lowercaseBody = body.lowercase(Locale.ROOT)
        val prepPatterns = listOf(
            Pattern.compile("at\\s+([A-Za-z0-9\\s.\\-_]{2,30})(?:\\.)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("at\\s+([A-Za-z0-9\\s.\\-_]{2,30})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("لدى\\s+([^\\s]{2,20}\\s*(?:[^\\s]{1,15}){0,2})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("في\\s+([^\\s]{2,20}\\s*(?:[^\\s]{1,15}){0,2})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("to\\s+([A-Za-z0-9\\s.\\-_]{2,25})", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in prepPatterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val merchantName = matcher.group(1)?.trim()
                if (!merchantName.isNullOrEmpty() &&
                    !merchantName.contains("atm", ignoreCase = true) &&
                    !merchantName.contains("card", ignoreCase = true) &&
                    !merchantName.contains("balance", ignoreCase = true)
                ) {
                    return merchantName.replace(Regex("[.\\-+]$"), "").trim()
                }
            }
        }

        // Fallbacks
        return if (type == "withdraw") "Retailer / Pos Terminal" else if (type == "deposit") "Salaries & Transfers" else "Other"
    }

    private fun extractBalance(body: String): Double? {
        val balancePatterns = listOf(
            Pattern.compile("(?:bal|balance|available balance|avail bal|رصيدك|الرصيد|رصيد)\\s*[:\\-]?\\s*[A-Za-z.]*\\s*([\\d,]+\\.?\\d*)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("رصيد المتاح\\s*([\\d,]+\\.?\\d*)", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in balancePatterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val balStr = matcher.group(1)
                if (balStr != null) {
                    val parsed = balStr.replace(",", "").toDoubleOrNull()
                    if (parsed != null) return parsed
                }
            }
        }
        return null
    }
}
