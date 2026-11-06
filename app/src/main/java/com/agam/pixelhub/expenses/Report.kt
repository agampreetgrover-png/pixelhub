package com.agam.pixelhub.expenses

import com.agam.pixelhub.data.Expense
import com.agam.pixelhub.data.ExpenseCategory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** One spending category this month beside the same stretch of last month. */
data class CategoryChange(val category: ExpenseCategory, val paise: Long, val previousPaise: Long)

/** The day with the most spending. */
data class DaySpend(val date: LocalDate, val paise: Long)

/**
 * A month in review. While the month is still running, last month is cut to the same days
 * (1–7 Nov against 1–7 Oct), so a half-finished month isn't compared with a whole one.
 */
data class MonthReport(
    val summary: MonthSummary,
    /** Days covered: the whole month, or the days so far for this month. */
    val days: Int,
    /** True while the month is still running. */
    val partial: Boolean,
    /** Last month's spending over the compared days. */
    val previousPaise: Long,
    val dailyAveragePaise: Long,
    val biggest: Expense?,
    val busiestDay: DaySpend?,
    /** Up to [ReportMath.TOP] biggest spending categories. */
    val top: List<CategoryChange>,
) {
    val month: YearMonth get() = summary.month

    /** Percent change in spending vs last month; null when last month had none to compare with. */
    val changePercent: Int? get() = ReportMath.percentChange(previousPaise, summary.totalPaise)

    /** Total budget left (negative = over), or null without a total budget. */
    val budgetLeftPaise: Long? get() = summary.budgetPaise?.let { it - summary.totalPaise }
}

/** Pure report logic (unit-tested). */
object ReportMath {
    const val TOP = 3

    fun build(current: MonthSummary, previous: MonthSummary, today: LocalDate, zone: ZoneId): MonthReport {
        val partial = current.month == YearMonth.from(today)
        val days = if (partial) today.dayOfMonth else current.month.lengthOfMonth()
        fun day(e: Expense) = Instant.ofEpochMilli(e.spentAt).atZone(zone).toLocalDate()
        // A finished month is compared with the whole month before; a running one with the same days.
        val prevSpend = if (partial) previous.expenses.filter { day(it).dayOfMonth <= days } else previous.expenses
        val prevByCategory = prevSpend.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amountPaise } }
        return MonthReport(
            summary = current,
            days = days,
            partial = partial,
            previousPaise = prevSpend.sumOf { it.amountPaise },
            dailyAveragePaise = current.totalPaise / days,
            biggest = current.expenses.maxWithOrNull(compareBy<Expense> { it.amountPaise }.thenBy { it.spentAt }),
            busiestDay = current.expenses.groupBy(::day)
                .map { (d, l) -> DaySpend(d, l.sumOf { it.amountPaise }) }
                .maxWithOrNull(compareBy<DaySpend> { it.paise }.thenBy { it.date }),
            top = current.byCategory.filter { it.spentPaise > 0 }.take(TOP)
                .map { CategoryChange(it.category, it.spentPaise, prevByCategory[it.category] ?: 0L) },
        )
    }

    /** Whole-percent change from [before] to [after]; null when [before] is zero. */
    fun percentChange(before: Long, after: Long): Int? {
        if (before <= 0) return null
        return ((after - before).toDouble() * 100 / before).roundToLong().coerceIn(-100_000, 100_000).toInt()
    }

    /** Hour on the 1st when last month's report notification is due. */
    const val REPORT_HOUR = 9

    /**
     * When the next report notification is due, given the last month one was sent for. Normally
     * 9:00 on the coming 1st; if the last one is still owed (the phone was off on the 1st), now.
     */
    fun nextReportAt(now: LocalDateTime, lastSent: YearMonth): LocalDateTime {
        val month = YearMonth.from(now)
        val firstOfThis = month.atDay(1).atTime(REPORT_HOUR, 0)
        return when {
            lastSent >= month.minusMonths(1) -> month.plusMonths(1).atDay(1).atTime(REPORT_HOUR, 0)
            now.isBefore(firstOfThis) -> firstOfThis
            else -> now
        }
    }
}

/**
 * "12% less than September", "40% more than 1–7 October", or null when last month had no
 * spending to compare with. Words carry the direction, so colour is never the only signal.
 */
fun MonthReport.changeText(): String? {
    val pct = changePercent ?: return null
    val prevName = month.minusMonths(1).format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))
    val against = if (partial) "1–$days $prevName" else prevName
    return when {
        pct == 0 -> "Same as $against"
        pct < 0 -> "${abs(pct)}% less than $against"
        else -> "$pct% more than $against"
    }
}

/** One line for the Report card and the 1st-of-month notification. */
fun MonthReport.oneLine(): String = buildString {
    append(Money.format(summary.totalPaise)).append(" spent")
    changeText()?.let { append(" · ").append(it.replaceFirstChar { c -> c.lowercase() }) }
}
