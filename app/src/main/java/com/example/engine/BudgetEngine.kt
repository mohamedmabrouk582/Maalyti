package com.example.engine

import com.example.data.model.AccountEntity
import com.example.data.model.BudgetEntity
import com.example.data.model.TransactionEntity
import java.util.Calendar

object BudgetEngine {

    // Exchange rates (Base: USD)
    private val EXC_RATES = mapOf(
        "USD" to 1.0,
        "SAR" to 3.75,
        "EGP" to 48.5,
        "AED" to 3.67
    )

    fun convert(amount: Double, from: String, to: String): Double {
        val baseAmount = amount / (EXC_RATES[from] ?: 1.0)
        return baseAmount * (EXC_RATES[to] ?: 1.0)
    }

    /**
     * Net Worth consolidation across multi-accounts into a specific target currency
     */
    fun calculateNetWorth(accounts: List<AccountEntity>, targetCurrency: String): Double {
        var total = 0.0
        for (acc in accounts) {
            total += convert(acc.balance, acc.currency, targetCurrency)
        }
        return total
    }

    /**
     * Burn Rate calculation: "Money runs out in X days."
     * Calculated by taking remaining liquid balance in primary account divided by net daily spending velocity.
     */
    fun calculateBurnRateDays(primaryBalance: Double, recentWithdrawals: List<TransactionEntity>, windowDays: Int = 7): Int {
        if (primaryBalance <= 0) return 0
        if (recentWithdrawals.isEmpty()) return 99 // Safe maximum placeholder if no spending yet
        
        // Sum spending in the window period
        val spendingSum = recentWithdrawals.asSequence()
            .filter { it.type == "withdraw" }
            .take(15) // Limit to recent items
            .sumOf { it.amount }

        if (spendingSum <= 0) return 99 // Multi-day safe projection

        val averageDailySpent = spendingSum / maxOf(1, windowDays)
        val projectDays = (primaryBalance / averageDailySpent).toInt()
        
        return minOf(365, maxOf(1, projectDays))
    }

    /**
     * Smart Budget Recalculation:
     * Dynamic daily limits adjust reactively to over-spending.
     * Formula: (Monthly Income - Monthly Savings Goal - Core Spent This Month) / Remaining Days in Month.
     */
    fun recalculateDailyLimit(
        monthlyIncome: Double,
        savingsGoal: Double,
        spentSoFarThisMonth: Double
    ): Double {
        val calendar = Calendar.getInstance()
        val totalDaysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val remainingDays = maxOf(1, totalDaysInMonth - currentDay + 1)

        val remainingBudgetPool = monthlyIncome - savingsGoal - spentSoFarThisMonth
        val calculatedDaily = remainingBudgetPool / remainingDays
        
        return maxOf(0.0, calculatedDaily)
    }
}
