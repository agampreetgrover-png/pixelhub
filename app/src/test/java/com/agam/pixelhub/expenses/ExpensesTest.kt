package com.agam.pixelhub.expenses

import com.agam.pixelhub.data.Budget
import com.agam.pixelhub.data.Expense
import com.agam.pixelhub.data.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class ExpensesTest {
    @Test fun formatsWholeRupeesWithoutDecimals() = assertEquals("₹1,250", Money.format(1_250_00))

    @Test fun formatsPaise() = assertEquals("₹99.50", Money.format(99_50))

    @Test fun usesIndianGrouping() {
        assertEquals("₹1,25,000", Money.format(1_25_000_00))
        assertEquals("₹12,34,567.05", Money.format(12_34_567_05))
        assertEquals("₹999", Money.format(999_00))
        assertEquals("₹0.05", Money.format(5))
    }

    @Test fun parsesCommonInputs() {
        assertEquals(250_00L, Money.parse("250"))
        assertEquals(1_250_50L, Money.parse("1,250.5"))
        assertEquals(99_99L, Money.parse("₹ 99.99"))
        assertEquals(40_00L, Money.parse("Rs 40"))
    }

    @Test fun rejectsBadAmounts() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("0"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("1.234"))
    }

    @Test fun fromRupeesRounds() = assertEquals(12_35L, Money.fromRupees(12.345))

    @Test fun crossing80Percent() = assertEquals(80, BudgetMath.crossed(7_000, 8_500, 10_000))

    @Test fun crossingBothThresholdsReportsHighest() = assertEquals(100, BudgetMath.crossed(5_000, 12_000, 10_000))

    @Test fun exactlyAtThresholdCounts() = assertEquals(100, BudgetMath.crossed(9_000, 10_000, 10_000))

    @Test fun alreadyPastDoesNotRealert() = assertNull(BudgetMath.crossed(8_500, 9_000, 10_000))

    @Test fun noBudgetNoAlert() = assertNull(BudgetMath.crossed(0, 1_000_000, 0))

    @Test fun summaryGroupsAndSorts() {
        val month = YearMonth.of(2026, 11)
        val expenses = listOf(
            Expense(id = 1, amountPaise = 300_00, category = ExpenseCategory.FOOD, spentAt = 0),
            Expense(id = 2, amountPaise = 1_000_00, category = ExpenseCategory.BILLS, spentAt = 0),
            Expense(id = 3, amountPaise = 200_00, category = ExpenseCategory.FOOD, spentAt = 0),
        )
        val budgets = listOf(Budget(Budget.TOTAL, 5_000_00), Budget("TRAVEL", 2_000_00))
        val s = BudgetMath.summarize(month, expenses, budgets)
        assertEquals(1_500_00L, s.totalPaise)
        assertEquals(5_000_00L, s.budgetPaise)
        assertEquals(0.3, s.budgetUsed!!, 1e-9)
        assertEquals(
            listOf(
                CategoryTotal(ExpenseCategory.BILLS, 1_000_00, null),
                CategoryTotal(ExpenseCategory.FOOD, 500_00, null),
                CategoryTotal(ExpenseCategory.TRAVEL, 0, 2_000_00), // budgeted but unspent still listed
            ),
            s.byCategory,
        )
    }
}
