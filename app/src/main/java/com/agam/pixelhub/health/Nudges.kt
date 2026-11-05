package com.agam.pixelhub.health

import com.agam.pixelhub.data.HealthNudge
import com.agam.pixelhub.data.NudgeMetric
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToLong

/** Today's totals so far, as Health Connect reports them. */
data class DailyActivity(
    val steps: Long,
    val distanceMeters: Double,
    val activeKcal: Double,
    val exerciseMinutes: Long,
    val asOf: Instant,
)

/** One day's totals, for the Health page's history chart. */
data class DayActivity(
    val date: LocalDate,
    val steps: Long,
    val distanceMeters: Double,
    val activeKcal: Double,
    val exerciseMinutes: Long,
)

sealed interface NudgeCheck {
    data class Behind(val current: Long) : NudgeCheck
    data object OnTrack : NudgeCheck
    /** Permission missing or Health Connect unreadable in the background. */
    data object NoAccess : NudgeCheck
}

/** Pure logic for nudges: what to compare and what to say. */
object NudgeMath {
    /** Typical walking pace, used for the "N-minute walk" hint. */
    private const val STEPS_PER_MINUTE = 100

    fun valueOf(metric: NudgeMetric, a: DailyActivity): Long = when (metric) {
        NudgeMetric.STEPS -> a.steps
        NudgeMetric.EXERCISE_MINUTES -> a.exerciseMinutes
        NudgeMetric.ACTIVE_CALORIES -> a.activeKcal.roundToLong()
    }

    fun evaluate(nudge: HealthNudge, a: DailyActivity): NudgeCheck {
        val current = valueOf(nudge.metric, a)
        return if (current < nudge.goal) NudgeCheck.Behind(current) else NudgeCheck.OnTrack
    }

    fun unit(metric: NudgeMetric): String = when (metric) {
        NudgeMetric.STEPS -> "steps"
        NudgeMetric.EXERCISE_MINUTES -> "active minutes"
        NudgeMetric.ACTIVE_CALORIES -> "active kcal"
    }

    /** Title and body for the nudge notification. */
    fun message(metric: NudgeMetric, current: Long, goal: Long): Pair<String, String> {
        val remaining = (goal - current).coerceAtLeast(0)
        val title = "${grouped(current)} of ${grouped(goal)} ${unit(metric)} so far"
        val body = when (metric) {
            NudgeMetric.STEPS -> {
                val minutes = ceil(remaining / STEPS_PER_MINUTE.toDouble()).toLong().coerceAtLeast(1)
                "${grouped(remaining)} to go. A $minutes-minute walk would get you there."
            }
            NudgeMetric.EXERCISE_MINUTES -> "$remaining more active minutes to hit today's goal."
            NudgeMetric.ACTIVE_CALORIES -> "${grouped(remaining)} kcal to go for today's goal."
        }
        return title to body
    }

    /** One-line description for lists: "Steps under 6,000 at 18:00". */
    fun describe(nudge: HealthNudge, time: String): String =
        "${unit(nudge.metric).replaceFirstChar { it.uppercase() }} under ${grouped(nudge.goal)} at $time"

    private fun grouped(n: Long): String = String.format(Locale.getDefault(), "%,d", n)
}
