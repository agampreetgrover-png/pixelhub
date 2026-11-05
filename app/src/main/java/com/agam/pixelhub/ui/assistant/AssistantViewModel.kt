package com.agam.pixelhub.ui.assistant

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agam.pixelhub.ai.CommandParser
import com.agam.pixelhub.ai.ParsedCommand
import com.agam.pixelhub.container
import com.agam.pixelhub.data.Alarm
import com.agam.pixelhub.data.AlarmRepository
import com.agam.pixelhub.data.AppShortcut
import com.agam.pixelhub.data.ShortcutKind
import com.agam.pixelhub.launcher.AppEntry
import com.agam.pixelhub.launcher.ShortcutLauncher
import com.agam.pixelhub.ledger.LedgerMath
import com.agam.pixelhub.scheduling.Recurrence
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.reminders.ReminderDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.withLock
import com.agam.pixelhub.data.Expense
import com.agam.pixelhub.expenses.Money
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

sealed interface ChatItem {
    val id: Long

    data class User(override val id: Long, val text: String) : ChatItem
    data class Bot(override val id: Long, val text: String, val isError: Boolean = false) : ChatItem
    data class Action(
        override val id: Long,
        val command: ParsedCommand,
        /** For OpenApp: the shortcut or installed app the query resolved to. */
        val target: AppShortcut? = null,
        val status: Status = Status.PENDING,
        val note: String = "",
        /** The Settings log entry for this request, updated when the draft is saved or cancelled. */
        val logId: Long? = null,
    ) : ChatItem

    enum class Status { PENDING, DONE, CANCELLED }
}

