package com.agam.pixelhub.expenses

import com.agam.pixelhub.data.Budget
import com.agam.pixelhub.data.Expense
import com.agam.pixelhub.data.ExpenseCategory
import com.agam.pixelhub.data.ExpenseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private val mixedMonth = YearMonth.of(2026, 11)
    private val mixed = listOf(
        Expense(id = 1, amountPaise = 300_00, category = ExpenseCategory.FOOD, spentAt = 1_000),
        Expense(id = 2, amountPaise = 50_000_00, category = ExpenseCategory.SALARY, spentAt = 2_000, kind = ExpenseKind.INCOME),
        Expense(id = 3, amountPaise = 1_200_00, category = ExpenseCategory.BILLS, spentAt = 3_000),
        Expense(id = 4, amountPaise = 2_500_00, category = ExpenseCategory.REFUNDS, spentAt = 4_000, kind = ExpenseKind.INCOME),
        // Other is shared by both kinds; only the spending one counts towards Other's total.
        Expense(id = 5, amountPaise = 100_00, category = ExpenseCategory.OTHER, spentAt = 5_000),
        Expense(id = 6, amountPaise = 700_00, category = ExpenseCategory.OTHER, spentAt = 5_000, kind = ExpenseKind.INCOME),
    )

    @Test fun summarySplitsIncomeFromSpending() {
        val s = BudgetMath.summarize(mixedMonth, mixed, listOf(Budget(Budget.TOTAL, 10_000_00)))
        assertEquals(listOf(1L, 3L, 5L), s.expenses.map { it.id })
        assertEquals(listOf(2L, 4L, 6L), s.incomes.map { it.id })
        assertTrue(s.expenses.none { it.isIncome })
        assertTrue(s.incomes.all { it.isIncome })
    }

    @Test fun totalExcludesIncomeAndBudgetMeasuresSpendingOnly() {
        val s = BudgetMath.summarize(mixedMonth, mixed, listOf(Budget(Budget.TOTAL, 10_000_00)))
        assertEquals(1_600_00L, s.totalPaise)
        assertEquals(0.16, s.budgetUsed!!, 1e-9)
    }

    @Test fun incomePaiseSumsIncome() {
        val s = BudgetMath.summarize(mixedMonth, mixed, emptyList())
        assertEquals(53_200_00L, s.incomePaise)
    }

    @Test fun netIsIncomeMinusSpent() {
        val s = BudgetMath.summarize(mixedMonth, mixed, emptyList())
        assertEquals(51_600_00L, s.netPaise)
        val spentOnly = BudgetMath.summarize(mixedMonth, mixed.filterNot { it.isIncome }, emptyList())
        assertEquals(-1_600_00L, spentOnly.netPaise)
        assertEquals(0L, spentOnly.incomePaise)
    }

    @Test fun byCategoryHasNoIncome() {
        val s = BudgetMath.summarize(mixedMonth, mixed, emptyList())
        assertTrue(s.byCategory.none { it.category.isIncome })
        assertEquals(
            listOf(
                CategoryTotal(ExpenseCategory.BILLS, 1_200_00, null),
                CategoryTotal(ExpenseCategory.FOOD, 300_00, null),
                CategoryTotal(ExpenseCategory.OTHER, 100_00, null), // the Other income isn't counted
            ),
            s.byCategory,
        )
    }

    @Test fun onlyIncomeLeavesSpendingEmpty() {
        val s = BudgetMath.summarize(mixedMonth, mixed.filter { it.isIncome }, emptyList())
        assertEquals(0L, s.totalPaise)
        assertTrue(s.expenses.isEmpty())
        assertTrue(s.byCategory.isEmpty())
        assertEquals(3, s.entries.size)
    }

    @Test fun entriesMixBothKindsNewestFirst() {
        val s = BudgetMath.summarize(mixedMonth, mixed.shuffled(java.util.Random(7)), emptyList())
        // Newest first; on a tie (5 and 6 share a time) the higher id comes first.
        assertEquals(listOf(6L, 5L, 4L, 3L, 2L, 1L), s.entries.map { it.id })
    }
}
