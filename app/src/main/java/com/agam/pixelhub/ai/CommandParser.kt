package com.agam.pixelhub.ai

import com.agam.pixelhub.data.ExpenseCategory
import com.agam.pixelhub.data.ExpenseKind
import com.agam.pixelhub.expenses.Money
import com.agam.pixelhub.scheduling.Recurrence
import com.agam.pixelhub.scheduling.RecurrenceCalc
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Turns a typed or spoken request, in English or Hinglish, into a [ParsedCommand] with keyword
 * and pattern rules (the words are in [Lexicon]). It runs on the phone, with no network and no
 * AI model, in well under a millisecond. Anything it can't read becomes [ParsedCommand.Invalid]
 * with a reason the user sees straight away.
 */
object CommandParser {
    /** ₹1 crore; anything bigger is almost certainly a misheard amount. */
    private const val MAX_EXPENSE_PAISE = 1_00_00_000_00L

    /** When a reminder names no time at all. */
    private const val DEFAULT_HOUR = 9

    const val HELP_TEXT =
        "I understand English and Hinglish:\n" +
            "• Reminders: \"remind me to call mom at 6pm\", \"kal subah 7 baje dawai yaad dilana\"\n" +
            "• Alarms: \"wake me up at 6:30 on weekdays\", \"subah 6 baje utha dena\"\n" +
            "• Money: \"spent 250 on lunch\", \"add 30 to food\", \"salary 50000 mili\"\n" +
            "• People: \"paid naruna 30\", \"rahul ko 50 diye\", \"naruna paid me 30\", \"naruna ka hisaab\"\n" +
            "• Apps: \"open spotify\", \"whatsapp kholo\"\n" +
            "• Calls and links: \"call 9876543210\", \"navigate to india gate\", \"search cricket score\""

    const val NOT_UNDERSTOOD =
        "I didn't understand that. Try \"remind me to call mom at 6pm\", \"alarm at 6:30\", " +
            "\"spent 250 on lunch\" or \"open spotify\". Type \"help\" to see everything."

    /**
     * Reads [text] as of [now]. [appNames] (installed apps and saved shortcuts) lets a bare app
     * name like "spotify" open that app; [people] (the People ledger's names) lets any phrasing
     * about them become a ledger entry.
     */
    fun parse(
        text: String,
        now: ZonedDateTime = ZonedDateTime.now(),
        appNames: List<String> = emptyList(),
        people: List<String> = emptyList(),
    ): ParseResult {
        val s = Lexicon.normalize(text)
        if (s.isEmpty()) return ParseResult(ParsedCommand.Invalid("Type or say something first."), "empty")
        if (Lexicon.HELP.matches(s)) return ParseResult(ParsedCommand.Answer(HELP_TEXT), "help")
        url(text)?.let { return it }
        // Without the Hinglish rewrites, for names, places and queries: "aaj tak" is not "today tak".
        val plain = Lexicon.normalize(text, synonyms = false)

        // "remind me to wake up dad" is a reminder: whichever trigger comes first wins.
        val reminderAt = Lexicon.REMINDER.find(s)?.range?.first
        val alarmAt = Lexicon.ALARM.find(s)?.range?.first
        val triggerAt = listOfNotNull(reminderAt, alarmAt).minOrNull()
        if (triggerAt != null) {
            // "open reminders", "alarm kholo": an app, not a reminder or alarm. Only when the app is
            // the reminders or alarm one: "remind me to keep the shop open" is still a reminder.
            val target = (Lexicon.OPEN.matchEntire(plain) ?: Lexicon.OPEN_HI.matchEntire(plain))?.groupValues?.get(1)
            if (target != null && TRIGGER_APP.matches(target)) {
                openApp(plain, text)?.let { return ParseResult(it, "open_app") }
            }
            val triggered = if (reminderAt != null && (alarmAt == null || reminderAt <= alarmAt)) {
                ParseResult(reminder(s, text, now), "reminder")
            } else {
                ParseResult(alarm(s, text, now), "alarm")
            }
            // "spent 500 on alarm clock": the trigger word was a noun (there's no time to read), and
            // money came first. A reminder or alarm that parses always wins: in "paid 500 for gym,
            // remind me on 5 dec to renew" the date and time belong to the reminder. The amount
            // must come before the trigger ("bought tickets, wake me up in 20" isn't ₹20), unless
            // the only trigger word is "alarm clock": "bought an alarm clock for 500".
            val spentAt = SPENT_PAST.find(s)?.range?.first
            val onlyAlarmClock = ALARM_CLOCK.replace(s, " ")
                .let { !Lexicon.ALARM.containsMatchIn(it) && !Lexicon.REMINDER.containsMatchIn(it) }
            if (triggered.command is ParsedCommand.Invalid && spentAt != null && spentAt < triggerAt &&
                (onlyAlarmClock || money(s.substring(0, triggerAt), text, now)?.command is ParsedCommand.LogExpense)
            ) {
                money(s, text, now)?.let { return it }
            }
            return triggered
        }

        // A whole app name before the search rule: "google maps" opens Maps, not a search for "maps".
        appNames.firstOrNull { val n = Lexicon.normalize(it, synonyms = false); plain == n || plain == "$n app" }
            ?.let { return ParseResult(ParsedCommand.OpenApp(it), "app_name") }
        link(s, plain, text)?.let { return it }
        openApp(plain, text)?.let { return ParseResult(it, "open_app") }
        ledger(s, text, now, people)?.let { return it }
        money(s, text, now)?.let { return it }
        return ParseResult(ParsedCommand.Invalid(NOT_UNDERSTOOD), "none")
    }

