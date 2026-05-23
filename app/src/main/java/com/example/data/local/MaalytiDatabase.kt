package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.data.model.AccountEntity
import com.example.data.model.BudgetEntity
import com.example.data.model.CardEntity
import com.example.data.model.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY timestamp DESC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("UPDATE accounts SET balance = :balance WHERE id = :id")
    suspend fun updateAccountBalance(id: Long, balance: Double)
}

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY timestamp DESC")
    fun getAllCards(): Flow<List<CardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: CardEntity): Long

    @Delete
    suspend fun deleteCard(card: CardEntity)

    @Update
    suspend fun updateCard(card: CardEntity)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE id = 1 LIMIT 1")
    fun getBudget(): Flow<BudgetEntity?>

    @Query("SELECT * FROM budgets WHERE id = 1 LIMIT 1")
    suspend fun getBudgetSync(): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity)
}

@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
        CardEntity::class,
        BudgetEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MaalytiDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun cardDao(): CardDao
    abstract fun budgetDao(): BudgetDao
}
