package com.agam.pixelhub.scheduling

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class RepeatType { NONE, DAILY, WEEKLY, EVERY_N_DAYS, EVERY_N_WEEKS }

data class Recurrence(
    val type: RepeatType = RepeatType.NONE,
    /** WEEKLY only: bitmask of days, see [Days]. */
    val daysMask: Int = 0,
    /** EVERY_N_DAYS / EVERY_N_WEEKS only. */
    val interval: Int = 1,
) {
    val isRepeating: Boolean get() = type != RepeatType.NONE

    fun describe(): String = when (type) {
        RepeatType.NONE -> ""
        RepeatType.DAILY -> "Every day"
        RepeatType.WEEKLY -> if ((daysMask and Days.ALL) == 0) "Weekly" else Days.label(daysMask)
        RepeatType.EVERY_N_DAYS -> if (interval <= 1) "Every day" else "Every $interval days"
        RepeatType.EVERY_N_WEEKS -> if (interval <= 1) "Every week" else "Every $interval weeks"
    }

    companion object {
        val NONE = Recurrence()
    }
}

/** Bit 0 = Monday ... bit 6 = Sunday. */
object Days {
    const val WEEKDAYS = 0b0011111
    const val WEEKENDS = 0b1100000
    const val ALL = 0b1111111

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    fun contains(mask: Int, day: DayOfWeek): Boolean = (mask and bit(day)) != 0

    fun label(mask: Int): String = when (mask and ALL) {
        0 -> "Once"
        ALL -> "Every day"
        WEEKDAYS -> "Weekdays"
        WEEKENDS -> "Weekends"
        else -> DayOfWeek.entries.filter { contains(mask, it) }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
}

object RecurrenceCalc {
    /**
     * The first occurrence strictly after [after], or null if the rule never fires again.
     * [anchor] is local wall-clock time: it sets the time of day, the earliest date, and
     * where every-N-days/weeks counting starts. Times inside a DST gap move forward.
     */
    fun nextTrigger(rule: Recurrence, anchor: LocalDateTime, after: ZonedDateTime): ZonedDateTime? {
        val zone = after.zone
        val time = anchor.toLocalTime()
        val at = { date: LocalDate -> ZonedDateTime.of(date, time, zone) }
        val anchorDate = anchor.toLocalDate()
        val afterDate = after.toLocalDate()
        val start = if (anchorDate.isAfter(afterDate)) anchorDate else afterDate

        return when (rule.type) {
            RepeatType.NONE -> at(anchorDate).takeIf { it.isAfter(after) }
            RepeatType.DAILY -> stepFrom(start, 1, after, at)
            RepeatType.WEEKLY -> {
                val mask = rule.daysMask and Days.ALL
                if (mask == 0) return null
                (0L..7L).asSequence()
                    .map { start.plusDays(it) }
                    .filter { Days.contains(mask, it.dayOfWeek) }
                    .map(at)
                    .firstOrNull { it.isAfter(after) }
            }
            RepeatType.EVERY_N_DAYS, RepeatType.EVERY_N_WEEKS -> {
                val n = rule.interval.coerceAtLeast(1).toLong()
                val step = if (rule.type == RepeatType.EVERY_N_WEEKS) n * 7 else n
                val skipped = ChronoUnit.DAYS.between(anchorDate, start) / step
                stepFrom(anchorDate.plusDays(skipped * step), step, after, at)
            }
        }
    }

    /** Next ring time for an alarm; daysMask 0 means "the next time the clock shows hh:mm". */
    fun nextAlarm(hour: Int, minute: Int, daysMask: Int, after: ZonedDateTime): ZonedDateTime {
        val mask = daysMask and Days.ALL
        val rule = if (mask == 0) Recurrence(RepeatType.DAILY) else Recurrence(RepeatType.WEEKLY, mask)
        val anchor = LocalDateTime.of(after.toLocalDate(), LocalTime.of(hour, minute))
        return checkNotNull(nextTrigger(rule, anchor, after))
    }

    private fun stepFrom(
        first: LocalDate,
        step: Long,
        after: ZonedDateTime,
        at: (LocalDate) -> ZonedDateTime,
    ): ZonedDateTime {
        var date = first
        var candidate = at(date)
        while (!candidate.isAfter(after)) {
            date = date.plusDays(step)
            candidate = at(date)
        }
        return candidate
    }
}