    /** The only open targets that beat a reminder or alarm trigger: the apps themselves. */
    private val TRIGGER_APP = Regex("""^(?:(?:the|my)\s+)?(?:reminders?|alarms?|alarm\s+clock|clock)(?:\s+app)?$""")

    /**
     * Past-tense spending only: "buy milk, remind me at 6" and "kal doodh kharidna yaad dilana"
     * are still reminders.
     */
    private val SPENT_PAST = Regex("""\b(?:spent|paid|payed|bought|purchased|costs?)\b""")

    /** The thing you buy, not an alarm being set. */
    private val ALARM_CLOCK = Regex("""\balarm\s+clocks?\b""")

    /** "1 minute from now", rounded up to the next whole minute so it's never early. */
    internal fun ceilToMinute(t: LocalDateTime): LocalDateTime =
        t.truncatedTo(ChronoUnit.MINUTES).let { if (it == t) it else it.plusMinutes(1) }

    // ---- Reminders ----------------------------------------------------------------------

    private fun reminder(s: String, original: String, now: ZonedDateTime): ParsedCommand {
        val sc = Scan(s)
        sc.take(Lexicon.REMINDER)
        val repeat = DateTimeRules.repeat(sc)
        if (repeat?.unsupported == true) return ParsedCommand.Invalid(UNSUPPORTED_REPEAT)
        val rel = DateTimeRules.relative(sc)
        val date = DateTimeRules.date(sc, now.toLocalDate(), past = false)
        if (date?.impossible == true) return ParsedCommand.Invalid(NO_SUCH_DATE)
        val time = DateTimeRules.time(sc)
        val part = DateTimeRules.part(sc) ?: date?.part ?: repeat?.part
        val title = phrase(sc.rest, Lexicon.REMINDER_FILLERS, original)
        if (title.isEmpty()) return ParsedCommand.Invalid("What should I remind you about? Try \"remind me to call mom at 6pm\".")

        val nowLocal = now.toLocalDateTime().truncatedTo(ChronoUnit.MINUTES)
        var at: LocalDateTime
        if (rel?.minutes != null) {
            // From the real time, rounded up: from the start of the minute, "in 1 minute" could already be past.
            at = ceilToMinute(now.plusMinutes(rel.minutes).toLocalDateTime())
        } else {
            // "tomorrow at 9am" said at 00:30 means the coming morning.
            val lateNight = date?.fromTomorrow == true && now.hour < LATE_NIGHT_END
            val day = when {
                lateNight -> now.toLocalDate()
                date != null -> date.date
                rel?.months != null -> now.toLocalDate().plusMonths(rel.months)
                else -> rel?.days?.let { now.toLocalDate().plusDays(it) }
            }
            val clock = time?.let { DateTimeRules.resolve(it, part) }
            if (time != null && clock == null) return ParsedCommand.Invalid("That time doesn't look right. Try \"at 6:30 pm\".")
            val timePart = time?.part ?: part
            at = when {
                clock == null -> (day ?: now.toLocalDate()).atTime(part?.defaultHour ?: DEFAULT_HOUR, 0)
                // "tonight at 12", "kal raat 2 baje": the small hours after that night, not before it.
                clock.certain && day != null && clock.hour < 6 && timePart == DayPart.NIGHT ->
                    day.plusDays(1).atTime(clock.hour, clock.minute)
                clock.certain -> (day ?: now.toLocalDate()).atTime(clock.hour, clock.minute)
                // "every day at 8": the same hour whenever it's said, not am or pm by the clock.
                repeat != null -> (day ?: now.toLocalDate()).atTime(DateTimeRules.likelyHour(clock.hour), clock.minute)
                // "today at 8" said at 9am: 8pm, the one still ahead today.
                day == now.toLocalDate() -> DateTimeRules.nextOccurrence(clock.hour, clock.minute, nowLocal)
                    .takeIf { it.toLocalDate() == day } ?: day.atTime(DateTimeRules.likelyHour(clock.hour), clock.minute)
                day != null -> day.atTime(DateTimeRules.likelyHour(clock.hour), clock.minute)
                else -> DateTimeRules.nextOccurrence(clock.hour, clock.minute, nowLocal)
            }
            // "at 9am" said at 10am, with no day: tomorrow. Likewise "tomorrow at 12:15am" said at 00:30.
            if ((day == null || lateNight) && repeat == null && !at.isAfter(nowLocal)) at = at.plusDays(1)
        }
        val recurrence = repeat?.toRecurrence(at.dayOfWeek) ?: Recurrence.NONE
        if (recurrence.isRepeating) {
            at = RecurrenceCalc.nextTrigger(recurrence, at, now)?.toLocalDateTime()?.truncatedTo(ChronoUnit.MINUTES) ?: at
        }
        val inMinutes = rel?.minutes?.takeIf { !recurrence.isRepeating }
        return ParsedCommand.CreateReminder(title, at, recurrence, inMinutes = inMinutes)
    }

