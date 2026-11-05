package com.agam.pixelhub.health

import com.agam.pixelhub.data.HealthNudge
import com.agam.pixelhub.data.HealthNudgeRepository
import com.agam.pixelhub.data.NudgeMetric
import com.agam.pixelhub.scheduling.Days
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class NudgeMathTest {
    private val activity = DailyActivity(
        steps = 3_412, distanceMeters = 2_500.0, activeKcal = 211.6, exerciseMinutes = 18, asOf = Instant.EPOCH,
    )

    private fun nudge(metric: NudgeMetric, goal: Long) = HealthNudge(metric = metric, goal = goal, hour = 18, minute = 0)

    @Test fun behindStepGoal() =
        assertEquals(NudgeCheck.Behind(3_412), NudgeMath.evaluate(nudge(NudgeMetric.STEPS, 6_000), activity))

    @Test fun exactlyAtGoalIsOnTrack() =
        assertEquals(NudgeCheck.OnTrack, NudgeMath.evaluate(nudge(NudgeMetric.STEPS, 3_412), activity))

    @Test fun exerciseMinutesMetric() =
        assertEquals(NudgeCheck.Behind(18), NudgeMath.evaluate(nudge(NudgeMetric.EXERCISE_MINUTES, 30), activity))

    @Test fun caloriesAreRounded() =
        assertEquals(212L, NudgeMath.valueOf(NudgeMetric.ACTIVE_CALORIES, activity))

    @Test fun stepMessageSuggestsWalkLength() {
        Locale.setDefault(Locale.US)
        val (title, body) = NudgeMath.message(NudgeMetric.STEPS, 3_412, 6_000)
        assertEquals("3,412 of 6,000 steps so far", title)
        // 2,588 steps at ~100/min → 26 minutes
        assertEquals("2,588 to go. A 26-minute walk would get you there.", body)
    }

    @Test fun nudgeCheckTimeRepeatsOnChosenDays() {
        val zone = ZoneId.of("Asia/Kolkata")
        val weekdayNudge = HealthNudge(goal = 6_000, hour = 18, minute = 0, daysMask = Days.WEEKDAYS)
        // Friday 2026-11-06 19:00 → next check Monday 18:00
        val after = LocalDateTime.parse("2026-11-06T19:00").atZone(zone)
        assertEquals(
            LocalDateTime.parse("2026-11-09T18:00").atZone(zone),
            HealthNudgeRepository.nextTrigger(weekdayNudge, after),
        )
    }

    @Test fun everyDayWhenNoDaysPicked() {
        val zone = ZoneId.of("Asia/Kolkata")
        val daily = HealthNudge(goal = 6_000, hour = 18, minute = 0)
        val after = LocalDateTime.parse("2026-11-07T19:00").atZone(zone) // Saturday
        assertEquals(LocalDateTime.parse("2026-11-08T18:00").atZone(zone), HealthNudgeRepository.nextTrigger(daily, after))
    }
}
