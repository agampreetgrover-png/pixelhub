package com.agam.pixelhub.ai

import com.agam.pixelhub.data.ExpenseCategory
import com.agam.pixelhub.data.ExpenseKind
import com.agam.pixelhub.expenses.Money
import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.scheduling.Recurrence
import com.agam.pixelhub.scheduling.RepeatType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** What the assistant wants to do. Always shown to the user for confirmation first. */
sealed interface ParsedCommand {
    data class CreateReminder(
        val title: String,
        val at: LocalDateTime,
        val recurrence: Recurrence,
        val notes: String = "",
    ) : ParsedCommand

    data class CreateAlarm(val hour: Int, val minute: Int, val daysMask: Int, val label: String) : ParsedCommand
    /** Money spent or received. [at] null = "now" (decided when the user confirms). */
    data class LogExpense(
        val amountPaise: Long,
        val category: ExpenseCategory,
        val note: String,
        val at: LocalDateTime? = null,
        val kind: ExpenseKind = ExpenseKind.EXPENSE,
    ) : ParsedCommand {
        val isIncome: Boolean get() = kind == ExpenseKind.INCOME
    }

    data class OpenApp(val query: String) : ParsedCommand
    data class OpenLink(val uri: String) : ParsedCommand
    data class Answer(val text: String) : ParsedCommand
    data class Invalid(val reason: String) : ParsedCommand
}

/** Builds the Gemini prompt/schema and turns the JSON reply into a [ParsedCommand]. */
object CommandParser {
    private val ACTIONS = listOf("create_reminder", "create_alarm", "log_expense", "open_app", "open_link", "answer")
    private val RECURRENCES = listOf("none", "daily", "weekly", "every_n_days", "every_n_weeks")
    private val DAY_CODES = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    private val PROPERTIES = listOf(
        "action", "title", "datetime", "recurrence", "days", "interval", "time", "label",
        "amount", "kind", "category", "note", "query", "uri", "text",
    )
    private val KINDS = listOf("expense", "income")

    /** ₹1 crore; anything bigger is almost certainly a misheard amount. */
    private const val MAX_EXPENSE_PAISE = 1_00_00_000_00L

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Serializable
    internal data class RawAction(
        val action: String? = null,
        val title: String? = null,
        val datetime: String? = null,
        val recurrence: String? = null,
        val days: List<String>? = null,
        val interval: Int? = null,
        val time: String? = null,
        val label: String? = null,
        val amount: Double? = null,
        val kind: String? = null,
        val category: String? = null,
        val note: String? = null,
        val query: String? = null,
        val uri: String? = null,
        val text: String? = null,
    )

