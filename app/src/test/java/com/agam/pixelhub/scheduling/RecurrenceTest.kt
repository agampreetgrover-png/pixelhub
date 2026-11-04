package com.agam.pixelhub.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurrenceTest {
    private val india = ZoneId.of("Asia/Kolkata") // no DST
    private val newYork = ZoneId.of("America/New_York")

    private fun at(s: String, zone: ZoneId = india): ZonedDateTime = LocalDateTime.parse(s).atZone(zone)
    private fun ldt(s: String): LocalDateTime = LocalDateTime.parse(s)
    private fun next(rule: Recurrence, anchor: String, after: String, zone: ZoneId = india) =
        RecurrenceCalc.nextTrigger(rule, ldt(anchor), at(after, zone))

    private val daily = Recurrence(RepeatType.DAILY)
    private val monWedFri = Recurrence(
        RepeatType.WEEKLY,
        Days.bit(DayOfWeek.MONDAY) or Days.bit(DayOfWeek.WEDNESDAY) or Days.bit(DayOfWeek.FRIDAY),
    )

    @Test fun oneOffInFutureFiresAtAnchor() =
        assertEquals(at("2026-11-05T18:00"), next(Recurrence.NONE, "2026-11-05T18:00", "2026-11-05T10:00"))

    @Test fun oneOffInPastNeverFires() =
        assertNull(next(Recurrence.NONE, "2026-11-05T09:00", "2026-11-05T10:00"))

    @Test fun oneOffExactlyNowDoesNotFireAgain() =
        assertNull(next(Recurrence.NONE, "2026-11-05T10:00", "2026-11-05T10:00"))

    @Test fun dailyLaterTodayStaysToday() =
        assertEquals(at("2026-11-05T07:00"), next(daily, "2026-11-01T07:00", "2026-11-05T06:00"))

    @Test fun dailyRollsToTomorrowOnceTimePassed() =
        assertEquals(at("2026-11-06T07:00"), next(daily, "2026-11-01T07:00", "2026-11-05T08:00"))

    @Test fun dailyDoesNotStartBeforeAnchorDate() =
        assertEquals(at("2026-11-10T07:00"), next(daily, "2026-11-10T07:00", "2026-11-05T08:00"))

    @Test fun dailyCrossesYearBoundary() =
        assertEquals(at("2027-01-01T07:00"), next(daily, "2026-12-01T07:00", "2026-12-31T23:00"))

    // 2026-11-06 is a Friday, 2026-11-09 a Monday.
    @Test fun weeklyJumpsToNextSelectedDay() =
        assertEquals(at("2026-11-09T07:00"), next(monWedFri, "2026-11-01T07:00", "2026-11-06T08:00"))

    @Test fun weeklySameDayLaterToday() =
        assertEquals(at("2026-11-06T07:00"), next(monWedFri, "2026-11-01T07:00", "2026-11-06T06:00"))

    @Test fun weeklySingleDayWrapsAFullWeek() {
        val thursdays = Recurrence(RepeatType.WEEKLY, Days.bit(DayOfWeek.THURSDAY))
        assertEquals(at("2026-11-12T07:00"), next(thursdays, "2026-11-01T07:00", "2026-11-05T08:00"))
    }

    @Test fun weeklyWithNoDaysNeverFires() =
        assertNull(next(Recurrence(RepeatType.WEEKLY, 0), "2026-11-01T07:00", "2026-11-05T08:00"))

    @Test fun everyThreeDaysKeepsCadenceFromAnchor() {
        // Nov 1, 4, 7, ... The Nov 4 slot has passed.
        val rule = Recurrence(RepeatType.EVERY_N_DAYS, interval = 3)
        assertEquals(at("2026-11-07T09:00"), next(rule, "2026-11-01T09:00", "2026-11-05T10:00"))
    }

    @Test fun everyThreeDaysSameDayStillAhead() {
        val rule = Recurrence(RepeatType.EVERY_N_DAYS, interval = 3)
        assertEquals(at("2026-11-04T09:00"), next(rule, "2026-11-01T09:00", "2026-11-04T08:00"))
    }

    @Test fun everyTwoWeeks() {
        val rule = Recurrence(RepeatType.EVERY_N_WEEKS, interval = 2)
        assertEquals(at("2026-11-16T08:00"), next(rule, "2026-11-02T08:00", "2026-11-10T12:00"))
    }

    @Test fun timeInsideDstGapMovesForward() {
        // US clocks jump from 02:00 to 03:00 on 2026-03-08.
        val result = next(daily, "2026-03-01T02:30", "2026-03-07T03:00", newYork)!!
        assertEquals(ldt("2026-03-08T03:30"), result.toLocalDateTime())
    }

    @Test fun oneShotAlarmRingsAtNextOccurrence() =
        assertEquals(at("2026-11-06T07:00"), RecurrenceCalc.nextAlarm(7, 0, 0, at("2026-11-05T08:00")))

    @Test fun weekdayAlarmSkipsWeekend() =
        assertEquals(at("2026-11-09T06:30"), RecurrenceCalc.nextAlarm(6, 30, Days.WEEKDAYS, at("2026-11-06T08:00")))

    @Test fun describeUsesFriendlyNames() {
        assertEquals("Weekdays", Recurrence(RepeatType.WEEKLY, Days.WEEKDAYS).describe())
        assertEquals("Every 3 days", Recurrence(RepeatType.EVERY_N_DAYS, interval = 3).describe())
        assertEquals("Every week", Recurrence(RepeatType.EVERY_N_WEEKS, interval = 1).describe())
        assertEquals("Once", Days.label(0))
        assertEquals("Every day", Days.label(Days.ALL))
    }
}
