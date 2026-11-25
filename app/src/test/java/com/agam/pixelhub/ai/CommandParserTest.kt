package com.agam.pixelhub.ai

import com.agam.pixelhub.data.ExpenseCategory
import com.agam.pixelhub.data.ExpenseKind
import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.scheduling.Recurrence
import com.agam.pixelhub.scheduling.RepeatType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Every case runs as of Thursday 5 Nov 2026, 10:00 in India. */
class CommandParserTest {
    private val now = ZonedDateTime.of(2026, 11, 5, 10, 0, 0, 0, ZoneId.of("Asia/Kolkata"))
    private val apps = listOf("Spotify", "YouTube", "WhatsApp", "Google Maps")
    private fun parse(text: String) = CommandParser.parse(text, now, apps).command
    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, month, day, hour, minute)
    private fun reminder(title: String, at: LocalDateTime, rec: Recurrence = Recurrence.NONE) = ParsedCommand.CreateReminder(title, at, rec)
    private fun reminderIn(title: String, at: LocalDateTime, minutes: Long) =
        ParsedCommand.CreateReminder(title, at, Recurrence.NONE, inMinutes = minutes)
    private fun spent(rupees: Long, category: ExpenseCategory, note: String, at: LocalDateTime? = null) =
        ParsedCommand.LogExpense(rupees * 100, category, note, at, ExpenseKind.EXPENSE)
    private fun earned(rupees: Long, category: ExpenseCategory, note: String) =
        ParsedCommand.LogExpense(rupees * 100, category, note, null, ExpenseKind.INCOME)
    private fun invalid(text: String) = assertTrue("expected Invalid for \"$text\", got ${parse(text)}", parse(text) is ParsedCommand.Invalid)

    // ---- Reminders, English ----

    @Test fun reminderAtPm() = assertEquals(reminder("Call mom", at(11, 5, 18)), parse("remind me to call mom at 6pm"))

    @Test fun reminderTomorrow() = assertEquals(reminder("Call mom", at(11, 6, 18)), parse("Remind me to call mom tomorrow at 6 pm"))

    @Test fun bareHourPicksTheNextOne() = assertEquals(reminder("Call Rahul", at(11, 5, 18)), parse("remind me to call Rahul at 6"))

    @Test fun todayBareHourPicksTheOneStillAhead() =
        assertEquals(reminder("Call mom", at(11, 5, 20)), parse("remind me to call mom today at 8"))

    @Test fun dailyBareHourDoesNotDependOnTheClock() = assertEquals(
        reminder("Take pills", at(11, 6, 8), Recurrence(RepeatType.DAILY)),
        parse("remind me to take pills every day at 8"),
    )

    @Test fun midnightOnANamedNightIsTheNextDay() =
        assertEquals(reminder("Lock the door", at(11, 7, 0)), parse("remind me to lock the door tomorrow night at 12"))

    @Test fun inTwoHours() = assertEquals(reminderIn("Pay rent", at(11, 5, 12), 120), parse("remind me to pay rent in 2 hours"))

    @Test fun inTwentyMinutesBeforeTitle() =
        assertEquals(reminderIn("Check the oven", at(11, 5, 10, 20), 20), parse("remind me in 20 min to check the oven"))

    @Test fun halfAnHour() = assertEquals(reminderIn("Stretch", at(11, 5, 10, 30), 30), parse("remind me to stretch in half an hour"))

    @Test fun decimalHours() = assertEquals(reminderIn("Stretch", at(11, 5, 11, 30), 90), parse("remind me to stretch in 1.5 hours"))

    @Test fun typedFillerPairStaysInTitle() =
        assertEquals(reminder("Go to the gym", at(11, 6, 7)), parse("remind me to go to the gym tomorrow at 7am"))

    @Test fun setIsPartOfTheTask() = assertEquals(reminder("Set the table", at(11, 5, 20)), parse("remind me to set the table at 8pm"))

    @Test fun dailyAtNight() = assertEquals(
        reminder("Take vitamins", at(11, 5, 21), Recurrence(RepeatType.DAILY)),
        parse("remind me to take vitamins every day at 9pm"),
    )

    @Test fun weeklyOnTwoDays() = assertEquals(
        reminder("Go to gym", at(11, 9, 7), Recurrence(RepeatType.WEEKLY, Days.bit(DayOfWeek.MONDAY) or Days.bit(DayOfWeek.WEDNESDAY))),
        parse("remind me to go to gym every monday and wednesday at 7am"),
    )

    @Test fun everyThreeDays() = assertEquals(
        reminder("Water plants", at(11, 8, 9), Recurrence(RepeatType.EVERY_N_DAYS, interval = 3)),
        parse("remind me to water plants every 3 days"),
    )

    @Test fun namedWeekdayAfternoon() = assertEquals(reminder("Meeting", at(11, 6, 15)), parse("remind me about the meeting on friday at 3"))

    @Test fun ordinalThisMonthStillAhead() = assertEquals(reminder("Pay rent", at(11, 30, 9)), parse("remind me on the 30th to pay rent"))

    @Test fun ordinalMissingFromThisMonthRollsToTheNextThatHasIt() =
        assertEquals(reminder("Pay rent", at(12, 31, 9)), parse("remind me on the 31st to pay rent"))

    @Test fun dayAndMonth() = assertEquals(reminder("Submit assignment", at(11, 12, 9)), parse("remind me on 12 nov to submit assignment"))

    @Test fun halfPastWithMinutes() = assertEquals(reminder("Leave for airport", at(11, 5, 17, 30)), parse("remind me to leave for airport at 5:30 pm"))

    @Test fun morningWithoutTime() = assertEquals(reminder("Buy milk", at(11, 6, 9)), parse("remind me to buy milk tomorrow morning"))

    @Test fun pastTimeTodayMovesToTomorrow() = assertEquals(reminder("Check mail", at(11, 6, 8)), parse("remind me to check mail at 8am"))

    @Test fun wakeUpInsideAReminderIsStillAReminder() =
        assertEquals(reminder("Wake up dad", at(11, 5, 19)), parse("remind me to wake up dad at 7"))

    @Test fun reminderWithoutTitleFails() = invalid("remind me")

    // ---- Reminders, Hinglish ----

    @Test fun kalSubah() = assertEquals(reminder("Doodh lena", at(11, 6, 7)), parse("kal subah 7 baje doodh lena yaad dilana"))

    @Test fun shaamKo() = assertEquals(reminder("Dawai", at(11, 5, 18)), parse("mujhe shaam 6 baje dawai yaad dila do"))

    @Test fun harSomvar() = assertEquals(
        reminder("Gym", at(11, 9, 8), Recurrence(RepeatType.WEEKLY, Days.bit(DayOfWeek.MONDAY))),
        parse("har somvar subah 8 baje gym yaad dilana"),
    )

    @Test fun ghanteBaad() = assertEquals(reminderIn("Khana banana", at(11, 5, 12), 120), parse("2 ghante baad khana banana yaad dilana"))

    @Test fun saadhe() = assertEquals(reminder("Bijli bill bharna", at(11, 6, 18, 30)), parse("kal shaam saadhe 6 baje bijli bill bharna yaad dilana"))

    @Test fun parso() = assertEquals(reminder("Bank jana", at(11, 7, 9)), parse("parso bank jana yaad dilana"))

    @Test fun rozRaat() = assertEquals(
        reminder("Dawai leni", at(11, 5, 22), Recurrence(RepeatType.DAILY)),
        parse("roz raat 10 baje dawai leni yaad dilana"),
    )

    // ---- Alarms ----

    @Test fun weekdayAlarm() = assertEquals(ParsedCommand.CreateAlarm(6, 30, Days.WEEKDAYS, ""), parse("wake me up at 6:30 on weekdays"))

    @Test fun oneOffAlarm() = assertEquals(ParsedCommand.CreateAlarm(7, 0, 0, ""), parse("set an alarm for 7am"))

    @Test fun alarmWithLabel() = assertEquals(ParsedCommand.CreateAlarm(17, 45, 0, "Gym"), parse("alarm at 5:45 pm for gym"))

    @Test fun alarmInMinutes() = assertEquals(ParsedCommand.CreateAlarm(10, 30, 0, "", inMinutes = 30), parse("wake me up in 30 minutes"))

    @Test fun alarmEveryDay() = assertEquals(ParsedCommand.CreateAlarm(5, 0, Days.ALL, ""), parse("alarm at 5am every day"))

    @Test fun utthaDena() = assertEquals(ParsedCommand.CreateAlarm(6, 0, 0, ""), parse("subah 6 baje utha dena"))

    @Test fun alarmLagaDoRoz() = assertEquals(ParsedCommand.CreateAlarm(7, 0, Days.ALL, ""), parse("roz 7 baje ka alarm laga do"))

    @Test fun alarmWithoutTimeFails() = invalid("set alarm")

    // ---- Money ----

    @Test fun spentOnLunch() = assertEquals(spent(250, ExpenseCategory.FOOD, "Lunch"), parse("spent 250 on lunch"))

    @Test fun rupeeSignAndCommas() =
        assertEquals(spent(1200, ExpenseCategory.BILLS, "Electricity bill"), parse("paid ₹1,200 for electricity bill"))

    @Test fun categoryAndAmountOnly() = assertEquals(spent(180, ExpenseCategory.TRANSPORT, "Uber"), parse("uber 180"))

    @Test fun thousandsAndYesterday() =
        assertEquals(spent(1200, ExpenseCategory.FOOD, "Groceries", at(11, 4, 10)), parse("1.2k on groceries yesterday"))

    @Test fun hinglishKalIsYesterdayForMoney() =
        assertEquals(spent(300, ExpenseCategory.FOOD, "Lunch", at(11, 4, 10)), parse("kal lunch pe 300 kharch kiye"))

    @Test fun petrolHinglish() = assertEquals(spent(500, ExpenseCategory.TRANSPORT, "Petrol"), parse("500 ka petrol dalwaya").let {
        // "dalwaya" is just a verb; the note keeps what's left.
        (it as ParsedCommand.LogExpense).copy(note = it.note.substringBefore(" "))
    })

    @Test fun salaryIncome() = assertEquals(earned(5000, ExpenseCategory.SALARY, "Salary"), parse("got paid 5000 salary"))

    @Test fun salaryCredited() = assertEquals(earned(50000, ExpenseCategory.SALARY, "Salary"), parse("salary 50000 credited"))

    @Test fun hinglishIncome() = assertEquals(earned(2000, ExpenseCategory.OTHER, "Mummy"), parse("mummy se 2000 mile"))

    @Test fun refund() = assertEquals(earned(499, ExpenseCategory.REFUNDS, "Refund from amazon"), parse("refund of 499 from amazon"))

    @Test fun lakhBonus() = assertEquals(earned(200000, ExpenseCategory.SALARY, "Bonus"), parse("2 lakh bonus"))

    @Test fun addToCategory() = assertEquals(spent(30, ExpenseCategory.FOOD, "Food"), parse("add 30 to food"))

    @Test fun addWithRupeeSign() = assertEquals(spent(120, ExpenseCategory.TRANSPORT, "Auto"), parse("Add ₹120 for auto"))

    @Test fun addToIncomeCategory() = assertEquals(earned(500, ExpenseCategory.SALARY, "Salary"), parse("add 500 to salary"))

    @Test fun addHinglish() = assertEquals(spent(30, ExpenseCategory.FOOD, "Food"), parse("food mein 30 add karo"))

    @Test fun daalDoHinglish() = assertEquals(spent(200, ExpenseCategory.FOOD, "Groceries"), parse("200 groceries mein daal do"))

    @Test fun addAReminderIsStillAReminder() = assertTrue(parse("add a reminder to call mom at 6pm") is ParsedCommand.CreateReminder)

    @Test fun addAlarmIsStillAnAlarm() = assertTrue(parse("add alarm at 7am") is ParsedCommand.CreateAlarm)

    @Test fun priceAfterForBeatsTheCount() =
        assertEquals(spent(500, ExpenseCategory.OTHER, "2 tickets"), parse("bought 2 tickets for 500"))

    @Test fun moneyWithoutAmountFails() = invalid("paid rent")

    @Test fun numberAloneIsNotMoney() = invalid("what is 15 percent of 80")

    // ---- People ledger ----

    private val people = listOf("Neha", "Mummy", "Rahul Sharma")
    private fun ledger(text: String) = CommandParser.parse(text, now, apps, people).command
    private fun owesMe(name: String, rupees: Long, note: String = "", new: Boolean = false, at: LocalDateTime? = null) =
        ParsedCommand.LedgerEntry(name, rupees * 100, note, at, isNewPerson = new)
    private fun iOwe(name: String, rupees: Long, note: String = "", new: Boolean = false, at: LocalDateTime? = null) =
        ParsedCommand.LedgerEntry(name, -rupees * 100, note, at, isNewPerson = new)

    @Test fun paidSavedPerson() = assertEquals(owesMe("Neha", 30), ledger("i paid neha 30"))

    @Test fun paidWithNote() = assertEquals(owesMe("Neha", 30, "Lunch"), ledger("paid Neha 30 for lunch"))

    @Test fun paidNewPerson() = assertEquals(owesMe("Rahul", 50, new = true), parse("paid rahul 50"))

    @Test fun lentNewPerson() = assertEquals(owesMe("Priya", 500, new = true), parse("lent priya 500"))

    @Test fun owesMe() = assertEquals(owesMe("Amit", 200, new = true), parse("amit owes me 200"))

    @Test fun koDiyeNewPerson() = assertEquals(owesMe("Rahul", 50, new = true), parse("rahul ko 50 diye"))

    @Test fun koDiyeWithNoteAndDate() =
        assertEquals(owesMe("Neha", 80, "Auto", at = at(11, 4, 10)), ledger("kal neha ko auto ke liye 80 diye"))

    @Test fun keLiye() = assertEquals(owesMe("Neha", 120, "Movie ticket"), ledger("neha ke liye 120 ka movie ticket"))

    @Test fun paidForSavedPerson() = assertEquals(owesMe("Neha", 60, "Coffee"), ledger("paid 60 for neha coffee"))

    @Test fun theyPaidMe() = assertEquals(iOwe("Neha", 30), ledger("neha paid me 30"))

    @Test fun theyPaidForMe() = assertEquals(iOwe("Neha", 150, "Dinner"), ledger("neha paid 150 for me dinner"))

    @Test fun gotFrom() = assertEquals(iOwe("Neha", 100), ledger("got 100 from neha"))

    @Test fun seMileSavedIsLedger() = assertEquals(iOwe("Mummy", 2000), ledger("mummy se 2000 mile"))

    @Test fun neDiye() = assertEquals(iOwe("Neha", 200), ledger("neha ne mere liye 200 diye"))

    @Test fun borrowedNewPerson() = assertEquals(iOwe("Karan", 300, new = true), parse("borrowed 300 from karan"))

    @Test fun iOweNewPerson() = assertEquals(iOwe("Karan", 40, new = true), parse("i owe karan 40"))

    @Test fun udhaarLiya() = assertEquals(iOwe("Karan", 500, new = true), parse("karan se 500 udhaar liye"))

    @Test fun multiWordSavedName() = assertEquals(owesMe("Rahul Sharma", 90), ledger("paid rahul sharma 90"))

    @Test fun ledgerNeedsAmount() = assertTrue(ledger("paid neha") is ParsedCommand.Invalid)

    @Test fun balanceHinglish() = assertEquals(ParsedCommand.LedgerBalance("Neha"), ledger("neha ka hisaab"))

    @Test fun balanceEnglish() = assertEquals(ParsedCommand.LedgerBalance("Neha"), ledger("how much does neha owe me"))

    @Test fun balanceUnknownName() = assertEquals(ParsedCommand.LedgerBalance("Vikram"), parse("vikram ka hisaab"))

    // Guards: these stay what they were before the ledger existed.

    @Test fun lunchStaysExpense() = assertEquals(spent(250, ExpenseCategory.FOOD, "Lunch"), ledger("spent 250 on lunch"))

    @Test fun billStaysExpense() =
        assertEquals(spent(1200, ExpenseCategory.BILLS, "Electricity bill"), ledger("paid ₹1,200 for electricity bill"))

    @Test fun merchantStaysExpense() = assertTrue(ledger("paid swiggy 300") is ParsedCommand.LogExpense)

    @Test fun rentStaysExpense() = assertTrue(ledger("paid rent 15000") is ParsedCommand.LogExpense)

    @Test fun unsavedSeMileStaysIncome() = assertEquals(earned(2000, ExpenseCategory.OTHER, "Papa"), ledger("papa se 2000 mile"))

    @Test fun savedNameInPlainExpenseStaysExpense() =
        assertTrue(ledger("spent 250 on lunch with neha") is ParsedCommand.LogExpense)

    @Test fun remindAboutPersonIsReminder() =
        assertTrue(ledger("remind me to pay neha 30 tomorrow") is ParsedCommand.CreateReminder)

    // ---- Apps and links ----

    @Test fun openApp() = assertEquals(ParsedCommand.OpenApp("Spotify"), parse("open spotify"))

    @Test fun openAppHinglish() = assertEquals(ParsedCommand.OpenApp("Whatsapp"), parse("whatsapp kholo"))

    @Test fun bareAppName() = assertEquals(ParsedCommand.OpenApp("YouTube"), parse("youtube"))

    @Test fun callNumber() = assertEquals(ParsedCommand.OpenLink("tel:9876543210"), parse("call 98765 43210"))

    @Test fun callHinglish() = assertEquals(ParsedCommand.OpenLink("tel:9876543210"), parse("9876543210 pe call karo"))

    @Test fun callNameFails() = invalid("mummy ko call karo")

    @Test fun navigate() = assertEquals(ParsedCommand.OpenLink("google.navigation:q=india%20gate"), parse("navigate to India Gate"))

    @Test fun navigateHinglish() = assertEquals(ParsedCommand.OpenLink("google.navigation:q=station"), parse("station ka rasta dikhao"))

    @Test fun search() = assertEquals(ParsedCommand.OpenLink("https://www.google.com/search?q=cricket%20score"), parse("search cricket score"))

    @Test fun website() = assertEquals(ParsedCommand.OpenLink("https://youtube.com"), parse("open youtube.com"))

    @Test fun sms() = assertEquals(ParsedCommand.OpenLink("sms:9876543210?body=running%20late"), parse("text 9876543210 running late"))

    @Test fun smsKeepsCaseAndPunctuation() = assertEquals(
        ParsedCommand.OpenLink("sms:9876543210?body=I%27ll%20be%20late%21"),
        parse("text 9876543210 saying I'll be late!"),
    )

    // ---- Regressions from review ----

    // An explicit spending word beats an income category: a birthday gift you bought is spending.
    @Test fun spentOnBirthdayGiftIsExpense() =
        assertEquals(spent(1500, ExpenseCategory.OTHER, "Birthday gift"), parse("spent 1500 on birthday gift"))

    @Test fun paidForProjectIsExpense() = assertEquals(spent(2000, ExpenseCategory.OTHER, "Project"), parse("paid 2000 for project"))

    @Test fun billAayaIsExpense() = assertEquals(spent(2000, ExpenseCategory.BILLS, "Bijli ka bill"), parse("bijli ka bill 2000 aaya"))

    @Test fun clientPaidMeStaysIncome() =
        assertTrue((parse("client paid me 5000 for project") as ParsedCommand.LogExpense).isIncome)

    @Test fun rupeeDotBeforeAmount() = assertEquals(spent(500, ExpenseCategory.FOOD, "Lunch"), parse("paid Rs.500 for lunch"))

    @Test fun decimalAmountIsOneNumber() = assertEquals(
        ParsedCommand.LogExpense(1250, ExpenseCategory.FOOD, "Chai", null, ExpenseKind.EXPENSE),
        parse("spent 12.50 on chai"),
    )

    @Test fun alarmOnALaterDateFails() = invalid("wake me up on 12 nov at 7")

    @Test fun alarmDayAfterTomorrowFails() = invalid("wake me up day after tomorrow at 7")

    @Test fun alarmInTwoDaysFails() = invalid("wake me up in 2 days at 7")

    @Test fun alarmTomorrowIsTheNextRing() = assertEquals(ParsedCommand.CreateAlarm(7, 0, 0, ""), parse("wake me up tomorrow at 7"))

    @Test fun alarmTonight() = assertEquals(ParsedCommand.CreateAlarm(23, 0, 0, ""), parse("set an alarm for tonight at 11"))

    @Test fun alarmOnTheComingWeekdayRingsOnce() = assertEquals(ParsedCommand.CreateAlarm(7, 0, 0, ""), parse("wake me up on friday at 7"))

    @Test fun alarmOnALaterWeekdayFails() = invalid("wake me up on monday at 7")

    @Test fun leapDayFindsTheNextLeapYear() = assertEquals(
        reminder("Renew passport", LocalDateTime.of(2028, 2, 29, 9, 0)),
        parse("remind me to renew passport on 29 feb"),
    )

    @Test fun impossibleDateFails() = invalid("remind me on 31 april to pay rent")

    @Test fun impossibleYearDateFails() = invalid("remind me on 29/2/2027 to pay rent")

    @Test fun rangeIsNotADate() =
        assertEquals(reminder("Drink 2-3 glasses of water", at(11, 5, 18)), parse("remind me to drink 2-3 glasses of water at 6pm"))

    @Test fun fractionIsNotADate() = assertEquals(reminder("Buy 1/2 kg paneer", at(11, 6, 9)), parse("remind me to buy 1/2 kg paneer"))

    @Test fun rangeInExpenseIsNotADate() = assertEquals(spent(250, ExpenseCategory.OTHER, "2-3 items"), parse("spent 250 on 2-3 items"))

    @Test fun thisEveningAt() = assertEquals(reminder("Call mom", at(11, 5, 19)), parse("remind me to call mom this evening at 7"))

    @Test fun hourAndMinutes() = assertEquals(reminderIn("Stretch", at(11, 5, 11, 30), 90), parse("remind me to stretch in 1 hour 30 minutes"))

    @Test fun hourAndAHalf() = assertEquals(reminderIn("Stretch", at(11, 5, 11, 30), 90), parse("remind me to stretch in an hour and a half"))

    @Test fun hugeRepeatDoesNotCrash() = assertEquals(
        Recurrence(RepeatType.EVERY_N_DAYS, interval = 365),
        (parse("remind me to water plants every 3000000000 days") as ParsedCommand.CreateReminder).recurrence,
    )

    @Test fun hugeDelayDoesNotCrash() =
        assertTrue(parse("remind me to stretch in 9999999999999999 minutes") is ParsedCommand.CreateReminder)

    @Test fun onlyIsNotAPerson() = assertTrue(parse("paid only 200 for lunch") is ParsedCommand.LogExpense)

    @Test fun aroundIsNotAPerson() = assertTrue(parse("paid around 300 for cab") is ParsedCommand.LogExpense)

    @Test fun heIsNotAPerson() = assertTrue(parse("he owes me 50") !is ParsedCommand.LedgerEntry)

    @Test fun mintStaysMint() = assertEquals(reminder("Buy mint", at(11, 6, 9)), parse("remind me to buy mint"))

    @Test fun shamIsAName() = assertEquals(reminder("Call Sham", at(11, 5, 18)), parse("remind me to call Sham at 6pm"))

    @Test fun paidForGoogleIsExpense() = assertEquals(spent(130, ExpenseCategory.OTHER, "Google"), parse("paid 130 for google"))

    @Test fun alarmSummaryUsesWesternDigits() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
            val summary = AssistantLog.summarize(ParsedCommand.CreateAlarm(7, 5, 0, ""))
            assertTrue(summary, "07:05" in summary)
        } finally {
            Locale.setDefault(before)
        }
    }

    // ---- Regressions from the second review ----

    @Test fun spentOnAlarmClockIsExpense() =
        assertEquals(spent(500, ExpenseCategory.OTHER, "Alarm clock"), parse("spent 500 on alarm clock"))

    @Test fun openRemindersOpensTheApp() = assertEquals(ParsedCommand.OpenApp("Reminders"), parse("open reminders"))

    @Test fun openAlarmOpensTheApp() = assertEquals(ParsedCommand.OpenApp("Alarm"), parse("open alarm"))

    @Test fun remindToPayAnAmountIsStillAReminder() =
        assertEquals(reminder("Pay 500 rent", at(11, 6, 9)), parse("remind me to pay 500 rent tomorrow"))

    @Test fun bareGoogleAppNameOpensIt() = assertEquals(ParsedCommand.OpenApp("Google Maps"), parse("google maps"))

    private val halfPast = ZonedDateTime.of(2026, 11, 5, 10, 0, 30, 0, ZoneId.of("Asia/Kolkata"))

    @Test fun relativeReminderRoundsUp() = assertEquals(
        reminderIn("Stretch", at(11, 5, 10, 2), 1),
        CommandParser.parse("remind me in 1 minute to stretch", halfPast).command,
    )

    @Test fun relativeAlarmRoundsUp() = assertEquals(
        ParsedCommand.CreateAlarm(10, 2, 0, "", inMinutes = 1),
        CommandParser.parse("alarm in 1 minute", halfPast).command,
    )

    @Test fun ceilToMinute() {
        assertEquals(at(11, 5, 10, 1), CommandParser.ceilToMinute(LocalDateTime.of(2026, 11, 5, 10, 0, 1)))
        assertEquals(at(11, 5, 10, 0), CommandParser.ceilToMinute(at(11, 5, 10, 0)))
    }

    @Test fun inMonths() =
        assertEquals(reminder("Renew passport", LocalDateTime.of(2027, 1, 5, 9, 0)), parse("remind me in 2 months to renew passport"))

    @Test fun inAYear() =
        assertEquals(reminder("Renew passport", LocalDateTime.of(2027, 11, 5, 9, 0)), parse("remind me in 1 year to renew passport"))

    @Test fun nextWeek() = assertEquals(reminder("Call", at(11, 12, 9)), parse("remind me next week to call"))

    @Test fun everyHourFails() = invalid("remind me to drink water every hour")

    @Test fun everyTwoHoursFails() = invalid("remind me to drink water every 2 hours")

    @Test fun everyMonthFails() = invalid("remind me to pay rent every month on 1st")

    @Test fun alarmEveryHourFails() = invalid("alarm every hour")

    private val afterMidnight = ZonedDateTime.of(2026, 11, 5, 0, 30, 0, 0, ZoneId.of("Asia/Kolkata"))
    private fun lateParse(text: String) = CommandParser.parse(text, afterMidnight).command

    @Test fun tomorrowAfterMidnightIsTheComingMorningAlarm() {
        assertEquals(ParsedCommand.CreateAlarm(7, 0, 0, ""), lateParse("wake me up tomorrow at 7"))
        assertEquals(ParsedCommand.CreateAlarm(7, 0, 0, ""), lateParse("wake me up tomorrow at 7am"))
    }

    @Test fun tomorrowAfterMidnightIsTheComingMorningReminder() =
        assertEquals(reminder("Call mom", at(11, 5, 9)), lateParse("remind me tomorrow at 9am to call mom"))

    @Test fun namedDateAfterMidnightStaysThatDate() =
        assertEquals(reminder("Call mom", at(11, 6, 9)), lateParse("remind me on 6 nov at 9am to call mom"))

    @Test fun croreIsTooMuch() = invalid("spent 1 crore on house")

    @Test fun halfACrore() = assertEquals(spent(50_00_000, ExpenseCategory.OTHER, "Flat"), parse("spent 0.5 cr on flat"))

    @Test fun croreInLedgerIsTooMuch() = invalid("paid rahul 2 cr")

    @Test fun moneyTomorrowFails() = invalid("spent 300 tomorrow")

    @Test fun moneyOnAFutureDateFails() = invalid("spent 2000 on rent 1/12/2026")

    @Test fun urlKeepsThePathsCase() = assertEquals(ParsedCommand.OpenLink("https://example.com/AbC"), parse("HTTPS://Example.com/AbC"))

    @Test fun urlKeepsItsQuery() =
        assertEquals(ParsedCommand.OpenLink("https://youtube.com/watch?v=AbCdEf"), parse("youtube.com/watch?v=AbCdEf"))

    @Test fun openUrlWithQuery() =
        assertEquals(ParsedCommand.OpenLink("https://example.com/Path?Q=1"), parse("open https://example.com/Path?Q=1"))

    @Test fun urlWithoutTheFullStop() = assertEquals(ParsedCommand.OpenLink("https://youtube.com"), parse("open youtube.com."))

    @Test fun placeKeepsHinglishWords() =
        assertEquals(ParsedCommand.OpenLink("google.navigation:q=har%20ki%20pauri"), parse("navigate to har ki pauri"))

    @Test fun searchKeepsHinglishWords() = assertEquals(
        ParsedCommand.OpenLink("https://www.google.com/search?q=raat%20akeli%20hai%20song"),
        parse("search raat akeli hai song"),
    )

    @Test fun appNameKeepsHinglishWords() = assertEquals(ParsedCommand.OpenApp("Aaj tak"), parse("aaj tak kholo"))

    @Test fun bareHinglishAppName() =
        assertEquals(ParsedCommand.OpenApp("Aaj Tak"), CommandParser.parse("aaj tak", now, listOf("Aaj Tak")).command)

    @Test fun dashRangeIsNotADate() = assertEquals(reminder("Buy 2-3 lemons", at(11, 6, 9)), parse("remind me to buy 2-3 lemons"))

    @Test fun dashTimeRangeIsNotADate() =
        assertEquals(at(11, 5, 19), (parse("remind me to call between 6-7 pm") as ParsedCommand.CreateReminder).at)

    @Test fun dashRangeInExpenseIsNotADate() =
        assertEquals(null, (parse("spent 100 on 3-4 samosas") as ParsedCommand.LogExpense).at)

    @Test fun dashDateWithYear() =
        assertEquals(reminder("Pay rent", LocalDateTime.of(2027, 11, 12, 9, 0)), parse("remind me on 12-11-2027 to pay rent"))

    @Test fun impossibleNumericDateFails() = invalid("remind me on 12/25 to pay")

    @Test fun splitFiftyFiftyStaysInTitle() = assertEquals(reminder("Split bill 50/50", at(11, 6, 9)), parse("remind me to split bill 50/50"))

    @Test fun ordinalThatIsNoDateStaysInTitle() =
        assertEquals(reminder("Plan the 40th anniversary", at(11, 5, 18)), parse("remind me to plan the 40th anniversary at 6pm"))

    @Test fun decimalDoseIsNotATime() = assertEquals(reminder("Take 2.50 mg", at(11, 5, 20)), parse("remind me to take 2.50 mg at 8pm"))

    @Test fun decimalWeightIsNotATime() =
        assertEquals(reminder("Buy 1.25 kg rice", at(11, 5, 18)), parse("remind me to buy 1.25 kg rice at 6pm"))

    @Test fun dotTimeStillWorks() = assertEquals(ParsedCommand.CreateAlarm(6, 30, 0, ""), parse("alarm at 6.30"))

    @Test fun alarmAtBareHour() {
        assertEquals(ParsedCommand.CreateAlarm(7, 0, 0, ""), parse("alarm at 7"))
        assertEquals(ParsedCommand.CreateAlarm(7, 0, 0, ""), parse("set alarm for 7"))
        assertEquals(ParsedCommand.CreateAlarm(7, 0, 0, ""), parse("alarm 7"))
        assertEquals(ParsedCommand.CreateAlarm(7, 0, Days.ALL, ""), parse("alarm at 7 every day"))
        assertEquals(ParsedCommand.CreateAlarm(7, 0, 0, ""), parse("set alarm for 7 tomorrow"))
        assertEquals(ParsedCommand.CreateAlarm(7, 30, 0, ""), parse("alarm at 7 30"))
    }

    @Test fun smsBodyStartingWithANumber() =
        assertEquals(ParsedCommand.OpenLink("sms:9876543210?body=5%20min%20late"), parse("text 9876543210 5 min late"))

    @Test fun smsSpacedNumber() = assertEquals(ParsedCommand.OpenLink("sms:9876543210?body=hi"), parse("text 98765 43210 hi"))

    @Test fun smsBodyStartingWithALongNumber() =
        assertEquals(ParsedCommand.OpenLink("sms:9876543210?body=100%20rs%20bhej"), parse("text 9876543210 100 rs bhej"))

    // ---- Regressions from the fixes above ----

    private fun isReminder(text: String) =
        assertTrue("expected a reminder for \"$text\", got ${parse(text)}", parse(text) is ParsedCommand.CreateReminder)

    @Test fun reminderEndingInOpenIsStillAReminder() {
        isReminder("remind me to keep the shop open")
        isReminder("remind me to check if the bank is open")
        isReminder("kal 9 baje yaad dilana, dukaan kholna")
        isReminder("open the shop at 9 remind me")
    }

    @Test fun openingTheRemindersOrAlarmAppHinglish() {
        assertEquals(ParsedCommand.OpenApp("Alarm"), parse("alarm kholo"))
        assertEquals(ParsedCommand.OpenApp("Reminders"), parse("reminders app open karo"))
    }

    @Test fun spendingBeforeAReminderLeavesItAReminder() {
        isReminder("bought the tickets, remind me at 6 to print them")
        isReminder("paid the electricity bill, remind me again on 5th")
        assertEquals(
            LocalDateTime.of(2026, 12, 5, 9, 0),
            (parse("paid 500 for gym, remind me on 5 dec to renew") as ParsedCommand.CreateReminder).at,
        )
    }

    @Test fun monthlyInATitleIsNotARepeat() {
        assertEquals(reminder("Submit the monthly report", at(11, 6, 10)), parse("remind me to submit the monthly report tomorrow at 10"))
        isReminder("remind me to book my yearly checkup on friday")
        assertEquals(ParsedCommand.CreateAlarm(7, 0, 0, "Monthly meeting"), parse("alarm at 7 for the monthly meeting"))
    }

    @Test fun monthlyAsTheRepeatStillFails() {
        invalid("remind me to pay rent monthly")
        invalid("remind me to pay rent monthly on the 1st")
        invalid("remind me to drink water hourly")
    }

    @Test fun namedDayBeatsNextMonthsInTheTitle() =
        assertEquals(reminder("Pay next month's rent", at(12, 1, 9)), parse("remind me on 1st to pay next month's rent"))

    @Test fun nextWeekFriday() {
        assertEquals(reminder("Call", at(11, 13, 9)), parse("remind me next week friday to call"))
        assertEquals(reminder("Call", at(11, 13, 9)), parse("remind me on friday next week to call"))
    }

    @Test fun phoneNumberInSmallGroups() {
        assertEquals(ParsedCommand.OpenLink("tel:9876543210"), parse("call 98 76 54 32 10"))
        assertEquals(ParsedCommand.OpenLink("sms:9876543210?body=running%20late"), parse("text 98 76 54 32 10 running late"))
        assertEquals(ParsedCommand.OpenLink("sms:+442079460958?body=hi"), parse("text +44 20 7946 0958 hi"))
    }

    @Test fun spacedNumberInTheLabelIsNotTheTime() {
        val train = parse("wake me up at 6 for the 10 30 train") as ParsedCommand.CreateAlarm
        assertEquals(6 to 0, train.hour to train.minute)
        val colon = parse("alarm at 6:30 for the 10 45 train") as ParsedCommand.CreateAlarm
        assertEquals(6 to 30, colon.hour to colon.minute)
    }

    @Test fun spacedTimeAfterAPartOfTheDay() = assertEquals(ParsedCommand.CreateAlarm(6, 30, 0, ""), parse("alarm kal subah 6 30"))

    @Test fun minutesWithoutTheUnitAskForTheTime() {
        invalid("wake me up in 20")
        invalid("alarm in 5")
    }

    // ---- Regressions from the third review ----

    @Test fun phoneNumberWithACountryCodeInSmallGroups() {
        assertEquals(ParsedCommand.OpenLink("tel:+919876543210"), parse("call +91 98 76 54 32 10"))
        assertEquals(ParsedCommand.OpenLink("tel:919876543210"), parse("call 91 98 76 54 32 10"))
        assertEquals(ParsedCommand.OpenLink("tel:+919876543210"), parse("call +91 9876 54 32 10"))
        assertEquals(ParsedCommand.OpenLink("sms:+919876543210?body=hi"), parse("text +91 98 76 54 32 10 hi"))
        assertEquals(ParsedCommand.OpenLink("sms:919876543210?body=hi"), parse("text 91 98 76 54 32 10 hi"))
        assertEquals(ParsedCommand.OpenLink("sms:+919876543210?body=100%20rs%20bhej"), parse("text +91 98765 43210 100 rs bhej"))
        // Ten digits that happen to start with 91, then a message starting with a number.
        assertEquals(ParsedCommand.OpenLink("sms:9123456789?body=5%20min%20late"), parse("text 91 23 45 67 89 5 min late"))
    }

    @Test fun ordinalInTheTitleDoesNotBeatNextMonthOrYear() {
        assertEquals(
            reminder("Our 25th anniversary", LocalDateTime.of(2027, 11, 5, 9, 0)),
            parse("remind me next year about our 25th anniversary"),
        )
        assertEquals(reminder("Dad's 50th birthday", at(12, 5, 9)), parse("remind me next month about dad's 50th birthday"))
        assertEquals(reminder("2nd installment", at(11, 12, 9)), parse("remind me next week about the 2nd installment"))
    }

    @Test fun nextMonthOnAnOrdinalIsThatDayNextMonth() {
        assertEquals(reminder("Pay rent", at(12, 10, 9)), parse("remind me next month on the 10th to pay rent"))
        assertEquals(reminder("Pay rent", at(12, 10, 9)), parse("remind me to pay rent on the 10th next month"))
        assertEquals(reminder("Pay rent", at(12, 10, 9)), parse("remind me next month 10th to pay rent"))
        assertEquals(reminder("Pay rent", at(12, 10, 9)), parse("remind me 10th next month to pay rent"))
        assertEquals(reminder("Pay rent", at(12, 10, 9)), parse("remind me to pay rent 10th of next month"))
    }

    @Test fun bareHourBeatsASpacedPairInTheLabel() {
        for (text in listOf("alarm 6 for the 10 30 train", "wake me up 6 for the 10 30 train")) {
            val a = parse(text) as ParsedCommand.CreateAlarm
            assertEquals(text, 6 to 0, a.hour to a.minute)
        }
    }

    @Test fun atTimeBeatsAForPairInTheLabel() {
        val a = parse("wake me up at 6 for 10 30 train") as ParsedCommand.CreateAlarm
        assertEquals(6 to 0, a.hour to a.minute)
        val b = parse("alarm for 7 30") as ParsedCommand.CreateAlarm
        assertEquals(7 to 30, b.hour to b.minute)
    }

    @Test fun returnTripIsSpendingNotARefund() {
        for (text in listOf("return cab 200", "return flight 5000")) {
            val e = parse(text) as ParsedCommand.LogExpense
            assertEquals(text, ExpenseKind.EXPENSE, e.kind)
        }
    }

    @Test fun callDoesNotSwallowATrailingNumber() {
        assertEquals(ParsedCommand.OpenLink("tel:9876543210"), parse("call 9876543210 2 baar"))
    }

    @Test fun spacedTimeAfterALabel() {
        assertEquals(ParsedCommand.CreateAlarm(6, 30, 0, "Gym"), parse("gym alarm 6 30"))
        assertEquals(ParsedCommand.CreateAlarm(7, 15, 0, "Office"), parse("office alarm 7 15"))
        assertEquals(ParsedCommand.CreateAlarm(6, 30, 0, "Gym"), parse("alarm for gym 6 30"))
        assertEquals(ParsedCommand.CreateAlarm(6, 30, 0, "Gym"), parse("wake me up for gym 6 30"))
    }

    @Test fun boughtAnAlarmClockIsExpense() {
        assertEquals(spent(500, ExpenseCategory.OTHER, "Alarm clock"), parse("bought an alarm clock for 500"))
        assertEquals(spent(1200, ExpenseCategory.OTHER, "Alarm clock"), parse("paid for the alarm clock 1200"))
        invalid("bought tickets, wake me up in 20")
        invalid("bought an alarm clock, wake me up in 20")
    }

    // ---- Everything else ----

    @Test fun help() = assertTrue(parse("help") is ParsedCommand.Answer)

    @Test fun unknownFailsWithRuleNone() {
        val r = CommandParser.parse("what is the weather", now, apps)
        assertTrue(r.command is ParsedCommand.Invalid)
        assertEquals("none", r.rule)
    }

    @Test fun empty() = invalid("   ")

    @Test fun isFast() {
        val inputs = listOf("remind me to call mom tomorrow at 6pm", "kal lunch pe 300 kharch kiye", "wake me up at 6:30 on weekdays")
        repeat(200) { inputs.forEach { parse(it) } } // warm up
        val started = System.nanoTime()
        repeat(1_000) { inputs.forEach { parse(it) } }
        val perParseMicros = (System.nanoTime() - started) / 3_000 / 1_000
        println("CommandParser: $perParseMicros µs per request")
        assertTrue("took $perParseMicros µs per request", perParseMicros < 2_000)
    }
}
