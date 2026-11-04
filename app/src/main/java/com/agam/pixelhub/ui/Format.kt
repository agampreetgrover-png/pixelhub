package com.agam.pixelhub.ui

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Human-friendly date/time strings that follow the phone's 12/24-hour setting. */
object Fmt {
    private fun timePattern(context: Context) = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"

    fun time(context: Context, t: LocalTime): String =
        t.format(DateTimeFormatter.ofPattern(timePattern(context), Locale.getDefault()))

    /** "Today, 18:00", "Tomorrow, 9:00 AM", "Thu 12 Nov, 09:00". */
    fun whenText(context: Context, millis: Long): String {
        val zdt = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        val date = zdt.toLocalDate()
        val today = LocalDate.now()
        val day = when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(
                DateTimeFormatter.ofPattern(
                    if (date.year == today.year) "EEE d MMM" else "EEE d MMM yyyy",
                    Locale.getDefault(),
                ),
            )
        }
        return "$day, ${time(context, zdt.toLocalTime())}"
    }

    fun whenText(context: Context, at: LocalDateTime): String =
        whenText(context, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())

    /** "2d 3h", "7h 5m", "12m" (rounded up to the next minute). */
    fun untilText(targetMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val totalMinutes = ((targetMillis - nowMillis + 59_999) / 60_000).coerceAtLeast(0)
        val d = totalMinutes / 1440
        val h = totalMinutes % 1440 / 60
        val m = totalMinutes % 60
        return buildList {
            if (d > 0) add("${d}d")
            if (h > 0) add("${h}h")
            if (m > 0 || isEmpty()) add("${m}m")
        }.joinToString(" ")
    }
}
