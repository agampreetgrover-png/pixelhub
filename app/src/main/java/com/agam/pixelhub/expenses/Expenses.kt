package com.agam.pixelhub.expenses

import com.agam.pixelhub.data.Budget
import com.agam.pixelhub.data.BudgetDao
import com.agam.pixelhub.data.Expense
import com.agam.pixelhub.data.ExpenseCategory
import com.agam.pixelhub.data.ExpenseDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.ZoneId

/** Rupee amounts. All money in PixelHub is Long paise. */
object Money {
    /**
     * "₹1,250" for whole rupees, "₹1,250.50" otherwise, with Indian grouping (₹1,25,000).
     * Done by hand because the JVM's NumberFormat doesn't do lakh/crore grouping.
     */
    fun format(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val abs = kotlin.math.abs(paise)
        val rupees = groupIndian((abs / 100).toString())
        val fraction = abs % 100
        return if (fraction == 0L) "$sign₹$rupees" else "$sign₹$rupees.${fraction.toString().padStart(2, '0')}"
    }

    /** 1234567 → 12,34,567: last three digits, then pairs. */
    private fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val head = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return "$head,${digits.takeLast(3)}"
    }

    /** Parses "250", "1,250.5", "₹ 99.99" into paise; null if not a positive amount. */
    fun parse(text: String): Long? {
        val cleaned = text.replace("₹", "").replace(",", "").replace("Rs", "", ignoreCase = true).trim()
        val value = cleaned.toBigDecimalOrNull()?.stripTrailingZeros() ?: return null
        if (value.signum() <= 0 || value.scale() > 2) return null
        return runCatching { value.movePointRight(2).longValueExact() }.getOrNull()
    }

    fun fromRupees(rupees: Double): Long? =
        BigDecimal.valueOf(rupees).setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong().takeIf { it > 0 }
}

data class CategoryTotal(val category: ExpenseCategory, val spentPaise: Long, val budgetPaise: Long?)

data class MonthSummary(
    val month: YearMonth,
    /** Newest first. */
    val expenses: List<Expense>,
    val totalPaise: Long,
    val budgetPaise: Long?,
    /** Categories with spending or a budget, biggest spend first. */
    val byCategory: List<CategoryTotal>,
) {
    /** 0.0–1.0+ of the total budget, or null without a budget. */
    val budgetUsed: Double? get() = budgetPaise?.takeIf { it > 0 }?.let { totalPaise.toDouble() / it }
}

/** A budget threshold that a new or edited expense pushed past. */
data class BudgetAlert(val category: ExpenseCategory?, val percent: Int, val spentPaise: Long, val limitPaise: Long)

/** Pure budget logic (unit-tested). */
object BudgetMath {
    val THRESHOLDS = listOf(80, 100)

    /** The highest threshold crossed going from [before] to [after], or null. */
    fun crossed(before: Long, after: Long, limit: Long): Int? {
        if (limit <= 0) return null
        return THRESHOLDS.lastOrNull { t -> before * 100 < t * limit && after * 100 >= t * limit }
    }

    fun summarize(month: YearMonth, expenses: List<Expense>, budgets: List<Budget>): MonthSummary {
        val limits = budgets.associate { it.key to it.limitPaise }
        val spent = expenses.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.amountPaise } }
        val categories = ExpenseCategory.entries.filter { it in spent || it.name in limits }
        return MonthSummary(
            month = month,
            expenses = expenses,
            totalPaise = expenses.sumOf { it.amountPaise },
            budgetPaise = limits[Budget.TOTAL],
            byCategory = categories
                .map { CategoryTotal(it, spent[it] ?: 0L, limits[it.name]) }
                .sortedByDescending { it.spentPaise },
        )
    }
}

class ExpenseRepository(
    private val expenses: ExpenseDao,
    private val budgets: BudgetDao,
    private val onBudgetAlert: (BudgetAlert) -> Unit,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    /** Live summary for one month (default: this month). */
    fun month(month: YearMonth = YearMonth.now()): Flow<MonthSummary> {
        val (start, end) = bounds(month)
        return combine(expenses.observeBetween(start, end), budgets.observeAll()) { list, b ->
            BudgetMath.summarize(month, list, b)
        }
    }

    suspend fun get(id: Long): Expense? = expenses.get(id)

    /** Inserts (id = 0) or updates, then alerts if this pushed a budget past 80% or 100%. */
    suspend fun save(expense: Expense): Expense {
        val month = YearMonth.from(java.time.Instant.ofEpochMilli(expense.spentAt).atZone(zone()))
        val (start, end) = bounds(month)
        val totalBefore = expenses.sumBetween(start, end)
        val categoryBefore = expenses.sumBetween(start, end, expense.category)

        val id = if (expense.id == 0L) expenses.insert(expense) else expense.id.also { expenses.update(expense) }

        val totalAfter = expenses.sumBetween(start, end)
        val categoryAfter = expenses.sumBetween(start, end, expense.category)
        budgets.get(Budget.TOTAL)?.let { b ->
            BudgetMath.crossed(totalBefore, totalAfter, b.limitPaise)
                ?.let { onBudgetAlert(BudgetAlert(null, it, totalAfter, b.limitPaise)) }
        }
        budgets.get(expense.category.name)?.let { b ->
            BudgetMath.crossed(categoryBefore, categoryAfter, b.limitPaise)
                ?.let { onBudgetAlert(BudgetAlert(expense.category, it, categoryAfter, b.limitPaise)) }
        }
        return expense.copy(id = id)
    }

    suspend fun delete(expense: Expense) = expenses.delete(expense)

    /** Sets the monthly limit for everything (category = null) or one category; null limit removes it. */
    suspend fun setBudget(category: ExpenseCategory?, limitPaise: Long?) {
        val key = Budget.keyFor(category)
        if (limitPaise == null || limitPaise <= 0) budgets.delete(key) else budgets.upsert(Budget(key, limitPaise))
    }

    private fun bounds(month: YearMonth): Pair<Long, Long> {
        val z = zone()
        val start = month.atDay(1).atStartOfDay(z).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(z).toInstant().toEpochMilli()
        return start to end
    }
}
