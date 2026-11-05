package com.agam.pixelhub.ai

import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.scheduling.Recurrence
import com.agam.pixelhub.scheduling.RepeatType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import kotlin.math.roundToLong

/** Text being taken apart: each rule that matches removes its words, and what's left is the title. */
internal class Scan(text: String) {
    var rest: String = " $text "
        private set

    /** Removes and returns the first match of [re], or null. */
    fun take(re: Regex): MatchResult? {
        val m = re.find(rest) ?: return null
        rest = rest.substring(0, m.range.first) + " " + rest.substring(m.range.last + 1)
        return m
    }

    /** Removes the characters at [range] (from a match against [rest]). */
    fun cut(range: IntRange) {
        rest = rest.substring(0, range.first) + " " + rest.substring(range.last + 1)
    }

    /** Removes every match of [re]. */
    fun takeAll(re: Regex) {
        rest = re.replace(rest, " ")
    }
}

internal enum class DayPart(val defaultHour: Int) { MORNING(9), AFTERNOON(14), EVENING(18), NIGHT(21) }

/** A time as written: [hour] 0–23, [meridiem] 'a', 'p' or null when not said. */
internal data class ClockTime(val hour: Int, val minute: Int, val meridiem: Char? = null, val part: DayPart? = null)

/** A resolved time of day. [certain] is false for "at 6" with nothing saying morning or evening. */
internal data class HourMinute(val hour: Int, val minute: Int, val certain: Boolean)

internal data class DateHit(val date: LocalDate, val part: DayPart? = null, val fromWeekday: Boolean = false)

/** "in 20 minutes" ([minutes]) or "in 3 days" ([days]). */
internal data class Relative(val minutes: Long? = null, val days: Long? = null)

/** A repeat rule. WEEKLY with [mask] 0 means "on the weekday of the date". */
internal data class RepeatHit(val type: RepeatType, val mask: Int = 0, val interval: Int = 1, val part: DayPart? = null) {
    fun toRecurrence(day: DayOfWeek): Recurrence = when (type) {
        RepeatType.WEEKLY -> Recurrence(RepeatType.WEEKLY, daysMask = if (mask != 0) mask else Days.bit(day))
        RepeatType.EVERY_N_DAYS, RepeatType.EVERY_N_WEEKS -> Recurrence(type, interval = interval)
        else -> Recurrence(type)
    }

    /** Alarms only repeat on weekdays, so every-N rules become every day / that weekday. */
    fun toAlarmMask(day: DayOfWeek): Int = when (type) {
        RepeatType.DAILY, RepeatType.EVERY_N_DAYS -> Days.ALL
        RepeatType.WEEKLY -> if (mask != 0) mask else Days.bit(day)
        RepeatType.EVERY_N_WEEKS -> Days.bit(day)
        RepeatType.NONE -> 0
    }
}

internal object DateTimeRules {
    private const val DAYS_FULL = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
    private const val DAYS_ABBR = "mon|tues|tue|weds|wed|thurs|thur|thu|fri|sat|sun"
    private const val DAY = "(?:$DAYS_FULL|$DAYS_ABBR)"
    private const val MONTHS =
        "january|february|march|april|may|june|july|august|september|october|november|december" +
            "|jan|feb|mar|apr|jun|jul|aug|sept|sep|oct|nov|dec"
    private const val MER = """(am|pm|a\.?m\.?|p\.?m\.?)"""
    // Decimals too: "in 1.5 hours" was left in the title with the reminder at the default time.
    private const val NUM = """\d+(?:\.\d+)?|an?|one|ek|two|do|three|teen|four|char|chaar|five|paanch|half\s+an?|half|aadhe|aadha|adha|adhe"""
    private const val UNIT = """minutes?|mins?|m|hours?|hrs?|h|days?|weeks?"""

