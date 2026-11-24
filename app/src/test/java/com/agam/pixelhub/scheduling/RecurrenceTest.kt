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

    // UK clocks go back from 02:00 BST to 01:00 GMT on 2026-10-25, so 01:00-02:00 happens twice.
    private val london = ZoneId.of("Europe/London")
    private fun secondPass(s: String) = ldt(s).atZone(london).withLaterOffsetAtOverlap()

    @Test fun oneOffInSecondPassOfRepeatedHourStillFires() =
        assertEquals(
            secondPass("2026-10-25T01:40"),
            RecurrenceCalc.nextTrigger(Recurrence.NONE, ldt("2026-10-25T01:40"), secondPass("2026-10-25T01:10")),
        )

    @Test fun oneTimeAlarmInSecondPassOfRepeatedHourRingsToday() =
        assertEquals(
            secondPass("2026-10-25T01:40"),
            RecurrenceCalc.nextAlarm(1, 40, 0, secondPass("2026-10-25T01:10"), once = true),
        )

    @Test fun dailyRuleFiresOnlyOnceInRepeatedHour() =
        // After the first-pass 01:40 has gone off, the next one is tomorrow, not 01:40 GMT today.
        assertEquals(
            ldt("2026-10-26T01:40").atZone(london),
            RecurrenceCalc.nextAlarm(1, 40, 0, ldt("2026-10-25T01:41").atZone(london)),
        )

    @Test fun savingOneOffThatFiredInFirstPassDoesNotRearmForSecondPass() =
        // Fired at 01:40 BST; a title edit (or undo, or un-tick) at 01:45 BST must not arm 01:40 GMT.
        assertNull(
            RecurrenceCalc.nextTriggerOnSave(
                Recurrence.NONE, ldt("2026-10-25T01:40"), ldt("2026-10-25T01:45").atZone(london),
                firedAnchor = ldt("2026-10-25T01:40"),
            ),
        )

    @Test fun savingNewOneOffInFirstPassStillArmsSecondPass() =
        assertEquals(
            secondPass("2026-10-25T01:40"),
            RecurrenceCalc.nextTriggerOnSave(
                Recurrence.NONE, ldt("2026-10-25T01:40"), ldt("2026-10-25T01:45").atZone(london), firedAnchor = null,
            ),
        )

    @Test fun savingFiredOneOffWithNewTimeArmsIt() =
        assertEquals(
            at("2026-11-05T18:00"),
            RecurrenceCalc.nextTriggerOnSave(
                Recurrence.NONE, ldt("2026-11-05T18:00"), at("2026-11-05T10:00"), firedAnchor = ldt("2026-11-05T09:00"),
            ),
        )

    @Test fun savingFiredOneOffAsRepeatingArmsIt() =
        assertEquals(
            at("2026-11-06T09:00"),
            RecurrenceCalc.nextTriggerOnSave(
                daily, ldt("2026-11-05T09:00"), at("2026-11-05T10:00"), firedAnchor = ldt("2026-11-05T09:00"),
            ),
        )

    @Test fun savingFiredOneOffAfterMoveWestKeepsTheInstantItFired() {
        // Fired at 10:00 IST (04:30Z); a title edit at 06:00 GMT in London must not store 10:00 GMT.
        val anchor = ldt("2026-11-05T10:00")
        val firedAt = at("2026-11-05T10:00").toInstant().toEpochMilli()
        val now = ldt("2026-11-05T06:00").atZone(london)
        val next = RecurrenceCalc.nextTriggerOnSave(Recurrence.NONE, anchor, now, firedAnchor = anchor)
        assertNull(next)
        assertEquals(firedAt, RecurrenceCalc.storedTriggerOnSave(next, anchor, now, anchor, firedAt))
    }

    @Test fun savingFiredOneOffInSecondPassKeepsFirstPassInstant() {
        val anchor = ldt("2026-10-25T01:40")
        val firstPass = anchor.atZone(london).toInstant().toEpochMilli()
        val now = ldt("2026-10-25T01:45").atZone(london)
        assertEquals(firstPass, RecurrenceCalc.storedTriggerOnSave(null, anchor, now, anchor, firstPass))
    }

    @Test fun storedTriggerIsTheArmedTimeOrThePastTime() {
        val now = at("2026-11-05T10:00")
        assertEquals(
            at("2026-11-05T18:00").toInstant().toEpochMilli(),
            RecurrenceCalc.storedTriggerOnSave(
                at("2026-11-05T18:00"), ldt("2026-11-05T18:00"), now, ldt("2026-11-05T09:00"), 1L,
            ),
        )
        // Past and never fired (or fired at another time): the time in today's zone.
        assertEquals(
            at("2026-11-05T08:00").toInstant().toEpochMilli(),
            RecurrenceCalc.storedTriggerOnSave(null, ldt("2026-11-05T08:00"), now, ldt("2026-11-05T09:00"), 1L),
        )
        assertEquals(
            at("2026-11-05T08:00").toInstant().toEpochMilli(),
            RecurrenceCalc.storedTriggerOnSave(null, ldt("2026-11-05T08:00"), now, null, null),
        )
    }

    @Test fun describeUsesFriendlyNames() {
        assertEquals("Weekdays", Recurrence(RepeatType.WEEKLY, Days.WEEKDAYS).describe())
        assertEquals("Every 3 days", Recurrence(RepeatType.EVERY_N_DAYS, interval = 3).describe())
        assertEquals("Every week", Recurrence(RepeatType.EVERY_N_WEEKS, interval = 1).describe())
        assertEquals("Once", Days.label(0))
        assertEquals("Every day", Days.label(Days.ALL))
    }
}
