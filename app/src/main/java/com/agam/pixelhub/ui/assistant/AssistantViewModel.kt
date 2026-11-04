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
import com.agam.pixelhub.scheduling.Recurrence
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.reminders.ReminderDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
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

    val hasApiKey: StateFlow<Boolean> = c.settings.apiKey.map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || _busy.value) return
        add(ChatItem.User(newId(), message))
        viewModelScope.launch {
            _busy.value = true
            try {
                handle(message)
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun handle(message: String) {
        val key = c.settings.apiKey.first()
        if (key.isBlank()) {
            add(ChatItem.Bot(newId(), "Add your Gemini API key in Settings first.", isError = true))
            return
        }
        val shortcuts = c.shortcuts.list()
        val apps = c.installedApps.list()
        val prompt = CommandParser.systemPrompt(ZonedDateTime.now(), shortcuts.map { it.label }, apps.map { it.label })
        val raw = try {
            c.gemini.generateJson(key, c.settings.model.first(), prompt, message, CommandParser.responseSchema)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            add(ChatItem.Bot(newId(), "Gemini request failed: ${e.message ?: e.javaClass.simpleName}", isError = true))
            return
        }
        when (val cmd = CommandParser.parse(raw)) {
            is ParsedCommand.Answer -> add(ChatItem.Bot(newId(), cmd.text))
            is ParsedCommand.Invalid ->
                add(ChatItem.Bot(newId(), "I couldn't turn that into an action (${cmd.reason}). Try rephrasing.", isError = true))
            is ParsedCommand.OpenApp -> add(ChatItem.Action(newId(), cmd, target = resolveApp(cmd.query, shortcuts, apps)))
            else -> add(ChatItem.Action(newId(), cmd))
        }
    }

    /** Runs the action exactly as Gemini proposed it. */
    fun confirm(id: Long) {
        val item = find(id) ?: return
        viewModelScope.launch {
            val outcome = when (val cmd = item.command) {
                is ParsedCommand.CreateReminder -> createReminder(cmd.title, cmd.notes, cmd.at, cmd.recurrence)
                is ParsedCommand.CreateAlarm -> createAlarm(
                    Alarm(hour = cmd.hour, minute = cmd.minute, daysMask = cmd.daysMask, label = cmd.label),
                )
                is ParsedCommand.OpenApp -> {
                    val t = item.target
                    when {
                        t == null -> Outcome(false, "No app matching \"${cmd.query}\". Add it as a shortcut in Apps.")
                        ShortcutLauncher.launch(context, t) -> Outcome(true, "Opened ${t.label}")
                        else -> Outcome(false, "Couldn't open ${t.label}")
                    }
                }
                is ParsedCommand.OpenLink ->
                    if (ShortcutLauncher.openUri(context, cmd.uri)) Outcome(true, "Opened")
                    else Outcome(false, "No app on this phone can open that link")
                else -> Outcome(false, "Nothing to do")
            }
            finish(id, outcome)
        }
    }

    /** Saves a reminder the user adjusted in the editor. */
    fun saveReminder(id: Long, draft: ReminderDraft) {
        viewModelScope.launch { finish(id, createReminder(draft.title, draft.notes, draft.at, draft.recurrence)) }
    }

    /** Saves an alarm the user adjusted in the editor. */
    fun saveAlarm(id: Long, alarm: Alarm) {
        viewModelScope.launch { finish(id, createAlarm(alarm)) }
    }

    fun cancel(id: Long) = updateAction(id) { it.copy(status = ChatItem.Status.CANCELLED, note = "") }

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
            ?: apps.firstOrNull { q.contains(it.label, ignoreCase = true) }?.asShortcut()
    }

    private fun updateAction(id: Long, f: (ChatItem.Action) -> ChatItem.Action) {
        _items.update { list -> list.map { if (it is ChatItem.Action && it.id == id) f(it) else it } }
    }

    private fun find(id: Long): ChatItem.Action? = _items.value.firstOrNull { it.id == id } as? ChatItem.Action

    private fun add(item: ChatItem) = _items.update { it + item }

    private fun newId() = ++nextId
}
