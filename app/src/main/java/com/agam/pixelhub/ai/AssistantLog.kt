package com.agam.pixelhub.ai

import com.agam.pixelhub.expenses.Money
import com.agam.pixelhub.scheduling.Days
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One assistant request: what was typed, what the rules made of it, and what happened next. */
@Serializable
data class AssistantLogEntry(
    val id: Long,
    /** When it was asked, epoch millis. */
    val at: Long,
    val input: String,
    /** True when a rule understood it. */
    val ok: Boolean,
    /** The rule that answered: reminder, alarm, expense, income, call, open_app, none, ... */
    val rule: String,
    /** The draft ("Reminder · Call mom · Fri 6 Nov, 6:00 pm") or why it failed. */
    val summary: String,
    /** How long the rules took, in microseconds. */
    val micros: Long,
    /** "Saved", "Cancelled" or the save error; null while the draft is waiting. */
    val outcome: String? = null,
    /** True saved, false failed, null cancelled or waiting. */
    val outcomeOk: Boolean? = null,
)

/**
 * The newest [MAX] assistant requests, kept in a small JSON file on the phone and shown in
 * Settings. Reads and writes happen off the main thread; [entries] updates at once.
 */
class AssistantLog(private val file: File, private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(AssistantLogEntry.serializer())
    private val _entries = MutableStateFlow<List<AssistantLogEntry>>(emptyList())
    val entries: StateFlow<List<AssistantLogEntry>> = _entries.asStateFlow()
    private val writeLock = Mutex()

    private val loaded: Job = scope.launch(Dispatchers.IO) {
        val saved = runCatching { json.decodeFromString(serializer, file.readText()) }.getOrNull() ?: return@launch
        // Requests logged while the file was loading stay on top.
        _entries.update { current -> (current + saved).distinctBy { it.id }.take(MAX) }
    }

    /** Logs a request and returns its id, for [setOutcome]. */
    fun add(input: String, result: ParseResult, micros: Long): Long {
        val now = System.currentTimeMillis()
        val id = maxOf(now, (_entries.value.firstOrNull()?.id ?: 0L) + 1)
        val entry = AssistantLogEntry(
            id = id, at = now, input = input, ok = result.ok, rule = result.rule,
            summary = summarize(result.command), micros = micros,
        )
        _entries.update { (listOf(entry) + it).take(MAX) }
        persist()
        return id
    }

    /** Records what happened to the draft: saved ([ok] true), failed (false) or cancelled (null). */
    fun setOutcome(id: Long, ok: Boolean?, text: String) {
        _entries.update { list -> list.map { if (it.id == id) it.copy(outcome = text, outcomeOk = ok) else it } }
        persist()
    }

    fun clear() {
        _entries.value = emptyList()
        persist()
    }

    private fun persist() {
        scope.launch(Dispatchers.IO) {
            loaded.join()
            writeLock.withLock {
                runCatching {
                    val tmp = File(file.parentFile, "${file.name}.tmp")
                    tmp.writeText(json.encodeToString(serializer, _entries.value))
                    tmp.renameTo(file)
                }
            }
        }
    }

    companion object {
        const val MAX = 200
        private val WHEN = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH)

        fun summarize(cmd: ParsedCommand): String = when (cmd) {
            is ParsedCommand.CreateReminder -> listOfNotNull(
                "Reminder", cmd.title, cmd.at.format(WHEN), cmd.recurrence.describe().takeIf { it.isNotEmpty() },
            ).joinToString(" · ")
            is ParsedCommand.CreateAlarm -> listOfNotNull(
                "Alarm", "%02d:%02d".format(cmd.hour, cmd.minute), Days.label(cmd.daysMask), cmd.label.takeIf { it.isNotEmpty() },
            ).joinToString(" · ")
            is ParsedCommand.LogExpense -> listOfNotNull(
                if (cmd.isIncome) "Income" else "Expense", Money.format(cmd.amountPaise), cmd.category.label,
                cmd.note.takeIf { it.isNotEmpty() }, cmd.at?.format(WHEN),
            ).joinToString(" · ")
            is ParsedCommand.OpenApp -> "Open app · ${cmd.query}"
            is ParsedCommand.OpenLink -> "Open · ${cmd.uri}"
            is ParsedCommand.Answer -> "Help"
            is ParsedCommand.Invalid -> cmd.reason
        }
    }
}