    val responseSchema: JsonObject by lazy {
        buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("action") { enumString(ACTIONS) }
                putJsonObject("title") { put("type", "STRING") }
                putJsonObject("datetime") {
                    put("type", "STRING")
                    put("description", "Local date-time, YYYY-MM-DDTHH:MM")
                }
                putJsonObject("recurrence") { enumString(RECURRENCES) }
                putJsonObject("days") {
                    put("type", "ARRAY")
                    putJsonObject("items") { enumString(DAY_CODES) }
                }
                putJsonObject("interval") { put("type", "INTEGER") }
                putJsonObject("time") {
                    put("type", "STRING")
                    put("description", "24-hour HH:MM")
                }
                putJsonObject("label") { put("type", "STRING") }
                putJsonObject("amount") {
                    put("type", "NUMBER")
                    put("description", "Amount in Indian rupees")
                }
                putJsonObject("kind") { enumString(KINDS) }
                putJsonObject("category") { enumString(ExpenseCategory.entries.map { it.name }) }
                putJsonObject("note") { put("type", "STRING") }
                putJsonObject("query") { put("type", "STRING") }
                putJsonObject("uri") { put("type", "STRING") }
                putJsonObject("text") { put("type", "STRING") }
            }
            putJsonArray("required") { add("action") }
            putJsonArray("propertyOrdering") { PROPERTIES.forEach { add(it) } }
        }
    }

    /** The same schema in standard JSON Schema form, for Grok's response_format. */
    val jsonSchema: JsonObject by lazy { SchemaConvert.toJsonSchema(responseSchema) as JsonObject }

    private fun JsonObjectBuilder.enumString(values: List<String>) {
        put("type", "STRING")
        put("format", "enum")
        putJsonArray("enum") { values.forEach { add(it) } }
    }

    fun systemPrompt(now: ZonedDateTime, shortcutNames: List<String>, appNames: List<String>): String = """
        You are the command parser inside PixelHub, a personal assistant app on the user's Android phone.
        Turn the user's message into exactly one JSON action.

        Current local date-time: ${now.toLocalDateTime().truncatedTo(ChronoUnit.MINUTES)} (${now.dayOfWeek}), time zone ${now.zone.id}.

        Actions:
        - create_reminder: "title" and "datetime" (local, YYYY-MM-DDTHH:MM) are required. Optional "recurrence":
          none | daily | weekly | every_n_days | every_n_weeks. Use "days" (MON..SUN) with weekly and "interval" with every_n_*.
          If no time of day is given, use 09:00. Resolve relative dates ("tomorrow", "next friday", "in 2 hours")
          against the current date-time. A one-off reminder must be in the future.
          The title is short and imperative, without the time ("Call mom").
        - create_alarm: "time" (24-hour HH:MM) is required. Optional "days" (MON..SUN) for a repeating alarm
          ("weekdays" = MON..FRI) and "label".
        - log_expense: the user spent or received money. "amount" (positive number, Indian rupees; "1.2k" = 1200),
          "kind" and "category" are required.
          "kind" = "expense" when they spent or paid; "income" when they got paid, earned, received or were refunded.
          Expense categories: ${ExpenseCategory.forKind(ExpenseKind.EXPENSE).joinToString(", ") { it.name }}.
          Income categories: ${ExpenseCategory.forKind(ExpenseKind.INCOME).joinToString(", ") { it.name }}.
          "note" = what it was for or where it came from, short ("Lunch at Saravana", "November salary").
          Include "datetime" only if the user says when ("yesterday", "on Monday").
        - open_app: "query" = the app or shortcut to open. Prefer an exact name from the lists below.
        - open_link: "uri" = a URL or Android URI (https:, tel:, geo:, mailto:, sms:, google.navigation:q=...)
          for calling a number, navigating somewhere, searching the web, etc.
        - answer: "text" = a short reply, for questions or anything that is not one of the actions above.

        User's saved shortcuts: ${shortcutNames.joinToString(", ").ifBlank { "(none)" }}
        Installed apps: ${appNames.take(300).joinToString(", ").ifBlank { "(unknown)" }}
    """.trimIndent()

    fun parse(raw: String, zone: ZoneId = ZoneId.systemDefault()): ParsedCommand {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val a = try {
            json.decodeFromString(RawAction.serializer(), cleaned)
        } catch (e: Exception) {
            return ParsedCommand.Invalid("unreadable response")
        }
        return when (a.action?.trim()?.lowercase()) {
            "create_reminder" -> {
                val title = a.title?.trim()?.takeIf { it.isNotEmpty() }
                    ?: return ParsedCommand.Invalid("missing reminder title")
                val at = parseDateTime(a.datetime, zone) ?: return ParsedCommand.Invalid("missing or bad date/time")
                ParsedCommand.CreateReminder(title, at, recurrenceOf(a, at))
            }
            "create_alarm" -> {
                val (hour, minute) = parseTime(a.time) ?: return ParsedCommand.Invalid("missing or bad alarm time")
                ParsedCommand.CreateAlarm(hour, minute, daysMask(a.days), a.label.orEmpty().trim())
            }
            "log_expense" -> {
                val paise = a.amount?.let { Money.fromRupees(it) }?.takeIf { it < MAX_EXPENSE_PAISE }
                    ?: return ParsedCommand.Invalid("missing or bad amount")
                val named = ExpenseCategory.entries.firstOrNull { it.name.equals(a.category?.trim(), ignoreCase = true) }
                // No (or an unknown) kind: an income-only category like SALARY still implies income.
                val kind = when (a.kind?.trim()?.lowercase()) {
                    "income" -> ExpenseKind.INCOME
                    "expense" -> ExpenseKind.EXPENSE
                    else -> if (named?.isIncome == true) ExpenseKind.INCOME else ExpenseKind.EXPENSE
                }
                val category = named?.takeIf { it in ExpenseCategory.forKind(kind) } ?: ExpenseCategory.OTHER
                ParsedCommand.LogExpense(paise, category, a.note?.trim().orEmpty(), parseDateTime(a.datetime, zone), kind)
            }
            "open_app" -> a.query?.trim()?.takeIf { it.isNotEmpty() }?.let { ParsedCommand.OpenApp(it) }
                ?: ParsedCommand.Invalid("missing app name")
            "open_link" -> a.uri?.trim()?.takeIf { ':' in it }?.let { ParsedCommand.OpenLink(it) }
                ?: ParsedCommand.Invalid("missing link")
            "answer" -> ParsedCommand.Answer(a.text?.trim().orEmpty().ifEmpty { "…" })
            else -> ParsedCommand.Invalid("unknown action '${a.action}'")
        }
    }

    private fun recurrenceOf(a: RawAction, at: LocalDateTime): Recurrence = when (a.recurrence?.trim()?.lowercase()) {
        "daily" -> Recurrence(RepeatType.DAILY)
        "weekly" -> Recurrence(
            RepeatType.WEEKLY,
            daysMask = daysMask(a.days).takeIf { it != 0 } ?: Days.bit(at.dayOfWeek),
        )
        "every_n_days" -> Recurrence(RepeatType.EVERY_N_DAYS, interval = (a.interval ?: 1).coerceIn(1, 365))
        "every_n_weeks" -> Recurrence(RepeatType.EVERY_N_WEEKS, interval = (a.interval ?: 1).coerceIn(1, 52))
        else -> Recurrence.NONE
    }

    internal fun parseDateTime(value: String?, zone: ZoneId): LocalDateTime? {
        val s = value?.trim()?.replace(' ', 'T')?.takeIf { it.isNotEmpty() } ?: return null
        val parsed = runCatching { LocalDateTime.parse(s) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s).atZoneSameInstant(zone).toLocalDateTime() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(s).withZoneSameInstant(zone).toLocalDateTime() }.getOrNull()
        return parsed?.truncatedTo(ChronoUnit.MINUTES)
    }

    /** Accepts "06:30", "6:30", "18:30:00", "6:30 PM". */
    internal fun parseTime(value: String?): Pair<Int, Int>? {
        val s = value?.trim()?.lowercase() ?: return null
        val match = Regex("""^(\d{1,2}):(\d{2})""").find(s) ?: return null
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (s.endsWith("pm") && hour in 1..11) hour += 12
        if (s.endsWith("am") && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    internal fun daysMask(days: List<String>?): Int = days.orEmpty().fold(0) { mask, name ->
        val code = name.trim().uppercase()
        mask or when {
            code.startsWith("WEEKDAY") -> Days.WEEKDAYS
            code.startsWith("WEEKEND") -> Days.WEEKENDS
            code.startsWith("EVERY") || code == "DAILY" -> Days.ALL
            else -> DayOfWeek.entries.firstOrNull { it.name.startsWith(code.take(3)) && code.length >= 3 }
                ?.let(Days::bit) ?: 0
        }
    }
}