    /** Before this hour, "tomorrow" may mean the coming morning (it's already the next day). */
    private const val LATE_NIGHT_END = 5

    private const val UNSUPPORTED_REPEAT =
        "I can only repeat daily, weekly, or every few days or weeks. For hourly or monthly ones, set each one separately."

    // ---- Alarms -------------------------------------------------------------------------

    private fun alarm(s: String, original: String, now: ZonedDateTime): ParsedCommand {
        val sc = Scan(s)
        sc.take(Lexicon.ALARM)
        val repeat = DateTimeRules.repeat(sc)
        val rel = DateTimeRules.relative(sc)
        val date = DateTimeRules.date(sc, now.toLocalDate(), past = false)
        val time = if (rel?.minutes == null) DateTimeRules.alarmTime(sc) else DateTimeRules.time(sc)
        val part = DateTimeRules.part(sc) ?: date?.part ?: repeat?.part
        val label = phrase(sc.rest, Lexicon.ALARM_FILLERS, original)

        if (repeat?.unsupported == true) return ParsedCommand.Invalid(UNSUPPORTED_REPEAT)
        if (date?.impossible == true) return ParsedCommand.Invalid(NO_SUCH_DATE)

        val clock = when {
            // Rounded up from the real time, so "in 1 minute" said at 10:00:50 isn't 10:01.
            rel?.minutes != null -> ceilToMinute(now.plusMinutes(rel.minutes).toLocalDateTime())
                .let { HourMinute(it.hour, it.minute, certain = true) }
            time != null -> DateTimeRules.resolve(time, part)
                ?: return ParsedCommand.Invalid("That time doesn't look right. Try \"alarm at 6:30\".")
            else -> return ParsedCommand.Invalid("What time? Try \"alarm at 6:30\" or \"wake me up at 7 on weekdays\".")
        }
        var hour = clock.hour
        // An alarm without repeat days rings once, the next time its clock comes round, so it can't
        // wait for a later date: "wake me up on 12 nov at 7" used to ring at the next 7:00 instead.
        // "on friday" (said on Thursday) or "tomorrow at 7" is fine: that is the next ring. A single
        // weekday further off used to repeat every week, which nobody asked for.
        val onDay = if (repeat == null) {
            date?.date ?: rel?.months?.let { now.toLocalDate().plusMonths(it) } ?: rel?.days?.let { now.toLocalDate().plusDays(it) }
        } else {
            null
        }
        if (onDay != null) {
            val nowLocal = now.toLocalDateTime()
            fun nextRing(h: Int): LocalDateTime =
                now.toLocalDate().atTime(h, clock.minute).let { if (it.isAfter(nowLocal)) it else it.plusDays(1) }
            // "tonight at 12" rings in the small hours after that night.
            val smallHours = clock.hour < 6 && (time?.part ?: part) == DayPart.NIGHT
            // "tomorrow at 7" said at 00:30 means the coming morning, today's date.
            val lateNight = date?.fromTomorrow == true && now.hour < LATE_NIGHT_END
            val ringsOn = when {
                smallHours -> setOf(onDay, onDay.plusDays(1))
                lateNight -> setOf(onDay.minusDays(1), onDay)
                else -> setOf(onDay)
            }
            // "tomorrow at 7" with no am or pm: whichever of 7:00 and 19:00 rings on that day.
            val hours = if (clock.certain || clock.hour !in 1..11) listOf(clock.hour) else listOf(clock.hour, clock.hour + 12)
            hour = hours.firstOrNull { nextRing(it).toLocalDate() in ringsOn } ?: return ParsedCommand.Invalid(
                "An alarm only rings at the next time you give, so it can't wait for a date. Set a reminder instead, " +
                    "like \"remind me on 12 nov at 7am to wake up\", or say \"every monday\" for a weekly alarm.",
            )
        }
        val mask = repeat?.toAlarmMask(date?.date?.dayOfWeek ?: now.dayOfWeek) ?: 0
        return ParsedCommand.CreateAlarm(hour, clock.minute, mask, label, inMinutes = rel?.minutes?.takeIf { repeat == null })
    }