    fun dayOf(word: String): DayOfWeek? = when (word.take(3)) {
        "mon" -> DayOfWeek.MONDAY
        "tue" -> DayOfWeek.TUESDAY
        "wed" -> DayOfWeek.WEDNESDAY
        "thu" -> DayOfWeek.THURSDAY
        "fri" -> DayOfWeek.FRIDAY
        "sat" -> DayOfWeek.SATURDAY
        "sun" -> DayOfWeek.SUNDAY
        else -> null
    }

    private fun partOf(word: String): DayPart? = when (word) {
        "morning" -> DayPart.MORNING
        "afternoon" -> DayPart.AFTERNOON
        "evening" -> DayPart.EVENING
        "night", "tonight" -> DayPart.NIGHT
        else -> null
    }

    // ---- Repeats ------------------------------------------------------------------------

    private val EVERY_N = Regex("""\bevery\s+(\d+)\s+(days?|weeks?)\b""")
    private val EVERY_OTHER = Regex("""\b(?:every\s+other\s+day|every\s+alternate\s+days?|alternate\s+days?)\b""")
    private val EVERY_PART = Regex("""\bevery\s+(morning|afternoon|evening|night)\b""")
    private val DAILY = Regex("""\b(?:every\s+days?|daily|each\s+day)\b""")
    private val WEEKDAYS = Regex("""\b(?:every\s+weekday|(?:on\s+)?weekdays)\b""")
    private val WEEKENDS = Regex("""\b(?:every\s+weekend|(?:on\s+)?weekends)\b""")
    private val DAY_LIST = Regex("""\bevery\s+($DAY(?:\s*(?:,|and|&)\s*$DAY)*)\b""")
    private val PLURAL_DAYS = Regex("""\b(?:on\s+)?((?:$DAYS_FULL)s(?:\s*(?:,|and|&)\s*(?:$DAYS_FULL)s?)*)\b""")
    private val WEEKLY = Regex("""\b(?:every\s+weeks?|each\s+week)\b""")
    private val ONE_DAY = Regex(DAY)

    private fun maskOf(list: String): Int =
        ONE_DAY.findAll(list).mapNotNull { dayOf(it.value) }.fold(0) { m, d -> m or Days.bit(d) }

    fun repeat(sc: Scan): RepeatHit? {
        sc.take(EVERY_N)?.let { m ->
            val n = m.groupValues[1].toInt()
            return if (m.groupValues[2].startsWith("day")) {
                if (n <= 1) RepeatHit(RepeatType.DAILY) else RepeatHit(RepeatType.EVERY_N_DAYS, interval = n.coerceAtMost(365))
            } else {
                if (n <= 1) RepeatHit(RepeatType.WEEKLY) else RepeatHit(RepeatType.EVERY_N_WEEKS, interval = n.coerceAtMost(52))
            }
        }
        if (sc.take(EVERY_OTHER) != null) return RepeatHit(RepeatType.EVERY_N_DAYS, interval = 2)
        sc.take(EVERY_PART)?.let { return RepeatHit(RepeatType.DAILY, part = partOf(it.groupValues[1])) }
        if (sc.take(DAILY) != null) return RepeatHit(RepeatType.DAILY)
        if (sc.take(WEEKDAYS) != null) return RepeatHit(RepeatType.WEEKLY, mask = Days.WEEKDAYS)
        if (sc.take(WEEKENDS) != null) return RepeatHit(RepeatType.WEEKLY, mask = Days.WEEKENDS)
        sc.take(DAY_LIST)?.let { return RepeatHit(RepeatType.WEEKLY, mask = maskOf(it.groupValues[1])) }
        sc.take(PLURAL_DAYS)?.let { return RepeatHit(RepeatType.WEEKLY, mask = maskOf(it.groupValues[1])) }
        if (sc.take(WEEKLY) != null) return RepeatHit(RepeatType.WEEKLY)
        return null
    }

    // ---- "in 2 hours", "10 minutes later" -------------------------------------------------

