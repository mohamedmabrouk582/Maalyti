package com.example.ui.components

object ArabicGlossary {
    
    // Arabic translation key-pair mapping
    private val translations = mapOf(
        "app_title" to "ماليتي — Maalyti",
        "budget_ring" to "حلقة الميزانية اليومية",
        "remaining" to "متبقي",
        "spent_today" to "المصروف اليوم",
        "burn_rate_title" to "مؤشر نفاد السيولة",
        "burn_rate_desc" to "رصيدك المالي المتاح يغطي أيامك لـ:",
        "days" to "أيام",
        "limit_recalculated" to "ميزانية ذكية متكيفة",
        "limit_recalc_desc" to "تمت إعادة حساب ميزانيتك اليومية لتفادي تجاوز المصاريف.",
        "transactions" to "العمليات والتحليلات",
        "recent_activity" to "أحدث التنبيهات والرسائل",
        "search" to "البحث عن عملية...",
        "filter" to "التصنيف",
        "all" to "الكل",
        "withdraw" to "سحب ومشتريات",
        "deposit" to "إيداعات وتحويلات",
        "otp" to "رموز التفعيل (OTP)",
        "confidence" to "دقة وتصنيف الموديل:",
        "was_fallback" to "تم الاستعانة بـ Gemini AI",
        "card_alerts" to "تنبيهات البطاقات الائتمانية",
        "due_in" to "تستحق خلال:",
        "cashback_fee" to "مقارنة الرسوم والكاش باك",
        "multi_account" to "توزيع الحسابات والأدوار",
        "role" to "الدور:",
        "net_worth" to "ملخص صافي الثروة الحالي",
        "premium_upgrade" to "الترقية للعضوية الممتازة",
        "premium_upgrade_desc" to "افتح تصدير التقارير (PDF/CSV) وتحليلات المصاريف الذكية مدى الحياة.",
        "unlock" to "دفع اشتراك لمرة واحدة (19 ر.س / 49 ج.م)",
        "unlocked_msg" to "👑 المزايا الممتازة نشطة الآن!",
        "simulate_sms" to "محاكي استقبال رسائل البنوك",
        "sandbox_alert" to "أرسل رسالة نصية تجريبية لاختبار دقة الموديل في استخراج البيانات تلقائياً:",
        "custom_sms_prompt" to "نص الرسالة المصرفية...",
        "parse" to "معالجة الرسالة الآن 🤖",
        "accounts" to "الحسابات والمحافظ",
        "reports" to "الإحصائيات والتحليلات",
        "budget" to "الميزانية والتنبؤ",
        "smart_advise" to "استشارة الذكاء الاصطناعي اليومية",
        "sync_firestore" to "مزامنة Cloud Firestore",
        "logged_in_as" to "مسجل باسم",
        "never_synced" to "لم تتم المزامنة بعد",
        "last_sync" to "آخر مزامنة:"
    )

    fun get(key: String, isArabic: Boolean): String {
        return if (isArabic) {
            translations[key] ?: key
        } else {
            // Return basic English representation
            when(key) {
                "app_title" -> "Maalyti — On-Device SMS AI"
                "budget_ring" -> "Daily Budget Velocity Ring"
                "remaining" -> "Remaining"
                "spent_today" -> "Spent Today"
                "burn_rate_title" -> "Liquidity Runway Indicator"
                "burn_rate_desc" -> "Your primary liquid reserves will last:"
                "days" -> "days"
                "limit_recalculated" -> "Smart Adaptive Budgeting"
                "limit_recalc_desc" -> "Daily budget limits dynamically adjusted to recover from overspending."
                "transactions" -> "Transactions & Auditing"
                "recent_activity" -> "Recent Parsed Notifications"
                "search" -> "Search transactions..."
                "filter" -> "Filter Category"
                "all" -> "All"
                "withdraw" -> "Withdrawals"
                "deposit" -> "Deposits"
                "otp" -> "OTPs & Codes"
                "confidence" -> "On-Device NER Confidence:"
                "was_fallback" -> "Gemini API Fallback Active"
                "card_alerts" -> "Credit Card Late Fee Alerts"
                "due_in" -> "Due in:"
                "cashback_fee" -> "Cashback vs Fee Comparison"
                "multi_account" -> "Accounts & Strategic Roles"
                "role" -> "Role:"
                "net_worth" -> "Consolidated Net Worth Summary"
                "premium_upgrade" -> "Upgrade to Premium Membership"
                "premium_upgrade_desc" -> "Permanently unlock advanced AI analytical forecasting, PDF/CSV backups, and premium metrics."
                "unlock" -> "One-time Premium (19 SAR / 49 EGP)"
                "unlocked_msg" -> "👑 Premium Features Active!"
                "simulate_sms" -> "Simulate Incoming Bank SMS"
                "sandbox_alert" -> "Simulate an incoming SMS transmission to experience the on-device AI model parsing in real time:"
                "custom_sms_prompt" -> "Enter bank text here..."
                "parse" -> "Trigger On-Device Parser 🤖"
                "accounts" -> "Accounts & Wallets"
                "reports" -> "Insights & Reports"
                "budget" -> "Budget Planner"
                "smart_advise" -> "AI Assistant Advisory"
                "sync_firestore" -> "Sync Cloud Firestore"
                "logged_in_as" -> "Logged in as"
                "never_synced" -> "Never synchronized"
                "last_sync" -> "Last Synced:"
                else -> key.replace("_", " ").capitalize()
            }
        }
    }
}