    private const val NO_SUCH_DATE = "That date doesn't exist. Check the day and month, like \"30 april\"."

    // ---- Money --------------------------------------------------------------------------

    // Two lookbehinds, not [\d.]: a number may follow "rs." ("Rs.500") but not a digit and a dot,
    // so "1.5" is never read again as 5.
    private val AMOUNT = Regex(
        """(?:\b(rs|inr)\.?\s*)?(?<!\d)(?<!\d\.)(\d+(?:\.\d{1,2})?)(?:\s*(k|thousand|hazaar|hazar|hajar|lakhs?|lacs?|crores?|cr))?""" +
            """(?:\s*(rs|rupees?|rupaye|rupay|rupaiye|rupiya|inr|bucks|/-))?(?![a-z\d])""",
    )

    /** "2k", "1.5 lakh", "2 cr": what the amount's unit multiplies it by ("" for plain rupees). */
    private fun unitMultiplier(unit: String): Double = when {
        unit.isEmpty() -> 1.0
        unit.startsWith("la") -> 1_00_000.0
        unit.startsWith("c") -> 1_00_00_000.0
        else -> 1_000.0
    }

    // "spent 300 tomorrow": saved, it would sit outside this month's totals and budget.
    private const val FUTURE_MONEY = "That date is still ahead. Log money once it's spent, or set a reminder."

    /** Words that put a price right after them, so a bare number there is the amount. */
    private val AMOUNT_CUE = Regex("""\b(?:spent|spend|paid|pay|payed|cost|costs|gave|add|added|for|worth|of)\s+$""")

