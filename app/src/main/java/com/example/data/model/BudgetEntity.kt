package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: Int = 1, // Single row budget config
    val monthlyIncome: Double,
    val monthlySavingsGoal: Double,
    val initialDailyBudget: Double,
    val customOverriddenDailyLimit: Double? = null,
    val currentDaySpent: Double = 0.0,
    val lastRecalculatedDay: String = ""
)
