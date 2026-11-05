package com.agam.pixelhub.calendar

import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.scheduling.Recurrence
import com.agam.pixelhub.scheduling.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class CalendarRulesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun millis(s: String) = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()

    @Test fun oneOffHasNoRule() = assertNull(CalendarRules.rrule(Recurrence.NONE))

    @Test fun daily() = assertEquals("FREQ=DAILY", CalendarRules.rrule(Recurrence(RepeatType.DAILY)))

    @Test fun weeklyListsDaysMondayFirst() = assertEquals(
        "FREQ=WEEKLY;BYDAY=MO,WE,FR",
        CalendarRules.rrule(
            Recurrence(
                RepeatType.WEEKLY,
                Days.bit(DayOfWeek.FRIDAY) or Days.bit(DayOfWeek.MONDAY) or Days.bit(DayOfWeek.WEDNESDAY),
            ),
        ),
    )

    @Test fun weekdays() =
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", CalendarRules.rrule(Recurrence(RepeatType.WEEKLY, Days.WEEKDAYS)))

    @Test fun everyThreeDays() =
        assertEquals("FREQ=DAILY;INTERVAL=3", CalendarRules.rrule(Recurrence(RepeatType.EVERY_N_DAYS, interval = 3)))

    @Test fun everyTwoWeeks() =
        assertEquals("FREQ=WEEKLY;INTERVAL=2", CalendarRules.rrule(Recurrence(RepeatType.EVERY_N_WEEKS, interval = 2)))

    @Test fun everyOneWeekHasNoInterval() =
        assertEquals("FREQ=WEEKLY", CalendarRules.rrule(Recurrence(RepeatType.EVERY_N_WEEKS, interval = 1)))

    @Test fun oneOffStartsAtAnchor() = assertEquals(
        millis("2026-11-05T18:00"),
        CalendarRules.firstOccurrence(LocalDateTime.parse("2026-11-05T18:00"), Recurrence.NONE, zone),
    )

    @Test fun weeklyStartsOnFirstSelectedDay() {
        // Anchor Sunday 2026-11-08, repeats Mon/Wed → first event Monday 2026-11-09.
        val monWed = Recurrence(RepeatType.WEEKLY, Days.bit(DayOfWeek.MONDAY) or Days.bit(DayOfWeek.WEDNESDAY))
        assertEquals(
            millis("2026-11-09T07:00"),
            CalendarRules.firstOccurrence(LocalDateTime.parse("2026-11-08T07:00"), monWed, zone),
        )
    }

    @Test fun repeatingStartsAtAnchorEvenIfInThePast() = assertEquals(
        millis("2026-01-01T08:00"),
        CalendarRules.firstOccurrence(LocalDateTime.parse("2026-01-01T08:00"), Recurrence(RepeatType.DAILY), zone),
    )

    @Test fun descriptionKeepsNotesAndAddsFooter() {
        assertEquals("Created by PixelHub", CalendarRules.description(""))
        assertEquals("Bring ID\n\nCreated by PixelHub", CalendarRules.description("Bring ID"))
    }
}