    /** An expense or income, or null if the message isn't about money at all. */
    private fun money(s: String, original: String, now: ZonedDateTime): ParseResult? {
        val incomeWord = Lexicon.INCOME.containsMatchIn(s)
        val expenseWord = Lexicon.EXPENSE.containsMatchIn(s)
        val sc = Scan(s)
        // Dates first, so "12 nov" isn't read as ₹12.
        val date = DateTimeRules.date(sc, now.toLocalDate(), past = true)
        val matches = AMOUNT.findAll(sc.rest).toList()
        val m = matches.firstOrNull { it.groupValues[1].isNotEmpty() || it.groupValues[3].isNotEmpty() || it.groupValues[4].isNotEmpty() }
            // "bought 2 tickets for 500": the number after "for" (or "spent", "paid") is the price,
            // not the count before it.
            ?: matches.firstOrNull { AMOUNT_CUE.containsMatchIn(sc.rest.substring(0, it.range.first)) }
            ?: matches.firstOrNull()
        if (m == null) {
            if (!incomeWord && !expenseWord) return null
            val rule = if (incomeWord) "income" else "expense"
            return ParseResult(ParsedCommand.Invalid("How much? Try \"spent 250 on lunch\" or \"got 5000 salary\"."), rule)
        }
        // A bare number counts as money only next to a money word, a category ("uber 180") or ₹.
        val marked = m.groupValues[1].isNotEmpty() || m.groupValues[3].isNotEmpty() || m.groupValues[4].isNotEmpty()
        if (!incomeWord && !expenseWord && !marked && categoryIn(s, null) == null) return null
        if (date?.impossible == true) return ParseResult(ParsedCommand.Invalid(NO_SUCH_DATE), "expense")
        if (date != null && date.date.isAfter(now.toLocalDate())) return ParseResult(ParsedCommand.Invalid(FUTURE_MONEY), "expense")
        val paise = Money.fromRupees(m.groupValues[2].toDouble() * unitMultiplier(m.groupValues[3]))?.takeIf { it < MAX_EXPENSE_PAISE }
            ?: return ParseResult(ParsedCommand.Invalid("That amount doesn't look right."), "expense")
        sc.cut(m.range)
        sc.takeAll(Lexicon.MONEY_VERBS)

        val named = categoryIn(s, null)
        // A spending word said before any income word makes it spending, whatever the category:
        // "paid 2000 for project", "bijli ka bill 2000 aaya" (the bill came, it wasn't money in).
        val spentAt = Lexicon.SPENT.find(s)?.range?.first
        val incomeAt = Lexicon.INCOME.find(s)?.range?.first
        val kind = when {
            spentAt != null && (incomeAt == null || spentAt < incomeAt) -> ExpenseKind.EXPENSE
            incomeWord || named?.isIncome == true -> ExpenseKind.INCOME
            else -> ExpenseKind.EXPENSE
        }
        val category = categoryIn(s, kind) ?: ExpenseCategory.OTHER
        val note = phrase(sc.rest, Lexicon.MONEY_FILLERS, original)
        val at = date?.date?.takeIf { it != now.toLocalDate() }
            ?.atTime(now.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
        val rule = if (kind == ExpenseKind.INCOME) "income" else "expense"
        return ParseResult(ParsedCommand.LogExpense(paise, category, note, at, kind), rule)
    }

    /** One pattern per category matching any of its keywords, compiled once. */
    private val CATEGORY_PATTERNS: Map<ExpenseCategory, Regex> = Lexicon.CATEGORY_WORDS.mapValues { (_, words) ->
        Regex("""\b(?:${words.joinToString("|") { Regex.escape(it) }})\b""")
    }

    /** The category whose keyword appears first in [s]; with [kind], only that kind's categories. */
    private fun categoryIn(s: String, kind: ExpenseKind?): ExpenseCategory? {
        val allowed = kind?.let { ExpenseCategory.forKind(it).toSet() }
        return CATEGORY_PATTERNS
            .filterKeys { allowed == null || it in allowed }
            .mapNotNull { (category, re) -> re.find(s)?.let { it.range.first to category } }
            .minByOrNull { it.first }
            ?.second
    }

    // ---- People ledger ------------------------------------------------------------------

    /** A ledger phrase: § is the person; [sign] +1 they owe you, -1 you owe them. */
    private class LedgerRule(pattern: String, val sign: Int, val anyName: Boolean) {
        val re = Regex(pattern.replace("§", NAME))
    }

    private const val NAME = """([a-z][a-z'.-]*)"""

    /** Stands in for a saved person's (possibly multi-word) name so every rule sees one word. */
    private const val SAVED = "zqsavedperson"

    private const val AMOUNT_NEXT = """(?=\s*(?:rs\.?\s*)?\d)"""

    /**
     * Rules with [LedgerRule.anyName] also accept a name that isn't saved yet ("paid rahul 50"),
     * so they stay strict. The others only fire for saved people, so "mummy se 2000 mile" stays
     * income unless Mummy is in People.
     */
    private val LEDGER_RULES = listOf(
        LedgerRule("""\b(?:paid|gave|lent|lend|sent|transferred)\s+§$AMOUNT_NEXT""", +1, anyName = true),
        LedgerRule("""\b§\s+owes\s+me\b""", +1, anyName = true),
        LedgerRule("""\b§\s+ko\s+(?:.*\s)?(?:de\s+diye|de\s+diya|diye|diya|udhaar|udhar|bheje|bheja)\b""", +1, anyName = true),
        LedgerRule("""\bborrowed\s+(?:.*\s)?from\s+§\b""", -1, anyName = true),
        LedgerRule("""\bi\s+owe\s+§\b""", -1, anyName = true),
        LedgerRule("""\b§\s+se\s+(?:.*\s)?(?:udhaar|udhar)\b""", -1, anyName = true),
        LedgerRule("""\b§\s+(?:paid|gave|lent|sent|transferred)\s+(?:me|mujhe)\b""", -1, anyName = false),
        LedgerRule("""\b§\s+paid\s+(?:.*\s)?for\s+me\b""", -1, anyName = false),
        LedgerRule("""\b§\s+ne\s+(?:.*\s)?(?:de\s+diye|diye|diya|paid|bheje|bheja|pay\s+kiye|kiye)\b""", -1, anyName = false),
        LedgerRule("""\b(?:got|received|recieved)\s+(?:.*\s)?from\s+§\b""", -1, anyName = false),
        LedgerRule("""\b§\s+se\s+(?:.*\s)?(?:mile|mila|mili|le\s+liye|liye|liya|aaye|aaya)\b""", -1, anyName = false),
        LedgerRule("""\b(?:paid|gave|lent|sent|spent)\s+(?:.*\s)?(?:to|for)\s+§\b""", +1, anyName = false),
        LedgerRule("""\b§\s+ke\s+liye\b""", +1, anyName = false),
    )

    private val BALANCE_RULES = listOf(
        """^§\s+(?:ka|ki|ke|se)\s+(?:hisaab|hisab|balance|kitna(?:\s+(?:lena|dena|baaki|baki))?)(?:\s+(?:hai|batao|bata|kya\s+hai))?$""",
        """^how\s+much\s+(?:does\s+)?§\s+owes?(?:\s+me)?$""",
        """^how\s+much\s+do\s+i\s+owe\s+§$""",
        """^(?:balance|hisaab|hisab)\s+(?:with\s+|of\s+|for\s+)?§$""",
        """^§\s+balance$""",
    ).map { Regex(it.replace("§", NAME)) }

    private fun isNameWord(w: String) = w.length >= 2 && w.all { it.isLetter() } && w !in Lexicon.NOT_A_NAME

    /** A ledger entry or balance question, or null when the message isn't about a person. */
    private fun ledger(s: String, original: String, now: ZonedDateTime, people: List<String>): ParseResult? {
        // A saved name anywhere in the message: the longest match wins ("Naruna S" over "Naruna").
        val match = people.sortedByDescending { it.length }.firstNotNullOfOrNull { p ->
            Regex("""\b${Regex.escape(Lexicon.normalize(p))}\b""").takeIf { it.containsMatchIn(s) }?.let { p to it }
        }
        val saved: String? = match?.first
        val text = match?.second?.replace(s, SAVED) ?: s
        fun nameOf(word: String): String? = when {
            saved != null -> saved.takeIf { word == SAVED }
            isNameWord(word) -> phrase(word, emptySet(), original)
            else -> null
        }

        for (re in BALANCE_RULES) {
            val m = re.find(text) ?: continue
            val name = nameOf(m.groupValues[1]) ?: continue
            return ParseResult(ParsedCommand.LedgerBalance(name), "ledger_balance")
        }

        var name: String? = null
        var sign = 0
        for (rule in LEDGER_RULES) {
            if (saved == null && !rule.anyName) continue
            val word = rule.re.find(text)?.groupValues?.get(1) ?: continue
            name = nameOf(word) ?: continue
            sign = rule.sign
            break
        }
        if (name == null) return null

        // Take the person out, then read the date, the amount and what it was for.
        val nameWord = if (saved != null) SAVED else name.lowercase()
        val sc = Scan(Regex("""\b${Regex.escape(nameWord)}\b""").replaceFirst(text, " "))
        val date = DateTimeRules.date(sc, now.toLocalDate(), past = true)
        if (date?.impossible == true) return ParseResult(ParsedCommand.Invalid(NO_SUCH_DATE), "ledger")
        if (date != null && date.date.isAfter(now.toLocalDate())) return ParseResult(ParsedCommand.Invalid(FUTURE_MONEY), "ledger")
        val m = AMOUNT.findAll(sc.rest).toList().let { all ->
            all.firstOrNull { it.groupValues[1].isNotEmpty() || it.groupValues[3].isNotEmpty() || it.groupValues[4].isNotEmpty() }
                ?: all.firstOrNull()
        } ?: return ParseResult(ParsedCommand.Invalid("How much? Try \"paid $name 30\"."), "ledger")
        val paise = Money.fromRupees(m.groupValues[2].toDouble() * unitMultiplier(m.groupValues[3]))?.takeIf { it < MAX_EXPENSE_PAISE }
            ?: return ParseResult(ParsedCommand.Invalid("That amount doesn't look right."), "ledger")
        sc.cut(m.range)
        sc.takeAll(Lexicon.LEDGER_WORDS)
        val note = phrase(sc.rest, Lexicon.LEDGER_FILLERS, original)
        val at = date?.date?.takeIf { it != now.toLocalDate() }?.atTime(now.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
        return ParseResult(ParsedCommand.LedgerEntry(name, sign * paise, note, at, isNewPerson = saved == null), "ledger")
    }

    // ---- Apps and links -----------------------------------------------------------------

    private fun openApp(s: String, original: String): ParsedCommand? {
        val m = Lexicon.OPEN.matchEntire(s) ?: Lexicon.OPEN_HI.matchEntire(s) ?: return null
        val name = phrase(m.groupValues[1], emptySet(), original)
        return if (name.isEmpty()) null else ParsedCommand.OpenApp(name)
    }

    /** Lexicon.SMS, read against the raw text so the body keeps its case and punctuation. */
    private val SMS_BODY = Regex(
        """^\s*(?:text|sms|message|msg)\s+(?:to\s+)?(${Lexicon.PHONE_NUM})(?:\s+(?:saying\s+|that\s+)?(.+))?$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** A link on its own, kept exactly as typed: paths and query strings are case-sensitive. */
    private fun url(original: String): ParseResult? {
        val m = Lexicon.URL_ONLY.matchEntire(original) ?: return null
        val parts = URL_PARTS.matchEntire(m.groupValues[1]) ?: return null
        // Scheme and host can be lower-cased safely (Android matches them case-sensitively); the path can't.
        val url = parts.groupValues[1].ifEmpty { "https://" }.lowercase() + parts.groupValues[2].lowercase() + parts.groupValues[3]
        return ParseResult(ParsedCommand.OpenLink(url), "url")
    }

    /** Scheme (maybe none), host, then the path as typed. */
    private val URL_PARTS = Regex("""^((?:https?://)?)([^/]*)(.*)$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /** [s] is the normalised text; [plain] the same without synonyms, for places and queries. */
    private fun link(s: String, plain: String, original: String): ParseResult? {
        Lexicon.EMAIL.find(s)?.let { m ->
            if (Lexicon.MAIL_WORD.containsMatchIn(s) || s == m.value) return ParseResult(ParsedCommand.OpenLink("mailto:${m.value}"), "email")
        }
        Lexicon.SMS.matchEntire(s)?.let { m ->
            // The message as typed: the normalised text is lower-case and has lost its ! and ?.
            val typed = SMS_BODY.find(original) ?: m
            val (numberText, overflow) = splitNumber(typed.groupValues[1])
            val number = digits(numberText)
            if (number.count { it.isDigit() } !in PHONE_DIGITS) {
                return ParseResult(ParsedCommand.Invalid("That number doesn't look right. Try \"text 9876543210 running late\"."), "sms")
            }
            val body = listOf(overflow, typed.groupValues[2].trim()).filter { it.isNotEmpty() }.joinToString(" ")
            val uri = if (body.isEmpty()) "sms:$number" else "sms:$number?body=${encode(body)}"
            return ParseResult(ParsedCommand.OpenLink(uri), "sms")
        }
        (Lexicon.CALL.matchEntire(s) ?: Lexicon.CALL_HI.matchEntire(s))?.let { m ->
            // Split: "call 9876543210 2 baar" must not dial 98765432102.
            val number = Lexicon.PHONE.find(m.groupValues[1])?.value?.let { digits(splitNumber(it).first) }
                ?.takeIf { n -> n.count { it.isDigit() } in PHONE_DIGITS }
                ?: return ParseResult(ParsedCommand.Invalid("I can only call phone numbers. Try \"call 9876543210\"."), "call")
            return ParseResult(ParsedCommand.OpenLink("tel:$number"), "call")
        }
        (Lexicon.NAVIGATE.matchEntire(plain) ?: Lexicon.NAVIGATE_HI.matchEntire(plain))?.let { m ->
            val place = (m.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: "").trim()
            if (place.isNotEmpty()) return ParseResult(ParsedCommand.OpenLink("google.navigation:q=${encode(place)}"), "navigate")
        }
        // "paid 130 for google" is spending at Google, not a search for "paid 130 for".
        val aboutMoney = (Lexicon.EXPENSE.containsMatchIn(s) || Lexicon.INCOME.containsMatchIn(s)) && s.any { it.isDigit() }
        if (!aboutMoney) (Lexicon.SEARCH.matchEntire(plain) ?: Lexicon.SEARCH_HI.matchEntire(plain))?.let { m ->
            val query = m.groupValues[1].trim()
            if (query.isNotEmpty()) return ParseResult(ParsedCommand.OpenLink("https://www.google.com/search?q=${encode(query)}"), "search")
        }
        return null
    }

    /** Shortest local number to the longest international one. */
    private val PHONE_DIGITS = 7..15

    /**
     * Splits off digit groups after the number has ten digits: in "text 9876543210 100 rs bhej"
     * the 100 is part of the message, not a 13-digit number. A leading country code isn't one of
     * the ten: "+91 98 76 54 32 10" is all number. Without a "+", "91" or "0" counts as one only
     * when the ten digits follow it.
     */
    private fun splitNumber(number: String): Pair<String, String> {
        val groups = number.split(' ').filter { it.isNotEmpty() }
        fun digitsIn(g: String) = g.count { it.isDigit() }
        val first = groups.firstOrNull().orEmpty()
        val code = digitsIn(first).takeIf { COUNTRY_CODE.matches(first) } ?: 0
        val limit = if (code > 0 && (first.startsWith('+') || groups.sumOf { digitsIn(it) } >= 10 + code)) 10 + code else 10
        var count = 0
        val taken = groups.takeWhile { g -> (count < limit).also { count += digitsIn(g) } }
        return taken.joinToString(" ") to groups.drop(taken.size).joinToString(" ")
    }

    /** A country code (or trunk 0) written as its own group. */
    private val COUNTRY_CODE = Regex("""^(?:\+\d{1,3}|91|0)$""")

    private fun digits(number: String) = number.filter { it.isDigit() || it == '+' }

    private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

    // ---- Text helpers -------------------------------------------------------------------

    /**
     * What's left of the message, without filler words at either end, in the user's own
     * capitalisation ("call Rahul"), first letter upper-case.
     */
    private fun phrase(rest: String, fillers: Set<String>, original: String): String {
        var words = rest.split(' ')
            .map { it.trim(',', '.', '-', ':', '\'') }
            .filter { it.isNotEmpty() }
        while (words.isNotEmpty() && words.first() in fillers) words = words.drop(1)
        while (words.isNotEmpty() && words.last() in fillers) words = words.dropLast(1)
        // Taking out the amount can leave two fillers side by side ("refund of from amazon"). A pair
        // the user typed themselves stays: "go to the gym" must not become "Go the gym".
        val said = " ${Lexicon.normalize(original)} "
        words = words.filterIndexed { i, w ->
            val next = words.getOrNull(i + 1)
            !(w in fillers && next in fillers && " $w $next " !in said)
        }
        return words.joinToString(" ") { w ->
            Regex("""(?i)(?<![\p{L}\p{N}])${Regex.escape(w)}(?![\p{L}\p{N}])""").find(original)?.value ?: w
        }.replaceFirstChar { it.titlecase() }
    }
}
