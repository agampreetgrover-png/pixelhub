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

/** Every case runs as of Thursday 5 Nov 2026, 10:00 in India. */
class CommandParserTest {
    private val now = ZonedDateTime.of(2026, 11, 5, 10, 0, 0, 0, ZoneId.of("Asia/Kolkata"))
    private val apps = listOf("Spotify", "YouTube", "WhatsApp", "Google Maps")
    private fun parse(text: String) = CommandParser.parse(text, now, apps).command
    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, month, day, hour, minute)
    private fun reminder(title: String, at: LocalDateTime, rec: Recurrence = Recurrence.NONE) = ParsedCommand.CreateReminder(title, at, rec)
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

    @Test fun inTwoHours() = assertEquals(reminder("Pay rent", at(11, 5, 12)), parse("remind me to pay rent in 2 hours"))

    @Test fun inTwentyMinutesBeforeTitle() = assertEquals(reminder("Check the oven", at(11, 5, 10, 20)), parse("remind me in 20 min to check the oven"))

    @Test fun halfAnHour() = assertEquals(reminder("Stretch", at(11, 5, 10, 30)), parse("remind me to stretch in half an hour"))

    @Test fun decimalHours() = assertEquals(reminder("Stretch", at(11, 5, 11, 30)), parse("remind me to stretch in 1.5 hours"))

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

    @Test fun ghanteBaad() = assertEquals(reminder("Khana banana", at(11, 5, 12)), parse("2 ghante baad khana banana yaad dilana"))

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

    @Test fun alarmInMinutes() = assertEquals(ParsedCommand.CreateAlarm(10, 30, 0, ""), parse("wake me up in 30 minutes"))

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

    private val people = listOf("Naruna", "Mummy", "Rahul Sharma")
    private fun ledger(text: String) = CommandParser.parse(text, now, apps, people).command
    private fun owesMe(name: String, rupees: Long, note: String = "", new: Boolean = false, at: LocalDateTime? = null) =
        ParsedCommand.LedgerEntry(name, rupees * 100, note, at, isNewPerson = new)
    private fun iOwe(name: String, rupees: Long, note: String = "", new: Boolean = false, at: LocalDateTime? = null) =
        ParsedCommand.LedgerEntry(name, -rupees * 100, note, at, isNewPerson = new)

    @Test fun paidSavedPerson() = assertEquals(owesMe("Naruna", 30), ledger("i paid naruna 30"))

    @Test fun paidWithNote() = assertEquals(owesMe("Naruna", 30, "Lunch"), ledger("paid Naruna 30 for lunch"))

    @Test fun paidNewPerson() = assertEquals(owesMe("Rahul", 50, new = true), parse("paid rahul 50"))

    @Test fun lentNewPerson() = assertEquals(owesMe("Priya", 500, new = true), parse("lent priya 500"))

    @Test fun owesMe() = assertEquals(owesMe("Amit", 200, new = true), parse("amit owes me 200"))

    @Test fun koDiyeNewPerson() = assertEquals(owesMe("Rahul", 50, new = true), parse("rahul ko 50 diye"))

    @Test fun koDiyeWithNoteAndDate() =
        assertEquals(owesMe("Naruna", 80, "Auto", at = at(11, 4, 10)), ledger("kal naruna ko auto ke liye 80 diye"))

    @Test fun keLiye() = assertEquals(owesMe("Naruna", 120, "Movie ticket"), ledger("naruna ke liye 120 ka movie ticket"))

    @Test fun paidForSavedPerson() = assertEquals(owesMe("Naruna", 60, "Coffee"), ledger("paid 60 for naruna coffee"))

    @Test fun theyPaidMe() = assertEquals(iOwe("Naruna", 30), ledger("naruna paid me 30"))

    @Test fun theyPaidForMe() = assertEquals(iOwe("Naruna", 150, "Dinner"), ledger("naruna paid 150 for me dinner"))

    @Test fun gotFrom() = assertEquals(iOwe("Naruna", 100), ledger("got 100 from naruna"))

    @Test fun seMileSavedIsLedger() = assertEquals(iOwe("Mummy", 2000), ledger("mummy se 2000 mile"))

    @Test fun neDiye() = assertEquals(iOwe("Naruna", 200), ledger("naruna ne mere liye 200 diye"))

    @Test fun borrowedNewPerson() = assertEquals(iOwe("Karan", 300, new = true), parse("borrowed 300 from karan"))

    @Test fun iOweNewPerson() = assertEquals(iOwe("Karan", 40, new = true), parse("i owe karan 40"))

    @Test fun udhaarLiya() = assertEquals(iOwe("Karan", 500, new = true), parse("karan se 500 udhaar liye"))

    @Test fun multiWordSavedName() = assertEquals(owesMe("Rahul Sharma", 90), ledger("paid rahul sharma 90"))

    @Test fun ledgerNeedsAmount() = assertTrue(ledger("paid naruna") is ParsedCommand.Invalid)

    @Test fun balanceHinglish() = assertEquals(ParsedCommand.LedgerBalance("Naruna"), ledger("naruna ka hisaab"))

    @Test fun balanceEnglish() = assertEquals(ParsedCommand.LedgerBalance("Naruna"), ledger("how much does naruna owe me"))

    @Test fun balanceUnknownName() = assertEquals(ParsedCommand.LedgerBalance("Vikram"), parse("vikram ka hisaab"))

    // Guards: these stay what they were before the ledger existed.

    @Test fun lunchStaysExpense() = assertEquals(spent(250, ExpenseCategory.FOOD, "Lunch"), ledger("spent 250 on lunch"))

    @Test fun billStaysExpense() =
        assertEquals(spent(1200, ExpenseCategory.BILLS, "Electricity bill"), ledger("paid ₹1,200 for electricity bill"))

    @Test fun merchantStaysExpense() = assertTrue(ledger("paid swiggy 300") is ParsedCommand.LogExpense)

    @Test fun rentStaysExpense() = assertTrue(ledger("paid rent 15000") is ParsedCommand.LogExpense)

    @Test fun unsavedSeMileStaysIncome() = assertEquals(earned(2000, ExpenseCategory.OTHER, "Papa"), ledger("papa se 2000 mile"))

    @Test fun savedNameInPlainExpenseStaysExpense() =
        assertTrue(ledger("spent 250 on lunch with naruna") is ParsedCommand.LogExpense)

    @Test fun remindAboutPersonIsReminder() =
        assertTrue(ledger("remind me to pay naruna 30 tomorrow") is ParsedCommand.CreateReminder)

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