class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val context: Context get() = getApplication()
    private var nextId = 0L

    private val _items = MutableStateFlow<List<ChatItem>>(emptyList())
    val items: StateFlow<List<ChatItem>> = _items.asStateFlow()

    // Keeps replies in the order the requests were sent (Home's assistant bar can send one
    // while the previous one is still looking up apps).
    private val order = Mutex()

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty()) return
        add(ChatItem.User(newId(), message))
        viewModelScope.launch {
            order.withLock {
                try {
                    handle(message)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A rule bug must never take the whole app down: answer like any unreadable request.
                    add(ChatItem.Bot(newId(), CommandParser.NOT_UNDERSTOOD, isError = true))
                }
            }
        }
    }

    /**
     * Reads [message] with the on-phone rules (see CommandParser) and replies at once: a draft
     * to confirm, or, when nothing matched, the reason. Every request goes to the Settings log.
     */
    private suspend fun handle(message: String) {
        val shortcuts = c.shortcuts.list()
        val apps = c.installedApps.list()
        val people = c.ledger.names()
        val started = System.nanoTime()
        val result = CommandParser.parse(message, ZonedDateTime.now(), shortcuts.map { it.label } + apps.map { it.label }, people)
        val micros = (System.nanoTime() - started) / 1_000
        val logId = c.assistantLog.add(message, result, micros)
        when (val cmd = result.command) {
            is ParsedCommand.Answer -> add(ChatItem.Bot(newId(), cmd.text))
            is ParsedCommand.Invalid -> add(ChatItem.Bot(newId(), cmd.reason, isError = true))
            is ParsedCommand.LedgerBalance -> {
                val person = c.ledger.find(cmd.name)
                if (person == null) {
                    add(ChatItem.Bot(newId(), "I don't have ${cmd.name} in People yet. Try \"paid ${cmd.name} 30\" to start.", isError = true))
                } else {
                    add(ChatItem.Bot(newId(), LedgerMath.balanceText(person.name, c.ledger.balance(person.id))))
                }
            }
            is ParsedCommand.OpenApp -> {
                // Fresh, so an app installed since the list was cached can be opened.
                val fresh = c.installedApps.list(refresh = true)
                add(ChatItem.Action(newId(), cmd, target = resolveApp(cmd.query, shortcuts, fresh), logId = logId))
            }
            else -> add(ChatItem.Action(newId(), cmd, logId = logId))
        }
    }

    /** Runs the action exactly as it was understood. */
    fun confirm(id: Long) {
        val item = find(id) ?: return
        runOnce(id) {
            when (val cmd = item.command) {
                is ParsedCommand.CreateReminder -> createReminder(cmd.title, cmd.notes, cmd.at, cmd.recurrence)
                is ParsedCommand.CreateAlarm -> createAlarm(
                    Alarm(hour = cmd.hour, minute = cmd.minute, daysMask = cmd.daysMask, label = cmd.label),
                )
                is ParsedCommand.OpenApp -> {
                    val t = item.target
                    when {
                        t == null -> Outcome(false, "No installed app matching \"${cmd.query}\".")
                        ShortcutLauncher.launch(context, t) -> Outcome(true, "Opened ${t.label}")
                        else -> Outcome(false, "Couldn't open ${t.label}")
                    }
                }
                is ParsedCommand.OpenLink ->
                    if (ShortcutLauncher.openUri(context, cmd.uri)) Outcome(true, "Opened")
                    else Outcome(false, "No app on this phone can open that link")
                is ParsedCommand.LogExpense -> {
                    val at = cmd.at?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                        ?: System.currentTimeMillis()
                    c.expenses.save(
                        Expense(amountPaise = cmd.amountPaise, category = cmd.category, note = cmd.note, spentAt = at, kind = cmd.kind),
                    )
                    if (cmd.isIncome) Outcome(true, "Added ${Money.format(cmd.amountPaise)} income from ${cmd.category.label}")
                    else Outcome(true, "Logged ${Money.format(cmd.amountPaise)} for ${cmd.category.label}")
                }
                is ParsedCommand.LedgerEntry -> {
                    val at = cmd.at?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                        ?: System.currentTimeMillis()
                    val balance = c.ledger.record(cmd.name, cmd.signedPaise, cmd.note, at)
                    val name = c.ledger.find(cmd.name)?.name ?: cmd.name
                    Outcome(true, LedgerMath.balanceText(name, balance).let { if (balance == 0L) it else "Now: $it" })
                }
                else -> Outcome(false, "Nothing to do")
            }
        }
    }

    // Actions being saved right now. A second tap on Confirm (or Cancel) during the save is ignored,
    // so a double tap can't create two reminders.
    private val inFlight = mutableSetOf<Long>()

    private fun runOnce(id: Long, work: suspend () -> Outcome) {
        val item = find(id) ?: return
        if (item.status != ChatItem.Status.PENDING || !inFlight.add(id)) return
        viewModelScope.launch {
            try {
                // A failed save (database, scheduling) shows on the card instead of crashing the app;
                // the card stays pending so Confirm can be tried again.
                val outcome = try {
                    work()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Outcome(false, "Couldn't save that. Try again.")
                }
                finish(id, outcome)
            } finally {
                inFlight.remove(id)
            }
        }
    }

    /** Saves a reminder the user adjusted in the editor. */
    fun saveReminder(id: Long, draft: ReminderDraft) {
        runOnce(id) { createReminder(draft.title, draft.notes, draft.at, draft.recurrence) }
    }

    /** Saves an alarm the user adjusted in the editor. */
    fun saveAlarm(id: Long, alarm: Alarm) {
        runOnce(id) { createAlarm(alarm) }
    }

    fun cancel(id: Long) {
        // The buttons stay tappable while they animate out: a late Cancel must not undo a save.
        if (id in inFlight || find(id)?.status != ChatItem.Status.PENDING) return
        find(id)?.logId?.let { c.assistantLog.setOutcome(it, null, "Cancelled") }
        updateAction(id) { it.copy(status = ChatItem.Status.CANCELLED, note = "") }
    }


    private data class Outcome(val ok: Boolean, val message: String)

    private suspend fun createReminder(title: String, notes: String, at: LocalDateTime, rec: Recurrence): Outcome {
        if (!rec.isRepeating && !at.isAfter(LocalDateTime.now())) {
            return Outcome(false, "That time has already passed. Tap Edit to change it.")
        }
        val saved = c.reminders.save(0, title, notes, at, rec)
        return Outcome(true, "Reminder set for ${Fmt.whenText(context, saved.triggerAt)}")
    }

    private suspend fun createAlarm(alarm: Alarm): Outcome {
        val saved = c.alarms.save(alarm.copy(id = 0, enabled = true))
        val at = AlarmRepository.nextTrigger(saved).toInstant().toEpochMilli()
        return Outcome(true, "Alarm set. It rings in ${Fmt.untilText(at)}.")
    }

    private fun finish(id: Long, outcome: Outcome) {
        find(id)?.logId?.let { c.assistantLog.setOutcome(it, outcome.ok, if (outcome.ok) "Saved · ${outcome.message}" else outcome.message) }
        updateAction(id) {
            it.copy(status = if (outcome.ok) ChatItem.Status.DONE else ChatItem.Status.PENDING, note = outcome.message)
        }
    }

    private fun resolveApp(query: String, shortcuts: List<AppShortcut>, apps: List<AppEntry>): AppShortcut? {
        val q = query.trim()
        fun AppEntry.asShortcut() = AppShortcut(label = label, kind = ShortcutKind.APP, packageName = packageName)
        return shortcuts.firstOrNull { it.label.equals(q, ignoreCase = true) }
            ?: apps.firstOrNull { it.label.equals(q, ignoreCase = true) }?.asShortcut()
            ?: shortcuts.firstOrNull { it.label.contains(q, ignoreCase = true) }
            ?: apps.firstOrNull { it.label.contains(q, ignoreCase = true) }?.asShortcut()
            // The query names an app inside a longer phrase ("Netflix app"): whole words only, and
            // not for tiny labels, so "Open Netflix" can't land on an app called "X".
            ?: apps.firstOrNull { it.label.length >= 3 && wholeWord(it.label).containsMatchIn(q) }?.asShortcut()
    }

    private fun wholeWord(label: String) =
        Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(label) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)

    private fun updateAction(id: Long, f: (ChatItem.Action) -> ChatItem.Action) {
        _items.update { list -> list.map { if (it is ChatItem.Action && it.id == id) f(it) else it } }
    }

    private fun find(id: Long): ChatItem.Action? = _items.value.firstOrNull { it.id == id } as? ChatItem.Action

    private fun add(item: ChatItem) = _items.update { it + item }

    private fun newId() = ++nextId
}
