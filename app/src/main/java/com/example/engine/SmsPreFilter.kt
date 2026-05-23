package com.example.engine

data class PreFilterResult(
  val mightBeBanking: Boolean,
  val isDefinitelyOtp: Boolean,
  val detectedCurrency: String?,
  val hasAmount: Boolean
)

class SmsPreFilter {

  private val ARABIC_FINANCIAL = listOf(
    "خصم", "إيداع", "سحب", "رصيد", "تحويل", "بطاقة",
    "حساب", "مبلغ", "عملية", "ائتمان", "مدين",
    "محفظة", "دفع", "استرداد", "مدفوع", "وارد", "صادر",
    "خصم,", "تم خصم"
  )

  private val ENGLISH_FINANCIAL = listOf(
    "debited", "credited", "withdrawn", "deposited",
    "balance", "account", "transaction", "payment",
    "transfer", "purchase", "charged", "received",
    "spent", "paid", "refund", "cashback", "debit", "credit"
  )

  private val CURRENCY_PATTERNS = listOf(
    "ريال", "جنيه", "درهم", "دينار", "ليرة", "روبية",
    "SAR", "EGP", "AED", "KWD", "BHD", "OMR", "QAR",
    "USD", "EUR", "GBP", "INR", "PKR", "TRY", "MAD",
    "$", "€", "£", "₹", "¥", "₩", "₽"
  )

  private val OTP_PATTERNS = listOf(
    "OTP", "رمز", "verification", "كلمة المرور المؤقتة",
    "one-time", "do not share", "لا تشارك",
    "expires in", "PIN", "تحقق", "رمز التحقق",
    "valid for", "صالح لمدة"
  )

  private val REJECT_SOCIAL = listOf(
    "liked your", "commented", "followed", "أعجب بـ", "علّق على", "تابعك"
  )

  private val REJECT_DELIVERY = listOf(
    "your order", "shipment", "package", "طلبك", "شحنة", "توصيل"
  )

  private val REJECT_PROMO = listOf(
    "discount", "offer", "عرض", "خصم خاص"
  )

  private val AMOUNT_REGEX = Regex("\\d{1,3}([,،]\\d{3})*(\\.\\d{1,3})?")

  fun analyze(sms: String): PreFilterResult {
    val clean = sms.trim()
    val lower = clean.lowercase()

    // REJECT rule 1: Zero digits in message
    val hasDigits = clean.any { it.isDigit() }
    if (!hasDigits) {
      return PreFilterResult(mightBeBanking = false, isDefinitelyOtp = false, detectedCurrency = null, hasAmount = false)
    }

    // REJECT rule 2: Social media patterns
    if (REJECT_SOCIAL.any { lower.contains(it) }) {
      return PreFilterResult(mightBeBanking = false, isDefinitelyOtp = false, detectedCurrency = null, hasAmount = false)
    }

    // REJECT rule 3: Delivery patterns
    if (REJECT_DELIVERY.any { lower.contains(it) }) {
      return PreFilterResult(mightBeBanking = false, isDefinitelyOtp = false, detectedCurrency = null, hasAmount = false)
    }

    // REJECT rule 4: Promotional only
    val hasAccountRef = lower.contains("card") || lower.contains("acc") || lower.contains("بطاقة") || lower.contains("حساب")
    val hasAmountPattern = AMOUNT_REGEX.containsMatchIn(clean)
    if (REJECT_PROMO.any { lower.contains(it) }) {
      // Reject promo unless it also has amount + account reference
      if (!(hasAmountPattern && hasAccountRef)) {
        return PreFilterResult(mightBeBanking = false, isDefinitelyOtp = false, detectedCurrency = null, hasAmount = false)
      }
    }

    // Check OTP
    val isOtp = OTP_PATTERNS.any { pattern ->
      lower.contains(pattern.lowercase()) || clean.contains(pattern)
    }

    // Detect currency
    var detectedCurrency: String? = null
    for (curr in CURRENCY_PATTERNS) {
      if (clean.contains(curr) || lower.contains(curr.lowercase())) {
        detectedCurrency = curr
        break
      }
    }

    // Check financial signal matches
    val hasArabicSignal = ARABIC_FINANCIAL.any { clean.contains(it) }
    val hasEnglishSignal = ENGLISH_FINANCIAL.any { lower.contains(it) }
    val mightBeBanking = hasArabicSignal || hasEnglishSignal || (detectedCurrency != null && hasAmountPattern)

    return PreFilterResult(
      mightBeBanking = mightBeBanking || isOtp,
      isDefinitelyOtp = isOtp,
      detectedCurrency = detectedCurrency,
      hasAmount = hasAmountPattern
    )
  }
}
