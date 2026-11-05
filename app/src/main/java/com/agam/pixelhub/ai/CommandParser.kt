package com.agam.pixelhub.ai

import com.agam.pixelhub.data.ExpenseCategory
import com.agam.pixelhub.data.ExpenseKind
import com.agam.pixelhub.expenses.Money
import com.agam.pixelhub.scheduling.Days
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

        // "remind me to wake up dad" is a reminder: whichever trigger comes first wins.
        val reminderAt = Lexicon.REMINDER.find(s)?.range?.first
        val alarmAt = Lexicon.ALARM.find(s)?.range?.first
        if (reminderAt != null && (alarmAt == null || reminderAt <= alarmAt)) return ParseResult(reminder(s, text, now), "reminder")
        if (alarmAt != null) return ParseResult(alarm(s, text, now), "alarm")

        link(s, text)?.let { return it }
        openApp(s, text)?.let { return ParseResult(it, "open_app") }
        ledger(s, text, now, people)?.let { return it }
        money(s, text, now)?.let { return it }
        appNames.firstOrNull { s == it.lowercase() || s == "${it.lowercase()} app" }
            ?.let { return ParseResult(ParsedCommand.OpenApp(it), "app_name") }
        return ParseResult(ParsedCommand.Invalid(NOT_UNDERSTOOD), "none")
    }

    // ---- Reminders ----------------------------------------------------------------------

    private fun reminder(s: String, original: String, now: ZonedDateTime): ParsedCommand {
        val sc = Scan(s)
        sc.take(Lexicon.REMINDER)
        val repeat = DateTimeRules.repeat(sc)
        val rel = DateTimeRules.relative(sc)
        val date = DateTimeRules.date(sc, now.toLocalDate(), past = false)
        val time = DateTimeRules.time(sc)
        val part = DateTimeRules.part(sc) ?: date?.part ?: repeat?.part
        val title = phrase(sc.rest, Lexicon.REMINDER_FILLERS, original)
        if (title.isEmpty()) return ParsedCommand.Invalid("What should I remind you about? Try \"remind me to call mom at 6pm\".")

        val nowLocal = now.toLocalDateTime().truncatedTo(ChronoUnit.MINUTES)
        var at: LocalDateTime
        if (rel?.minutes != null) {
            at = nowLocal.plusMinutes(rel.minutes)
        } else {
            val day = date?.date ?: rel?.days?.let { now.toLocalDate().plusDays(it) }
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
            // "at 9am" said at 10am, with no day: tomorrow.
            if (day == null && repeat == null && !at.isAfter(nowLocal)) at = at.plusDays(1)
        }
        val recurrence = repeat?.toRecurrence(at.dayOfWeek) ?: Recurrence.NONE
        if (recurrence.isRepeating) {
            at = RecurrenceCalc.nextTrigger(recurrence, at, now)?.toLocalDateTime()?.truncatedTo(ChronoUnit.MINUTES) ?: at
        }
        return ParsedCommand.CreateReminder(title, at, recurrence)
    }

    // ---- Alarms -------------------------------------------------------------------------

    private fun alarm(s: String, original: String, now: ZonedDateTime): ParsedCommand {
        val sc = Scan(s)
        sc.take(Lexicon.ALARM)
        val repeat = DateTimeRules.repeat(sc)
        val rel = DateTimeRules.relative(sc)
        val date = DateTimeRules.date(sc, now.toLocalDate(), past = false)
        val time = DateTimeRules.time(sc)
        val part = DateTimeRules.part(sc) ?: date?.part ?: repeat?.part
        val label = phrase(sc.rest, Lexicon.ALARM_FILLERS, original)

        val (hour, minute) = when {
            rel?.minutes != null -> now.plusMinutes(rel.minutes).let { it.hour to it.minute }
            time != null -> DateTimeRules.resolve(time, part)?.let { it.hour to it.minute }
                ?: return ParsedCommand.Invalid("That time doesn't look right. Try \"alarm at 6:30\".")
            else -> return ParsedCommand.Invalid("What time? Try \"alarm at 6:30\" or \"wake me up at 7 on weekdays\".")
        }
        val day = date?.date?.dayOfWeek ?: now.dayOfWeek
        val mask = when {
            repeat != null -> repeat.toAlarmMask(day)
            date?.fromWeekday == true -> Days.bit(day)
            else -> 0
        }
        return ParsedCommand.CreateAlarm(hour, minute, mask, label)
    }

    // ---- Money --------------------------------------------------------------------------

    private val AMOUNT = Regex(
        """(?:\b(rs|inr)\.?\s*)?(?<![\d.])(\d+(?:\.\d{1,2})?)(?:\s*(k|thousand|hazaar|hazar|hajar|lakhs?|lacs?))?""" +
            """(?:\s*(rs|rupees?|rupaye|rupay|rupaiye|rupiya|inr|bucks|/-))?(?![a-z\d])""",
    )

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
        val multiplier = when {
            m.groupValues[3].isEmpty() -> 1.0
            m.groupValues[3].startsWith("la") -> 1_00_000.0
            else -> 1_000.0
        }
        val paise = Money.fromRupees(m.groupValues[2].toDouble() * multiplier)?.takeIf { it < MAX_EXPENSE_PAISE }
            ?: return ParseResult(ParsedCommand.Invalid("That amount doesn't look right."), "expense")
        sc.cut(m.range)
        sc.takeAll(Lexicon.MONEY_VERBS)

        val named = categoryIn(s, null)
        val kind = if (incomeWord || named?.isIncome == true) ExpenseKind.INCOME else ExpenseKind.EXPENSE
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
        val m = AMOUNT.findAll(sc.rest).toList().let { all ->
            all.firstOrNull { it.groupValues[1].isNotEmpty() || it.groupValues[3].isNotEmpty() || it.groupValues[4].isNotEmpty() }
                ?: all.firstOrNull()
        } ?: return ParseResult(ParsedCommand.Invalid("How much? Try \"paid $name 30\"."), "ledger")
        val multiplier = when {
            m.groupValues[3].isEmpty() -> 1.0
            m.groupValues[3].startsWith("la") -> 1_00_000.0
            else -> 1_000.0
        }
        val paise = Money.fromRupees(m.groupValues[2].toDouble() * multiplier)?.takeIf { it < MAX_EXPENSE_PAISE }
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
        """^\s*(?:text|sms|message|msg)\s+(?:to\s+)?\+?\d[\d\s-]{5,}\d\s+(?:saying\s+|that\s+)?(.+)$""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private fun link(s: String, original: String): ParseResult? {
        Lexicon.URL_ONLY.matchEntire(s)?.let { m ->
            val url = m.groupValues[1]
            return ParseResult(ParsedCommand.OpenLink(if (url.startsWith("http")) url else "https://$url"), "url")
        }
        Lexicon.EMAIL.find(s)?.let { m ->
            if (Lexicon.MAIL_WORD.containsMatchIn(s) || s == m.value) return ParseResult(ParsedCommand.OpenLink("mailto:${m.value}"), "email")
        }
        Lexicon.SMS.matchEntire(s)?.let { m ->
            val number = digits(m.groupValues[1])
            // The message as typed: the normalised text is lower-case and has lost its ! and ?.
            val body = SMS_BODY.find(original)?.groupValues?.get(1)?.trim() ?: m.groupValues[2].trim()
            val uri = if (body.isEmpty()) "sms:$number" else "sms:$number?body=${encode(body)}"
            return ParseResult(ParsedCommand.OpenLink(uri), "sms")
        }
        (Lexicon.CALL.matchEntire(s) ?: Lexicon.CALL_HI.matchEntire(s))?.let { m ->
            val number = Lexicon.PHONE.find(m.groupValues[1])?.value
                ?: return ParseResult(ParsedCommand.Invalid("I can only call phone numbers. Try \"call 9876543210\"."), "call")
            return ParseResult(ParsedCommand.OpenLink("tel:${digits(number)}"), "call")
        }
        (Lexicon.NAVIGATE.matchEntire(s) ?: Lexicon.NAVIGATE_HI.matchEntire(s))?.let { m ->
            val place = (m.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: "").trim()
            if (place.isNotEmpty()) return ParseResult(ParsedCommand.OpenLink("google.navigation:q=${encode(place)}"), "navigate")
        }
        (Lexicon.SEARCH.matchEntire(s) ?: Lexicon.SEARCH_HI.matchEntire(s))?.let { m ->
            val query = m.groupValues[1].trim()
            if (query.isNotEmpty()) return ParseResult(ParsedCommand.OpenLink("https://www.google.com/search?q=${encode(query)}"), "search")
        }
        return null
    }

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