    private val REL_PRE = Regex("""\b(?:in|after)\s+($NUM)\s*($UNIT)\b""")
    private val REL_POST = Regex("""\b($NUM)\s*($UNIT)\s+(?:later|from\s+now|mein|me|main)\b""")

    private fun amountOf(word: String): Double? = when {
        word.first().isDigit() -> word.toDoubleOrNull()
        word.startsWith("half") || word.startsWith("aadh") || word.startsWith("adh") -> 0.5
        else -> when (word) {
            "a", "an", "one", "ek" -> 1.0
            "two", "do" -> 2.0
            "three", "teen" -> 3.0
            "four", "char", "chaar" -> 4.0
            "five", "paanch" -> 5.0
            else -> null
        }
    }

    fun relative(sc: Scan): Relative? {
        val m = sc.take(REL_PRE) ?: sc.take(REL_POST) ?: return null
        val n = amountOf(m.groupValues[1]) ?: return null
        val unit = m.groupValues[2]
        return when {
            unit.startsWith("d") -> Relative(days = n.roundToLong().coerceAtLeast(1))
            unit.startsWith("w") -> Relative(days = (n * 7).roundToLong().coerceAtLeast(1))
            unit.startsWith("h") -> Relative(minutes = (n * 60).roundToLong().coerceAtLeast(1))
            else -> Relative(minutes = n.roundToLong().coerceAtLeast(1))
        }
    }

    // ---- Dates --------------------------------------------------------------------------

    private val DAY_AFTER = Regex("""\b(?:day\s+after\s+tomorrow|parso|parson)\b""")
    private val DAY_BEFORE = Regex("""\bday\s+before\s+yesterday\b""")
    private val TOMORROW = Regex("""\btomorrow\b""")
    private val YESTERDAY = Regex("""\byesterday\b""")
    private val KAL = Regex("""\bkal\b""")
    private val TONIGHT = Regex("""\btonight\b""")
    private val TODAY = Regex("""\btoday\b""")
    private val WEEKDAY = Regex("""\b(?:(on|next|this|coming|last)\s+)?($DAYS_FULL)\b""")
    private val WEEKDAY_ABBR = Regex("""\b(on|next|this|coming|last)\s+($DAYS_ABBR)\b""")
    private val DAY_MONTH = Regex("""\b(?:on\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($MONTHS)\b""")
    private val MONTH_DAY = Regex("""\b(?:on\s+)?($MONTHS)\s+(\d{1,2})(?:st|nd|rd|th)?\b""")
    private val NUMERIC = Regex("""\b(?:on\s+)?(\d{1,2})[/-](\d{1,2})(?:[/-](\d{2,4}))?\b""")
    private val ORDINAL = Regex("""\b(?:on\s+)?(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)\b""")

    private fun monthOf(word: String): Month? = Month.entries.firstOrNull { it.name.lowercase().startsWith(word.take(3)) }

