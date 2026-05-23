package com.example.data.repository

import android.util.Log
import com.example.data.local.AccountDao
import com.example.data.local.BudgetDao
import com.example.data.local.CardDao
import com.example.data.local.TransactionDao
import com.example.data.model.AccountEntity
import com.example.data.model.BudgetEntity
import com.example.data.model.CardEntity
import com.example.data.model.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.delay

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val cardDao: CardDao,
    private val budgetDao: BudgetDao
) {
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()
    val allCards: Flow<List<CardEntity>> = cardDao.getAllCards()
    val currentBudget: Flow<BudgetEntity?> = budgetDao.getBudget()

    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        // Adjust corresponding account balance reactively based on card type & transaction
        val accounts = allAccounts.firstOrNull() ?: emptyList()
        if (accounts.isNotEmpty()) {
            val matchingAccount = accounts.find { 
                it.bankName.uppercase() == transaction.bankName.uppercase() || 
                transaction.rawBody.uppercase().contains(it.bankName.uppercase()) 
            } ?: accounts.first()

            val diff = transaction.amount
            val newBalance = if (transaction.type == "withdraw") {
                matchingAccount.balance - diff
            } else if (transaction.type == "deposit") {
                matchingAccount.balance + diff
            } else {
                matchingAccount.balance
            }
            accountDao.updateAccountBalance(matchingAccount.id, newBalance)
        }

        // Maintain budget recalculation spent amount
        val budget = budgetDao.getBudgetSync()
        if (budget != null && transaction.type == "withdraw") {
            val updatedBudget = budget.copy(
                currentDaySpent = budget.currentDaySpent + transaction.amount
            )
            budgetDao.insertBudget(updatedBudget)
        }

        return transactionDao.insertTransaction(transaction)
    }

    suspend fun deleteTransaction(id: Long) {
        transactionDao.deleteTransactionById(id)
    }

    suspend fun clearAllTransactions() {
        transactionDao.deleteAllTransactions()
    }

    suspend fun insertAccount(account: AccountEntity): Long {
        return accountDao.insertAccount(account)
    }

    suspend fun deleteAccount(account: AccountEntity) {
        accountDao.deleteAccount(account)
    }

    suspend fun updateAccount(account: AccountEntity) {
        accountDao.updateAccount(account)
    }

    suspend fun insertCard(card: CardEntity): Long {
        return cardDao.insertCard(card)
    }

    suspend fun updateCard(card: CardEntity) {
        cardDao.updateCard(card)
    }

    suspend fun deleteCard(card: CardEntity) {
        cardDao.deleteCard(card)
    }

    suspend fun saveBudget(budget: BudgetEntity) {
        budgetDao.insertBudget(budget)
    }

    suspend fun getBudgetSync(): BudgetEntity? {
        return budgetDao.getBudgetSync()
    }

    // Firestore integration Simulation
    suspend fun syncWithFirestore(): Boolean {
        return try {
            // Emulate secure network push delay 
            delay(1500)
            Log.d("TransactionRepository", "Synced database offline transactions and accounts to Firestore safely.")
            true
        } catch (e: Exception) {
            false
        }
    }
}
