package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.local.MaalytiDatabase
import com.example.data.model.AccountEntity
import com.example.data.model.BudgetEntity
import com.example.data.model.CardEntity
import com.example.data.model.TransactionEntity
import com.example.data.repository.TransactionRepository
import com.example.engine.BudgetEngine
import com.example.engine.GeminiSmsParser
import com.example.engine.LegacyParsedSms
import com.example.engine.TFLiteNERAgent
import com.example.engine.SmsPreFilter
import com.example.engine.DeviceCapabilityChecker
import com.example.engine.GeminiNanoEngine
import com.example.engine.MediaPipeGemmaEngine
import com.example.engine.TFLiteNerEngine
import com.example.engine.CorrectionLearningEngine
import com.example.engine.SmartSmsParserEngine
import com.example.engine.TransactionType
import com.example.data.model.ParsedSmsEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class MaalytiViewModel(application: Application) : AndroidViewModel(application) {

    private val db = Room.databaseBuilder(
        application,
        MaalytiDatabase::class.java,
        "maalyti_secure_db"
    ).build()

    val repository = TransactionRepository(
        db.transactionDao(),
        db.accountDao(),
        db.cardDao(),
        db.budgetDao()
    )

    // UI Feedback Event Stream
    private val _eventFlow = MutableSharedFlow<String>()
    val eventFlow: SharedFlow<String> = _eventFlow.asSharedFlow()

    // Interactive Filter States
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _filterType = MutableStateFlow("all") // "all", "withdraw", "deposit", "otp"
    val filterType = _filterType.asStateFlow()

    private val _selectedTargetCurrency = MutableStateFlow("SAR") // default base displays
    val selectedTargetCurrency = _selectedTargetCurrency.asStateFlow()

    // Firebase Auth simulated state
    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail = _userEmail.asStateFlow()

    private val _userName = MutableStateFlow<String?>(null)
    val userName = _userName.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _lastSyncedStr = MutableStateFlow("Never")
    val lastSyncedStr = _lastSyncedStr.asStateFlow()

    // Premium Subscription state
    private val _isPremiumLocked = MutableStateFlow(true)
    val isPremiumLocked = _isPremiumLocked.asStateFlow()

    // Parsing Status Loader State
    private val _isParsing = MutableStateFlow(false)
    val isParsing = _isParsing.asStateFlow()

    private val _parsingResultAlert = MutableStateFlow<LegacyParsedSms?>(null)
    val parsingResultAlert = _parsingResultAlert.asStateFlow()

    // Observe Room streams
    val transactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val accounts: StateFlow<List<AccountEntity>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cards: StateFlow<List<CardEntity>> = repository.allCards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budget: StateFlow<BudgetEntity?> = repository.currentBudget
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Filtered transaction list combining Search + Type
    val filteredTransactions: StateFlow<List<TransactionEntity>> = combine(
        transactions,
        _searchQuery,
        _filterType
    ) { txList, query, filter ->
        var res = txList
        if (query.isNotEmpty()) {
            res = res.filter {
                it.merchant.contains(query, ignoreCase = true) ||
                it.bankName.contains(query, ignoreCase = true) ||
                it.rawBody.contains(query, ignoreCase = true)
            }
        }
        if (filter != "all") {
            res = res.filter { it.type == filter }
        }
        res
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI consolidated dynamic states
    val netWorth: StateFlow<Double> = combine(
        accounts,
        _selectedTargetCurrency
    ) { accList, target ->
        BudgetEngine.calculateNetWorth(accList, target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val burnRateDays: StateFlow<Int> = combine(
        accounts,
        transactions
    ) { accList, txList ->
        // Use primary bank account balance (or first one)
        val primaryAcc = accList.find { it.role == "primary" } ?: accList.firstOrNull()
        if (primaryAcc != null) {
            BudgetEngine.calculateBurnRateDays(primaryAcc.balance, txList)
        } else {
            0
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        // Pre-populate some sandbox items to let the app immediately shine on launch
        viewModelScope.launch {
            val budgetCount = repository.getBudgetSync()
            if (budgetCount == null) {
                repository.saveBudget(
                    BudgetEntity(
                        monthlyIncome = 12000.0,
                        monthlySavingsGoal = 3000.0,
                        initialDailyBudget = 300.0,
                        lastRecalculatedDay = "Today"
                    )
                )

                // Standard demo accounts
                repository.insertAccount(
                    AccountEntity(
                        accountName = "Primary SNB Current",
                        bankName = "SNB",
                        balance = 8450.0,
                        currency = "SAR",
                        role = "primary"
                    )
                )
                repository.insertAccount(
                    AccountEntity(
                        accountName = "Cash Wallet Pocket",
                        bankName = "Personal Cash",
                        balance = 1200.0,
                        currency = "SAR",
                        role = "monitor"
                    )
                )
                repository.insertAccount(
                    AccountEntity(
                        accountName = "Vault Savings",
                        bankName = "Al Rajhi",
                        balance = 35000.0,
                        currency = "SAR",
                        role = "transfer"
                    )
                )

                // Standard credit cards with due notices
                val nextWeek = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L)
                val inTwoWeeks = System.currentTimeMillis() + (14 * 24 * 60 * 60 * 1000L)
                repository.insertCard(
                    CardEntity(
                        cardName = "SNB Cashback Card",
                        limitAmount = 15000.0,
                        outstandingAmount = 2450.00,
                        dueDateMillis = nextWeek,
                        cashbackPercentage = 1.5,
                        annualFeeFlag = false
                    )
                )
                repository.insertCard(
                    CardEntity(
                        cardName = "Alinma Credit Line",
                        limitAmount = 10000.0,
                        outstandingAmount = 450.00,
                        dueDateMillis = inTwoWeeks,
                        cashbackPercentage = 1.0,
                        annualFeeFlag = true
                    )
                )

                // Pre-populated standard transactions parsed
                repository.insertTransaction(
                    TransactionEntity(
                        bankName = "SNB",
                        amount = 45.50,
                        currency = "SAR",
                        merchant = "Starbucks",
                        balance = 8450.0,
                        cardLast4 = "4512",
                        dateString = "2026-05-23 09:15",
                        type = "withdraw",
                        confidence = 0.95,
                        rawBody = "SNB Alert: A withdrawal of 45.50 SAR was made at Starbucks. Remaining balance: 8,450 SAR."
                    )
                )
                repository.insertTransaction(
                    TransactionEntity(
                        bankName = "Al Rajhi",
                        amount = 3200.0,
                        currency = "SAR",
                        merchant = "Almutlaq Office Transfer",
                        balance = 35000.0,
                        cardLast4 = "9901",
                        dateString = "2026-05-22 14:02",
                        type = "deposit",
                        confidence = 0.90,
                        rawBody = "Al Rajhi Deposit: You received SAR 3,200.00 transfer from Almutlaq Office. Total Balance: 35,000 SAR"
                    )
                )
            }
        }
    }

    /**
     * Intercept and parse dry SMS alerts
     */
    fun parseIncomingSms(sender: String, body: String) {
        viewModelScope.launch {
            _isParsing.value = true
            
            // Core on-device multi-layered parser injection
            val preFilter = SmsPreFilter()
            val checker = DeviceCapabilityChecker(getApplication())
            val nano = GeminiNanoEngine(getApplication())
            val mediaPipe = MediaPipeGemmaEngine(getApplication())
            val tflite = TFLiteNerEngine(getApplication())
            val correction = CorrectionLearningEngine(db.parsedSmsDao())
            val smartParser = SmartSmsParserEngine(
                preFilter, nano, mediaPipe, tflite, checker, correction
            )

            val parsedResult = smartParser.parse(body, sender, System.currentTimeMillis())
            
            Log.d("MaalytiVM", "SmartSmsParserEngine on-device parsed SMS with confidence: ${parsedResult.confidence}")
            
            if (parsedResult.isBanking) {
                // Save historical parsed_sms entry
                val parsedSmsEntity = ParsedSmsEntity(
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

                // Map standard transaction type
                val typeString = when (parsedResult.transactionType) {
                    TransactionType.WITHDRAW, TransactionType.TRANSFER_OUT -> "withdraw"
                    TransactionType.DEPOSIT, TransactionType.TRANSFER_IN, TransactionType.REFUND -> "deposit"
                    TransactionType.OTP -> "otp"
                    else -> "other"
                }

                // High confidence - automatically save to viewable transactions list
                if (parsedResult.confidence >= 0.40) {
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
                        wasFallbackUsed = false
                    )
                    repository.insertTransaction(entity)

                    val legacyParsedSMS = LegacyParsedSms(
                        bankName = parsedResult.bankName ?: "Unknown Bank",
                        amount = parsedResult.amount ?: 0.0,
                        currency = parsedResult.currency ?: "SAR",
                        merchant = parsedResult.merchantName ?: "Merchant",
                        balance = parsedResult.balanceAfter,
                        cardLast4 = parsedResult.cardLastDigits,
                        dateString = parsedResult.transactionDate ?: "now",
                        type = typeString,
                        confidence = parsedResult.confidence.toDouble(),
                        rawBody = parsedResult.rawSms
                    )
                    _parsingResultAlert.value = legacyParsedSMS
                    _eventFlow.emit("On-device AI parsed SMS successfully (Engine: ${parsedResult.engineUsed.name}). Saved locally.")
                } else {
                    // Low confidence model fallback (<40%) - Call Gemini API if user wants fallback.
                    // Or since we want purely offline and privacy-first, we can flag review state.
                    _eventFlow.emit("Inference confidence low (${(parsedResult.confidence*100).toInt()}%). Stored as yellow review status.")
                    
                    val entity = TransactionEntity(
                        bankName = parsedResult.bankName ?: "Needs Review",
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
                        wasFallbackUsed = false
                    )
                    repository.insertTransaction(entity)

                    val legacyParsedSMS = LegacyParsedSms(
                        bankName = parsedResult.bankName ?: "Needs Review",
                        amount = parsedResult.amount ?: 0.0,
                        currency = parsedResult.currency ?: "SAR",
                        merchant = parsedResult.merchantName ?: "Merchant",
                        balance = parsedResult.balanceAfter,
                        cardLast4 = parsedResult.cardLastDigits,
                        dateString = parsedResult.transactionDate ?: "now",
                        type = typeString,
                        confidence = parsedResult.confidence.toDouble(),
                        rawBody = parsedResult.rawSms
                    )
                    _parsingResultAlert.value = legacyParsedSMS
                }
            } else {
                _eventFlow.emit("SmsPreFilter rejected: This message does not look like a bank SMS notification.")
            }
            _isParsing.value = false
        }
    }

    fun dismissParsingAlert() {
        _parsingResultAlert.value = null
    }

    fun updateSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun updateFilterType(type: String) {
        _filterType.value = type
    }

    fun changeTargetCurrency(curr: String) {
        _selectedTargetCurrency.value = curr
    }

    // Interactive Action Adders
    fun triggerManualItem(
        bankName: String,
        amount: Double,
        currency: String,
        merchant: String,
        type: String,
        cardLast4: String?
    ) {
        viewModelScope.launch {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            val dateStr = sdf.format(java.util.Date())
            val desc = if (type == "withdraw") "Purchase at $merchant for $amount $currency" else "Account Deposit from $merchant"
            
            repository.insertTransaction(
                TransactionEntity(
                    bankName = bankName,
                    amount = amount,
                    currency = currency,
                    merchant = merchant,
                    balance = null,
                    cardLast4 = cardLast4,
                    dateString = dateStr,
                    type = type,
                    rawBody = "Manual Entry: $desc",
                    sender = "ManualInput",
                    confidence = 1.0,
                    wasFallbackUsed = false
                )
            )
            _eventFlow.emit("Manual $type recorded safely.")
        }
    }

    fun removeTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
            _eventFlow.emit("Transaction dismissed.")
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAllTransactions()
            _eventFlow.emit("Cleaned workspace.")
        }
    }

    // Google Sign-In & Firestore sync triggers
    fun performGoogleSignIn() {
        viewModelScope.launch {
            _userName.value = "Mohamed Mabrouk"
            _userEmail.value = "mohamed.mabrouk@alarabiya.net"
            _eventFlow.emit("Welcome back, Mohamed Mabrouk! (Signed in via Google)")
            triggerFirestoreSync()
        }
    }

    fun performSignOut() {
        viewModelScope.launch {
            _userName.value = null
            _userEmail.value = null
            _eventFlow.emit("Signed out.")
        }
    }

    fun triggerFirestoreSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            _eventFlow.emit("Connecting Firestore safe pipeline...")
            val success = repository.syncWithFirestore()
            if (success) {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                _lastSyncedStr.value = sdf.format(java.util.Date())
                _eventFlow.emit("Cloud Firestore sync succeeded!")
            } else {
                _eventFlow.emit("Firestore pipeline connection error. Kept local.")
            }
            _isSyncing.value = false
        }
    }

    // Recalculating Dynamic Budgets configuration
    fun configurePrimaryBudget(monthlyIncome: Double, savingsGoal: Double, baseDaily: Double) {
        viewModelScope.launch {
            val existing = repository.getBudgetSync()
            val newBudget = BudgetEntity(
                id = 1,
                monthlyIncome = monthlyIncome,
                monthlySavingsGoal = savingsGoal,
                initialDailyBudget = baseDaily,
                customOverriddenDailyLimit = baseDaily,
                currentDaySpent = existing?.currentDaySpent ?: 0.0,
                lastRecalculatedDay = "Recalculated"
            )
            repository.saveBudget(newBudget)
            _eventFlow.emit("Smart dynamic budget limits recalculated safely.")
        }
    }

    // Credit Card creators
    fun configureCard(cardName: String, limit: Double, outstanding: Double, daysLimit: Int, alertFlag: Boolean) {
        viewModelScope.launch {
            val due = System.currentTimeMillis() + (daysLimit * 24 * 60 * 60 * 1000L)
            repository.insertCard(
                CardEntity(
                    cardName = cardName,
                    limitAmount = limit,
                    outstandingAmount = outstanding,
                    dueDateMillis = due,
                    cashbackPercentage = 1.5,
                    annualFeeFlag = false,
                    feeAlertActive = alertFlag
                )
            )
            _eventFlow.emit("Card $cardName added to credit alert watch list.")
        }
    }

    // Bank account creator
    fun triggerNewAccount(name: String, bank: String, bal: Double, curr: String, role: String) {
        viewModelScope.launch {
            repository.insertAccount(
                AccountEntity(
                    accountName = name,
                    bankName = bank,
                    balance = bal,
                    currency = curr,
                    role = role
                )
            )
            _eventFlow.emit("Account $name activated with role: $role.")
        }
    }

    // Payment Processing premium upgrade (19 SAR / 49 EGP)
    fun processPremiumPurchase() {
        viewModelScope.launch {
            _isPremiumLocked.value = false
            _eventFlow.emit("Premium Membership active! 👑 (Paid 19 SAR). Pro statistics unlocked.")
        }
    }
}
