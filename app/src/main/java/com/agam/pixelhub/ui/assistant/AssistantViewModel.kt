package com.agam.pixelhub.ui.assistant

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agam.pixelhub.ai.CommandParser
import com.agam.pixelhub.ai.GeminiException
import com.agam.pixelhub.ai.GrokException
import com.agam.pixelhub.ai.ParsedCommand
import com.agam.pixelhub.container
import com.agam.pixelhub.data.Alarm
import com.agam.pixelhub.data.AlarmRepository
import com.agam.pixelhub.data.AppShortcut
import com.agam.pixelhub.data.ShortcutKind
import com.agam.pixelhub.launcher.AppEntry
import com.agam.pixelhub.launcher.ShortcutLauncher
import com.agam.pixelhub.scheduling.Recurrence
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.reminders.ReminderDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.agam.pixelhub.data.Expense
import com.agam.pixelhub.expenses.Money
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

sealed interface ChatItem {
    val id: Long

    data class User(override val id: Long, val text: String) : ChatItem
    data class Bot(
        override val id: Long,
        val text: String,
        val isError: Boolean = false,
        /** Set when the backup AI answered, e.g. "Answered by Grok". */
        val via: String? = null,
        /** The request to send again when the user taps Retry (failed or stopped turns). */
        val retryText: String? = null,
        /** The failure looks like a bad or missing key, so the bubble also offers Settings. */
        val authError: Boolean = false,
    ) : ChatItem
    data class Action(
        override val id: Long,
        val command: ParsedCommand,
        /** For OpenApp: the shortcut or installed app the query resolved to. */
        val target: AppShortcut? = null,
        val status: Status = Status.PENDING,
        val note: String = "",
        /** Set when the backup AI answered, e.g. "Answered by Grok". */
        val via: String? = null,
    ) : ChatItem

    enum class Status { PENDING, DONE, CANCELLED }
}

class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val context: Context get() = getApplication()
    private var nextId = 0L

    private val _items = MutableStateFlow<List<ChatItem>>(emptyList())
    val items: StateFlow<List<ChatItem>> = _items.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** True if the selected AI or its backup has a key. */
    val hasApiKey: StateFlow<Boolean> = c.ai.hasKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // The request being answered, and the ones sent while it runs (e.g. from Home's assistant
    // bar, which clears and navigates straight away). They are answered in order.
    private var job: Job? = null
    private var current: String? = null
    private val pending = ArrayDeque<String>()

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty()) return
        add(ChatItem.User(newId(), message))
        if (_busy.value) pending.addLast(message) else process(message)
    }

    /** Sends a failed or stopped request again; its bubble's Retry goes away. */
    fun retry(id: Long) {
        val bot = _items.value.firstOrNull { it.id == id } as? ChatItem.Bot ?: return
        val text = bot.retryText ?: return
        _items.update { list -> list.map { if (it is ChatItem.Bot && it.id == id) it.copy(retryText = null, authError = false) else it } }
        send(text)
    }

    /** Cancels the request in flight. Its reply, if one still arrives, is dropped. */
    fun stop() {
        val message = current ?: return
        job?.cancel()
        add(ChatItem.Bot(newId(), "Stopped", retryText = message))
        next()
    }

    private fun process(message: String) {
        current = message
        _busy.value = true
        // Lazy, so [job] is set before the body can finish and chain the next request.
        val j = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                handle(message)
            } finally {
                // A stopped job has already been replaced; only the live one moves the queue on.
                if (job === coroutineContext[Job]) next()
            }
        }
        job = j
        j.start()
    }

    private fun next() {
        job = null
        current = null
        val queued = pending.removeFirstOrNull()
        if (queued != null) process(queued) else _busy.value = false
    }

    private suspend fun handle(message: String) {
        val shortcuts = c.shortcuts.list()
        // Fresh each time (list() runs on IO), so an app installed since the last request can be opened.
        val apps = c.installedApps.list(refresh = true)
        val prompt = CommandParser.systemPrompt(ZonedDateTime.now(), shortcuts.map { it.label }, apps.map { it.label })
        val reply = try {
            c.ai.generateCommand(prompt, message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            // "Add a … API key in Settings first." is already user-ready.
            add(ChatItem.Bot(newId(), e.message ?: "Add an API key in Settings first.", isError = true, retryText = message, authError = true))
            return
        } catch (e: Exception) {
            add(
                ChatItem.Bot(
                    newId(), "AI request failed: ${e.message ?: e.javaClass.simpleName}",
                    isError = true, retryText = message, authError = isAuthError(e),
                ),
            )
            return
        }
        // Stopped while the blocking request was finishing: drop the reply.
        currentCoroutineContext().ensureActive()
        val via = if (reply.usedFallback) "Answered by ${reply.provider.label}" else null
        when (val cmd = CommandParser.parse(reply.text)) {
            is ParsedCommand.Answer -> add(ChatItem.Bot(newId(), cmd.text, via = via))
            is ParsedCommand.Invalid ->
                add(ChatItem.Bot(newId(), "I couldn't turn that into an action (${cmd.reason}). Try rephrasing.", isError = true, via = via, retryText = message))
            is ParsedCommand.OpenApp -> add(ChatItem.Action(newId(), cmd, target = resolveApp(cmd.query, shortcuts, apps), via = via))
            else -> add(ChatItem.Action(newId(), cmd, via = via))
        }
    }

    /** Bad request, unauthorised or forbidden: almost always a wrong or revoked key. */
    private fun isAuthError(e: Exception): Boolean {
        val code = when (e) {
            is GeminiException -> e.httpCode
            is GrokException -> e.httpCode
            else -> null
        }
        return code == 400 || code == 401 || code == 403 || e.message?.contains("API key not valid", ignoreCase = true) == true
    }

    /** Runs the action exactly as the AI proposed it. */
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
                finish(id, work())
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
        if (id in inFlight) return
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

    private fun finish(id: Long, outcome: Outcome) = updateAction(id) {
        it.copy(status = if (outcome.ok) ChatItem.Status.DONE else ChatItem.Status.PENDING, note = outcome.message)
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
