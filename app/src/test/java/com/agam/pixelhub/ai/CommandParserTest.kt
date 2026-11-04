package com.agam.pixelhub.ai

import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.scheduling.Recurrence
import com.agam.pixelhub.scheduling.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class CommandParserTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun parse(json: String) = CommandParser.parse(json, zone)

    @Test fun oneOffReminder() = assertEquals(
        ParsedCommand.CreateReminder("Call mom", LocalDateTime.of(2026, 11, 5, 18, 0), Recurrence.NONE),
        parse("""{"action":"create_reminder","title":"Call mom","datetime":"2026-11-05T18:00"}"""),
    )

    @Test fun weeklyReminderWithDays() = assertEquals(
        ParsedCommand.CreateReminder(
            "Gym",
            LocalDateTime.of(2026, 11, 9, 7, 0),
            Recurrence(RepeatType.WEEKLY, Days.bit(DayOfWeek.MONDAY) or Days.bit(DayOfWeek.WEDNESDAY)),
        ),
        parse("""{"action":"create_reminder","title":"Gym","datetime":"2026-11-09T07:00","recurrence":"weekly","days":["MON","WED"]}"""),
    )

    @Test fun weeklyReminderWithoutDaysUsesTheDatesWeekday() {
        val cmd = parse("""{"action":"create_reminder","title":"Bins","datetime":"2026-11-06T20:00","recurrence":"weekly"}""")
        assertEquals(Days.bit(DayOfWeek.FRIDAY), (cmd as ParsedCommand.CreateReminder).recurrence.daysMask)
    }

    @Test fun everyNDaysReminder() {
        val cmd = parse("""{"action":"create_reminder","title":"Water plants","datetime":"2026-11-06T08:00","recurrence":"every_n_days","interval":3}""")
        assertEquals(Recurrence(RepeatType.EVERY_N_DAYS, interval = 3), (cmd as ParsedCommand.CreateReminder).recurrence)
    }

    @Test fun datetimeWithUtcOffsetIsConvertedToLocal() {
        val cmd = parse("""{"action":"create_reminder","title":"x","datetime":"2026-11-05T12:30:00Z"}""")
        assertEquals(LocalDateTime.of(2026, 11, 5, 18, 0), (cmd as ParsedCommand.CreateReminder).at)
    }

    @Test fun weekdayAlarm() = assertEquals(
        ParsedCommand.CreateAlarm(6, 30, Days.WEEKDAYS, "Gym"),
        parse("""{"action":"create_alarm","time":"06:30","days":["MON","TUE","WED","THU","FRI"],"label":"Gym"}"""),
    )

    @Test fun alarmIn12HourFormat() = assertEquals(
        ParsedCommand.CreateAlarm(18, 30, 0, ""),
        parse("""{"action":"create_alarm","time":"6:30 PM"}"""),
    )

    @Test fun openApp() = assertEquals(ParsedCommand.OpenApp("Spotify"), parse("""{"action":"open_app","query":"Spotify"}"""))

    @Test fun openLink() = assertEquals(
        ParsedCommand.OpenLink("geo:0,0?q=gym"),
        parse("""{"action":"open_link","uri":"geo:0,0?q=gym"}"""),
    )

    @Test fun answer() = assertEquals(ParsedCommand.Answer("Hi!"), parse("""{"action":"answer","text":"Hi!"}"""))

    @Test fun codeFencesAreStripped() = assertEquals(
        ParsedCommand.OpenApp("Maps"),
        parse("```json\n{\"action\":\"open_app\",\"query\":\"Maps\"}\n```"),
    )

    @Test fun unknownFieldsAreIgnored() = assertEquals(
        ParsedCommand.OpenApp("Maps"),
        parse("""{"action":"open_app","query":"Maps","confidence":0.9}"""),
    )

    @Test fun malformedJsonIsInvalid() = assertInvalid(parse("not json at all"))
    @Test fun unknownActionIsInvalid() = assertInvalid(parse("""{"action":"order_pizza"}"""))
    @Test fun reminderWithoutDatetimeIsInvalid() = assertInvalid(parse("""{"action":"create_reminder","title":"x"}"""))
    @Test fun reminderWithoutTitleIsInvalid() = assertInvalid(parse("""{"action":"create_reminder","datetime":"2026-11-05T18:00"}"""))
    @Test fun alarmWithImpossibleTimeIsInvalid() = assertInvalid(parse("""{"action":"create_alarm","time":"25:00"}"""))
    @Test fun linkWithoutSchemeIsInvalid() = assertInvalid(parse("""{"action":"open_link","uri":"example"}"""))

    @Test fun schemaRequiresAction() {
        assertTrue(CommandParser.responseSchema.toString().contains("\"required\":[\"action\"]"))
    }

    private fun assertInvalid(cmd: ParsedCommand) =
        assertTrue("expected Invalid but got $cmd", cmd is ParsedCommand.Invalid)
}
