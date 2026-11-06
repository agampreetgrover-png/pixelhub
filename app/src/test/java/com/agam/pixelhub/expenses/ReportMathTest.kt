package com.agam.pixelhub.expenses

import com.agam.pixelhub.data.Budget
import com.agam.pixelhub.data.Expense
import com.agam.pixelhub.data.ExpenseCategory
import com.agam.pixelhub.data.ExpenseCategory.BILLS
import com.agam.pixelhub.data.ExpenseCategory.FOOD
import com.agam.pixelhub.data.ExpenseCategory.SHOPPING
import com.agam.pixelhub.data.ExpenseCategory.TRANSPORT
import com.agam.pixelhub.data.ExpenseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class ReportMathTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private var nextId = 1L

    private fun on(date: LocalDate, hour: Int = 12) =
        LocalDateTime.of(date, LocalTime.of(hour, 0)).atZone(zone).toInstant().toEpochMilli()

    private fun spend(rupees: Long, cat: ExpenseCategory, date: LocalDate, kind: ExpenseKind = ExpenseKind.EXPENSE) =
        Expense(id = nextId++, amountPaise = rupees * 100, category = cat, spentAt = on(date), kind = kind)

    private fun summary(m: YearMonth, entries: List<Expense>, budgetRupees: Long? = null) =
        BudgetMath.summarize(m, entries, listOfNotNull(budgetRupees?.let { Budget(Budget.TOTAL, it * 100) }))

    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)

    @Test fun runningMonthComparesTheSameDaysOfLastMonth() {
        val prev = summary(oct, listOf(spend(100, FOOD, oct.atDay(5)), spend(500, FOOD, oct.atDay(20))))
        val cur = summary(nov, listOf(spend(140, FOOD, nov.atDay(3))))
        val r = ReportMath.build(cur, prev, today = nov.atDay(7), zone = zone)
        assertTrue(r.partial)
        assertEquals(7, r.days)
        assertEquals(100_00L, r.previousPaise)
        assertEquals(40, r.changePercent)
        assertEquals(20_00L, r.dailyAveragePaise)
    }

    @Test fun finishedMonthComparesTheWholeMonthBefore() {
        // February (28 days) must still count 29–31 January.
        val jan = YearMonth.of(2026, 1)
        val feb = YearMonth.of(2026, 2)
        val prev = summary(jan, listOf(spend(200, FOOD, jan.atDay(31))))
        val cur = summary(feb, listOf(spend(280, FOOD, feb.atDay(10))))
        val r = ReportMath.build(cur, prev, today = LocalDate.of(2026, 3, 2), zone = zone)
        assertFalse(r.partial)
        assertEquals(28, r.days)
        assertEquals(200_00L, r.previousPaise)
        assertEquals(10_00L, r.dailyAveragePaise)
    }

    @Test fun findsBiggestSpendAndBusiestDay() {
        val cur = summary(
            oct,
            listOf(
                spend(900, SHOPPING, oct.atDay(14)),
                spend(600, FOOD, oct.atDay(12)),
                spend(500, TRANSPORT, oct.atDay(12)),
                spend(5_000, ExpenseCategory.SALARY, oct.atDay(1), ExpenseKind.INCOME),
            ),
        )
        val r = ReportMath.build(cur, summary(YearMonth.of(2026, 9), emptyList()), today = nov.atDay(1), zone = zone)
        assertEquals(900_00L, r.biggest?.amountPaise)
        assertEquals(DaySpend(oct.atDay(12), 1_100_00), r.busiestDay)
    }

    @Test fun topCategoriesCarryLastMonthBesideThem() {
        val prev = summary(oct, listOf(spend(300, FOOD, oct.atDay(2))))
        val cur = summary(
            nov,
            listOf(
                spend(400, FOOD, nov.atDay(1)),
                spend(1_000, BILLS, nov.atDay(1)),
                spend(50, TRANSPORT, nov.atDay(1)),
                spend(20, SHOPPING, nov.atDay(1)),
            ),
        )
        val r = ReportMath.build(cur, prev, today = nov.atDay(30), zone = zone)
        assertEquals(listOf(BILLS, FOOD, TRANSPORT), r.top.map { it.category })
        assertEquals(300_00L, r.top[1].previousPaise)
        assertEquals(0L, r.top[0].previousPaise)
    }

    @Test fun emptyMonthHasNothingToShow() {
        val r = ReportMath.build(summary(nov, emptyList()), summary(oct, emptyList()), today = nov.atDay(7), zone = zone)
        assertNull(r.biggest)
        assertNull(r.busiestDay)
        assertNull(r.changePercent)
        assertTrue(r.top.isEmpty())
    }

    @Test fun budgetLeftIsNegativeWhenOver() {
        val cur = summary(oct, listOf(spend(1_200, FOOD, oct.atDay(3))), budgetRupees = 1_000)
        val r = ReportMath.build(cur, summary(YearMonth.of(2026, 9), emptyList()), today = nov.atDay(1), zone = zone)
        assertEquals(-200_00L, r.budgetLeftPaise)
    }

    @Test fun nextReportIsNineOnTheComingFirst() {
        val now = LocalDateTime.of(2026, 11, 7, 18, 0)
        assertEquals(LocalDateTime.of(2026, 12, 1, 9, 0), ReportMath.nextReportAt(now, lastSent = oct))
    }

    @Test fun reportStillOwedGoesOutNow() {
        // October's report was due 1 Nov 9:00 but the phone was off; it's now 3 Nov.
        val now = LocalDateTime.of(2026, 11, 3, 8, 0)
        assertEquals(now, ReportMath.nextReportAt(now, lastSent = YearMonth.of(2026, 9)))
    }

    @Test fun earlyOnTheFirstWaitsForNine() {
        val now = LocalDateTime.of(2026, 11, 1, 6, 30)
        assertEquals(LocalDateTime.of(2026, 11, 1, 9, 0), ReportMath.nextReportAt(now, lastSent = YearMonth.of(2026, 9)))
    }

    @Test fun decemberRollsIntoNextYear() {
        val now = LocalDateTime.of(2026, 12, 15, 12, 0)
        assertEquals(LocalDateTime.of(2027, 1, 1, 9, 0), ReportMath.nextReportAt(now, lastSent = nov))
    }

    @Test fun changeTextNamesTheMonthAndStretch() {
        val prev = summary(oct, listOf(spend(100, FOOD, oct.atDay(5))))
        val cur = summary(nov, listOf(spend(88, FOOD, nov.atDay(3))))
        val r = ReportMath.build(cur, prev, today = nov.atDay(7), zone = zone)
        assertEquals("12% less than 1–7 October", r.changeText())
        assertEquals("₹88 spent · 12% less than 1–7 October", r.oneLine())
    }

    @Test fun percentChange() {
        assertNull(ReportMath.percentChange(0, 500))
        assertEquals(-12, ReportMath.percentChange(100_00, 88_00))
        assertEquals(150, ReportMath.percentChange(100_00, 250_00))
        assertEquals(100_000, ReportMath.percentChange(1, Long.MAX_VALUE / 2))
    }
}
