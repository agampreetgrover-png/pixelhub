package com.agam.pixelhub.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class CalendarEventsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val la = ZoneId.of("America/Los_Angeles")
    private val dash = "–"
    private val dot = "•"

    private fun at(s: String, z: ZoneId = zone) = LocalDateTime.parse(s).atZone(z).toInstant().toEpochMilli()

    private fun event(
        begin: String,
        end: String,
        title: String = "Event",
        allDay: Boolean = false,
        id: Long = 1,
    ) = CalendarEvent(id, title, at(begin), at(end), allDay, 0, "Work", "")

    private fun allDay(from: LocalDate, days: Long, title: String = "Trip") = CalendarEvent(
        9, title,
        from.atStartOfDay(zone).toInstant().toEpochMilli(),
        from.plusDays(days).atStartOfDay(zone).toInstant().toEpochMilli(),
        true, 0, "Personal", "",
    )

    // --- duplicates across accounts -------------------------------------------------------

    @Test fun sameEventFromSeveralCalendarsShowsOnce() {
        val diwali = LocalDate.of(2026, 11, 8)
        val list = listOf(
            allDay(diwali, 1, "Diwali/Deepavali").copy(eventId = 9),
            allDay(diwali, 1, "Diwali/Deepavali").copy(eventId = 109),
            allDay(diwali, 1, "diwali/deepavali ").copy(eventId = 211),
            allDay(diwali, 1, "Naraka Chaturdasi").copy(eventId = 82),
        )
        val kept = CalendarAgenda.withoutDuplicates(list)
        assertEquals(listOf(9L, 82L), kept.map { it.eventId })
    }

    @Test fun sameTitleAtDifferentTimesIsKept() {
        val a = event("2026-11-06T09:00", "2026-11-06T10:00", title = "Lab", id = 1)
        val b = event("2026-11-06T10:00", "2026-11-06T11:00", title = "Lab", id = 2)
        assertEquals(2, CalendarAgenda.withoutDuplicates(listOf(a, b)).size)
    }

    // --- all-day conversion ---------------------------------------------------------------

    @Test fun allDayLandsOnItsDateWestOfUtc() {
        val utc = LocalDate.of(2026, 11, 6).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val local = CalendarAgenda.allDayUtcToLocal(utc, la)
        assertEquals(LocalDate.of(2026, 11, 6), CalendarAgenda.date(local, la))
        assertEquals(LocalDate.of(2026, 11, 6).atStartOfDay(la).toInstant().toEpochMilli(), local)
    }

    @Test fun allDayLandsOnItsDateEastOfUtc() {
        val utc = LocalDate.of(2026, 11, 6).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 6), CalendarAgenda.date(CalendarAgenda.allDayUtcToLocal(utc, zone), zone))
    }

    @Test fun allDayRoundTrips() {
        val utc = LocalDate.of(2026, 11, 6).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(utc, CalendarAgenda.localToAllDayUtc(CalendarAgenda.allDayUtcToLocal(utc, la), la))
    }

    // --- PixelHub's own events ------------------------------------------------------------

    @Test fun recognisesPixelHubEvents() {
        assertTrue(CalendarAgenda.isFromPixelHub(CalendarRules.description("")))
        assertTrue(CalendarAgenda.isFromPixelHub(CalendarRules.description("Take the pills")))
        assertFalse(CalendarAgenda.isFromPixelHub("Lunch with Sam"))
        assertFalse(CalendarAgenda.isFromPixelHub(null))
    }

    // --- grouping -------------------------------------------------------------------------

    @Test fun groupsByLocalDateInOrder() {
        val a = event("2026-11-06T14:00", "2026-11-06T15:00", "Late")
        val b = event("2026-11-06T09:00", "2026-11-06T10:00", "Early")
        val c = event("2026-11-05T18:00", "2026-11-05T19:00", "Yesterday-ish")
        val days = CalendarAgenda.groupByDay(listOf(a, b, c), zone, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 30))
        assertEquals(listOf(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6)), days.keys.toList())
        assertEquals(listOf("Early", "Late"), days.getValue(LocalDate.of(2026, 11, 6)).map { it.title })
    }

    @Test fun multiDayEventAppearsOnEachDayWithinTheWindow() {
        val trip = allDay(LocalDate.of(2026, 11, 4), 4) // 4th to 7th
        val days = CalendarAgenda.groupByDay(listOf(trip), zone, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6))
        assertEquals(listOf(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6)), days.keys.toList())
    }

    @Test fun allDayEventsComeFirst() {
        val meeting = event("2026-11-06T08:00", "2026-11-06T09:00", "Meeting")
        val holiday = allDay(LocalDate.of(2026, 11, 6), 1, "Holiday")
        val day = CalendarAgenda.groupByDay(listOf(meeting, holiday), zone, LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 6))
        assertEquals(listOf("Holiday", "Meeting"), day.getValue(LocalDate.of(2026, 11, 6)).map { it.title })
    }

    @Test fun eventEndingAtMidnightStaysOnItsDay() {
        val late = event("2026-11-06T22:00", "2026-11-07T00:00")
        val days = CalendarAgenda.groupByDay(listOf(late), zone, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        assertEquals(listOf(LocalDate.of(2026, 11, 6)), days.keys.toList())
    }

    // --- labels ---------------------------------------------------------------------------

    @Test fun rangeSharingAmPm() {
        val e = event("2026-11-06T09:00", "2026-11-06T10:30")
        assertEquals("9:00 $dash 10:30 am", CalendarAgenda.timeLabel(e, LocalDate.of(2026, 11, 6), zone, false))
    }

    @Test fun rangeAcrossNoon() {
        val e = event("2026-11-06T11:00", "2026-11-06T13:00")
        assertEquals("11:00 am $dash 1:00 pm", CalendarAgenda.timeLabel(e, LocalDate.of(2026, 11, 6), zone, false))
    }

    @Test fun rangeIn24Hours() {
        val e = event("2026-11-06T09:00", "2026-11-06T10:30")
        assertEquals("09:00 $dash 10:30", CalendarAgenda.timeLabel(e, LocalDate.of(2026, 11, 6), zone, true))
    }

    @Test fun multiDayTimedEvent() {
        val e = event("2026-11-06T21:00", "2026-11-08T17:00")
        assertEquals("From 9 pm", CalendarAgenda.timeLabel(e, LocalDate.of(2026, 11, 6), zone, false))
        assertEquals("All day", CalendarAgenda.timeLabel(e, LocalDate.of(2026, 11, 7), zone, false))
        assertEquals("Until 5 pm", CalendarAgenda.timeLabel(e, LocalDate.of(2026, 11, 8), zone, false))
    }

    @Test fun allDayLabel() =
        assertEquals("All day", CalendarAgenda.timeLabel(allDay(LocalDate.of(2026, 11, 6), 1), LocalDate.of(2026, 11, 6), zone, false))

    @Test fun clockFormats() {
        assertEquals("12:00 am", CalendarAgenda.clock(LocalTime.MIDNIGHT, false))
        assertEquals("12 pm", CalendarAgenda.clock(LocalTime.NOON, false, compact = true))
        assertEquals("5:30 pm", CalendarAgenda.clock(LocalTime.of(17, 30), false, compact = true))
        assertEquals("17:30", CalendarAgenda.clock(LocalTime.of(17, 30), true))
    }

    @Test fun dayLabels() {
        val today = LocalDate.of(2026, 11, 6)
        assertEquals("Today", CalendarAgenda.dayLabel(today, today, Locale.ENGLISH))
        assertEquals("Tomorrow", CalendarAgenda.dayLabel(today.plusDays(1), today, Locale.ENGLISH))
        assertEquals("Sun 8 Nov", CalendarAgenda.dayLabel(today.plusDays(2), today, Locale.ENGLISH))
        assertEquals("Fri 1 Jan 2027", CalendarAgenda.dayLabel(LocalDate.of(2027, 1, 1), today, Locale.ENGLISH))
    }

    @Test fun upcomingLabels() {
        val now = at("2026-11-06T10:00")
        assertEquals(
            "2:00 pm $dot Today",
            CalendarAgenda.upcomingLabel(event("2026-11-06T14:00", "2026-11-06T15:00"), now, zone, false, Locale.ENGLISH),
        )
        assertEquals(
            "Now $dot until 3 pm",
            CalendarAgenda.upcomingLabel(event("2026-11-06T09:00", "2026-11-06T15:00"), now, zone, false, Locale.ENGLISH),
        )
        assertEquals(
            "All day $dot Tomorrow",
            CalendarAgenda.upcomingLabel(allDay(LocalDate.of(2026, 11, 7), 1), now, zone, false, Locale.ENGLISH),
        )
    }

    // --- picking -------------------------------------------------------------------------

    @Test fun nextPrefersAnUpcomingTimedEvent() {
        val now = at("2026-11-06T10:00")
        val holiday = allDay(LocalDate.of(2026, 11, 6), 1, "Holiday")
        val ongoing = event("2026-11-06T09:30", "2026-11-06T10:30", "Standup")
        val later = event("2026-11-06T13:00", "2026-11-06T14:00", "Lunch")
        assertEquals("Lunch", CalendarAgenda.nextEvent(listOf(holiday, ongoing, later), now)?.title)
    }

    @Test fun nextFallsBackToWhatIsUnderWay() {
        val now = at("2026-11-06T10:00")
        val ongoing = event("2026-11-06T09:30", "2026-11-06T10:30", "Standup")
        val past = event("2026-11-06T08:00", "2026-11-06T09:00", "Gym")
        assertEquals("Standup", CalendarAgenda.nextEvent(listOf(past, ongoing), now)?.title)
        assertNull(CalendarAgenda.nextEvent(listOf(past), now))
    }

    @Test fun suggestedStart() {
        val now = at("2026-11-06T10:20")
        assertEquals(at("2026-11-06T11:00"), CalendarAgenda.suggestedStart(LocalDate.of(2026, 11, 6), now, zone))
        assertEquals(at("2026-11-09T09:00"), CalendarAgenda.suggestedStart(LocalDate.of(2026, 11, 9), now, zone))
    }

    @Test fun overlapWindow() {
        assertTrue(CalendarAgenda.overlaps(10, 20, 15, 30))
        assertFalse(CalendarAgenda.overlaps(10, 15, 15, 30))
        assertTrue(CalendarAgenda.overlaps(15, 15, 15, 30)) // an instant at the window's start
        assertFalse(CalendarAgenda.overlaps(30, 40, 15, 30))
    }

    @Test fun keyDistinguishesRepeats() {
        val a = event("2026-11-06T09:00", "2026-11-06T10:00")
        val b = event("2026-11-13T09:00", "2026-11-13T10:00")
        assertFalse(a.key == b.key)
    }
}