    /**
     * The date the text names, removed from [sc]. With [past] ("spent 200 kal"), "kal" and
     * "parso" look back and bare dates fall in the past; otherwise they look ahead.
     */
    fun date(sc: Scan, today: LocalDate, past: Boolean): DateHit? {
        if (sc.take(DAY_BEFORE) != null) return DateHit(today.minusDays(2))
        if (sc.take(DAY_AFTER) != null) return DateHit(if (past) today.minusDays(2) else today.plusDays(2))
        if (sc.take(TOMORROW) != null) return DateHit(today.plusDays(1))
        if (sc.take(YESTERDAY) != null) return DateHit(today.minusDays(1))
        if (sc.take(KAL) != null) return DateHit(if (past) today.minusDays(1) else today.plusDays(1))
        if (sc.take(TONIGHT) != null) return DateHit(today, part = DayPart.NIGHT)
        if (sc.take(TODAY) != null) return DateHit(today)
        (sc.take(WEEKDAY) ?: sc.take(WEEKDAY_ABBR))?.let { m ->
            val day = dayOf(m.groupValues[2]) ?: return null
            val back = m.groupValues[1] == "last" || past
            val date = if (back) {
                today.minusDays(((today.dayOfWeek.value - day.value + 7) % 7).toLong())
                    .let { if (m.groupValues[1] == "last" && it == today) it.minusDays(7) else it }
            } else {
                val ahead = (day.value - today.dayOfWeek.value + 7) % 7
                today.plusDays(if (ahead == 0 && m.groupValues[1] != "this") 7L else ahead.toLong())
            }
            return DateHit(date, fromWeekday = true)
        }
        sc.take(DAY_MONTH)?.let { m -> return dayMonth(m.groupValues[1].toInt(), monthOf(m.groupValues[2]), null, today, past) }
        sc.take(MONTH_DAY)?.let { m -> return dayMonth(m.groupValues[2].toInt(), monthOf(m.groupValues[1]), null, today, past) }
        sc.take(NUMERIC)?.let { m ->
            val month = m.groupValues[2].toInt().takeIf { it in 1..12 }?.let { Month.of(it) }
            val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            return dayMonth(m.groupValues[1].toInt(), month, year, today, past)
        }
        sc.take(ORDINAL)?.let { m ->
            val day = m.groupValues[1].toInt()
            // The nearest month that has this day, ahead (or behind, for spending): "the 31st"
            // said in November is 31 December, not dropped; "the 30th" in March looking back is
            // 30 January, not a clamped 28 February.
            val date = generateSequence(today.withDayOfMonth(1)) { if (past) it.minusMonths(1) else it.plusMonths(1) }
                .take(13)
                .mapNotNull { runCatching { it.withDayOfMonth(day) }.getOrNull() }
                .firstOrNull { if (past) !it.isAfter(today) else !it.isBefore(today) }
                ?: return null
            return DateHit(date)
        }
        return null
    }

    private fun dayMonth(day: Int, month: Month?, year: Int?, today: LocalDate, past: Boolean): DateHit? {
        month ?: return null
        val date = runCatching { LocalDate.of(year ?: today.year, month, day) }.getOrNull() ?: return null
        return DateHit(
            when {
                year != null -> date
                past && date.isAfter(today) -> date.minusYears(1)
                !past && date.isBefore(today) -> date.plusYears(1)
                else -> date
            },
        )
    }

    // ---- Times --------------------------------------------------------------------------

    private val FRACTION = Regex("""\b(?:at\s+)?(saadhe|sadhe|sade|saade|sava|savaa|sawa|paune|pone)\s+(\d{1,2})(?:\s*oclock)?\b""")
    private val DEDH = Regex("""\b(?:at\s+)?(dedh|derh|dhai|dhaai|adhai)\s+oclock\b""")
    private val NOON = Regex("""\b(?:at\s+)?(noon|midday|midnight)\b""")
    private val HHMM = Regex("""(?:\bat\s+|@\s*|\b)(\d{1,2})[:.](\d{2})\s*$MER?(?:\s*oclock)?(?![a-z\d])""")
    private val H_MER = Regex("""(?:\bat\s+|@\s*|\b)(\d{1,2})\s*$MER(?![a-z])""")
    private val H_OCLOCK = Regex("""(?:\bat\s+)?\b(\d{1,2})\s*oclock\b""")
    private val PART_H = Regex("""\b(morning|afternoon|evening|night)\s+(?:at\s+)?(\d{1,2})\b(?![:.]\d)(?!\s*(?:minutes|hours|days|weeks|rs))""")
    private val H_PART = Regex("""\b(\d{1,2})\s+(?:in\s+the\s+|at\s+)?(morning|afternoon|evening|night)\b""")
    private val AT_H = Regex("""\bat\s+(\d{1,2})\b(?![:.]\d)(?!\s*(?:%|rs|rupees|kg|km|minutes|hours|days|weeks|percent))""")
    private val PART = Regex("""\b(?:in\s+the\s+|this\s+|at\s+)?(morning|afternoon|evening|night)(?:\s+(?:ko|mein|me))?\b""")

