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
import com.example.engine.ParsedSMS
import com.example.engine.TFLiteNERAgent
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

    private val _parsingResultAlert = MutableStateFlow<ParsedSMS?>(null)
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
            // 1) Run TFLite multilingual NER model natively
            val onDeviceResult = TFLiteNERAgent.parse(sender, body)
            
            Log.d("MaalytiVM", "TFLiteNERAgent on-device parsed SMS with confidence: ${onDeviceResult.confidence}")
            
            if (onDeviceResult.confidence >= 0.40) {
                // High confidence - automatically save
                repository.insertTransaction(
                    TransactionEntity(
                        bankName = onDeviceResult.bankName,
                        amount = onDeviceResult.amount,
                        currency = onDeviceResult.currency,
                        merchant = onDeviceResult.merchant,
                        balance = onDeviceResult.balance,
                        cardLast4 = onDeviceResult.cardLast4,
                        dateString = onDeviceResult.dateString,
                        type = onDeviceResult.type,
                        rawBody = onDeviceResult.rawBody,
                        sender = sender,
                        confidence = onDeviceResult.confidence,
                        wasFallbackUsed = false
                    )
                )
                _parsingResultAlert.value = onDeviceResult
                _eventFlow.emit("On-device AI parsed SMS with high confidence (${(onDeviceResult.confidence*100).toInt()}%). Saved locally.")
            } else {
                // Low confidence model fallback - Call Gemini API
                _eventFlow.emit("Confidence low (${(onDeviceResult.confidence*100).toInt()}%). Calling Gemini AI fallback...")
                
                val geminiResult = GeminiSmsParser.parseWithGemini(body)
                if (geminiResult != null) {
                    repository.insertTransaction(
                        TransactionEntity(
                            bankName = geminiResult.bankName,
                            amount = geminiResult.amount,
                            currency = geminiResult.currency,
                            merchant = geminiResult.merchant,
                            balance = geminiResult.balance,
                            cardLast4 = geminiResult.cardLast4,
                            dateString = geminiResult.dateString,
                            type = geminiResult.type,
                            rawBody = geminiResult.rawBody,
                            sender = sender,
                            confidence = 1.0,
                            wasFallbackUsed = true
                        )
                    )
                    _parsingResultAlert.value = geminiResult
                    _eventFlow.emit("Gemini SMS Parser completed fallback sync successfully. Saved.")
                } else {
                    // Fallback to on-device even if low confidence if Gemini fails
                    repository.insertTransaction(
                        TransactionEntity(
                            bankName = onDeviceResult.bankName,
                            amount = onDeviceResult.amount,
                            currency = onDeviceResult.currency,
                            merchant = onDeviceResult.merchant,
                            balance = onDeviceResult.balance,
                            cardLast4 = onDeviceResult.cardLast4,
                            dateString = onDeviceResult.dateString,
                            type = onDeviceResult.type,
                            rawBody = onDeviceResult.rawBody,
                            sender = sender,
                            confidence = onDeviceResult.confidence,
                            wasFallbackUsed = false
                        )
                    )
                    _parsingResultAlert.value = onDeviceResult
                    _eventFlow.emit("Saved on-device prediction with warning (Gemini fallback offline).")
                }
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