    private fun meridiemOf(text: String): Char? = text.firstOrNull()?.takeIf { it == 'a' || it == 'p' }

    /** The time of day the text names, removed from [sc]. */
    fun time(sc: Scan): ClockTime? {
        sc.take(FRACTION)?.let { m ->
            val h = m.groupValues[2].toInt()
            return when (m.groupValues[1].take(2)) {
                "pa", "po" -> ClockTime(if (h == 1) 12 else h - 1, 45)
                "sa" -> if (m.groupValues[1].startsWith("sav") || m.groupValues[1] == "sawa") ClockTime(h, 15) else ClockTime(h, 30)
                else -> ClockTime(h, 30)
            }
        }
        sc.take(DEDH)?.let { m -> return if (m.groupValues[1].startsWith("de")) ClockTime(1, 30) else ClockTime(2, 30) }
        sc.take(NOON)?.let { m -> return if (m.groupValues[1] == "midnight") ClockTime(0, 0, 'a') else ClockTime(12, 0, 'p') }
        sc.take(HHMM)?.let { m -> return ClockTime(m.groupValues[1].toInt(), m.groupValues[2].toInt(), meridiemOf(m.groupValues[3])) }
        sc.take(H_MER)?.let { m -> return ClockTime(m.groupValues[1].toInt(), 0, meridiemOf(m.groupValues[2])) }
        sc.take(H_OCLOCK)?.let { m -> return ClockTime(m.groupValues[1].toInt(), 0) }
        sc.take(PART_H)?.let { m -> return ClockTime(m.groupValues[2].toInt(), 0, part = partOf(m.groupValues[1])) }
        sc.take(H_PART)?.let { m -> return ClockTime(m.groupValues[1].toInt(), 0, part = partOf(m.groupValues[2])) }
        sc.take(AT_H)?.let { m -> return ClockTime(m.groupValues[1].toInt(), 0) }
        return null
    }

    /** "morning", "in the evening", "shaam ko", removed from [sc]. */
    fun part(sc: Scan): DayPart? = sc.take(PART)?.let { partOf(it.groupValues[1]) }

    /** 24-hour time from what was written, or null if it isn't a real time ("at 27"). */
    fun resolve(t: ClockTime, part: DayPart?): HourMinute? {
        val h = t.hour
        if (h !in 0..23 || t.minute !in 0..59) return null
        if (h == 0 || h >= 13) return HourMinute(h, t.minute, certain = true)
        if (t.meridiem == 'p') return HourMinute(if (h == 12) 12 else h + 12, t.minute, true)
        if (t.meridiem == 'a') return HourMinute(if (h == 12) 0 else h, t.minute, true)
        return when (t.part ?: part) {
            DayPart.MORNING -> HourMinute(h, t.minute, true)
            DayPart.AFTERNOON -> HourMinute(if (h in 1..6) h + 12 else h, t.minute, true)
            DayPart.EVENING -> HourMinute(if (h == 12) 12 else h + 12, t.minute, true)
            DayPart.NIGHT -> HourMinute(
                when (h) {
                    12 -> 0
                    in 6..11 -> h + 12
                    else -> h
                },
                t.minute, true,
            )
            null -> HourMinute(h, t.minute, certain = false)
        }
    }

    /** "at 6" on a named day: 7–11 is morning, 12 is noon, 1–6 is afternoon or evening. */
    fun likelyHour(h: Int): Int = if (h in 1..6) h + 12 else h

    /** "at 6" with no day: the next 6:00 or 18:00 after [now]. */
    fun nextOccurrence(h: Int, minute: Int, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate()
        val candidates = if (h == 12) {
            listOf(today.atTime(12, minute), today.plusDays(1).atTime(12, minute))
        } else {
            listOf(today.atTime(h, minute), today.atTime(h + 12, minute), today.plusDays(1).atTime(h, minute))
        }
        return candidates.first { it.isAfter(now) }
    }
}
