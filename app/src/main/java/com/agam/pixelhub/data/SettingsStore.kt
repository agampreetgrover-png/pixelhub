package com.agam.pixelhub.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.YearMonth

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val keyCalendar = longPreferencesKey("calendar_id")
    private val keyStepGoal = longPreferencesKey("step_goal")
    private val keyReportMonth = stringPreferencesKey("report_last_month")

    /** Calendar that reminders are copied into, or null when calendar sync is off. */
    val calendarId: Flow<Long?> = context.settingsDataStore.data.map { it[keyCalendar] }

    /** Daily step goal shown on the Health page (default 8,000). */
    val stepGoal: Flow<Long> = context.settingsDataStore.data.map { it[keyStepGoal] ?: DEFAULT_STEP_GOAL }

    suspend fun setStepGoal(steps: Long) {
        context.settingsDataStore.edit { it[keyStepGoal] = steps.coerceIn(500, 100_000) }
    }

    /** The last month a report notification went out for, or null before the first one. */
    suspend fun lastReportMonth(): YearMonth? =
        context.settingsDataStore.data.first()[keyReportMonth]?.let { runCatching { YearMonth.parse(it) }.getOrNull() }

    suspend fun setLastReportMonth(month: YearMonth) {
        context.settingsDataStore.edit { it[keyReportMonth] = month.toString() }
    }

    /**
     * Calendar events of deleted reminders that couldn't be removed yet (no calendar access then).
     * In their own file, kept out of backup (data_extraction_rules.xml): event ids are per-device,
     * and replayed on a new phone they could delete other PixelHub events there. Callers hold the
     * reminders' calendar lock, so the read-modify-writes below don't interleave.
     */
    private val pendingDeletes get() = context.getSharedPreferences("calendar_pending", Context.MODE_PRIVATE)

    fun pendingCalendarDeletes(): Set<Long> =
        pendingDeletes.getStringSet(KEY_PENDING_IDS, null).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    fun addPendingCalendarDelete(eventId: Long) {
        // A new set each time: the one getStringSet returns must not be modified.
        val ids = pendingDeletes.getStringSet(KEY_PENDING_IDS, null).orEmpty()
        pendingDeletes.edit().putStringSet(KEY_PENDING_IDS, ids + eventId.toString()).commit()
    }

    fun removePendingCalendarDeletes(eventIds: Set<Long>) {
        val ids = pendingDeletes.getStringSet(KEY_PENDING_IDS, null).orEmpty()
        pendingDeletes.edit().putStringSet(KEY_PENDING_IDS, ids - eventIds.map { it.toString() }.toSet()).commit()
    }

    suspend fun setCalendarId(id: Long?) {
        context.settingsDataStore.edit {
            if (id == null) it.remove(keyCalendar) else it[keyCalendar] = id
        }
    }

    /** Deletes the Gemini / Groq keys and choices that versions before the rule-based assistant saved. */
    suspend fun forgetCloudAi() {
        context.settingsDataStore.edit { prefs ->
            listOf("gemini_api_key", "gemini_model", "ai_provider", "grok_api_key", "grok_model")
                .forEach { prefs.remove(stringPreferencesKey(it)) }
            prefs.remove(booleanPreferencesKey("ai_fallback"))
        }
    }

    companion object {
        const val DEFAULT_STEP_GOAL = 8_000L
        private const val KEY_PENDING_IDS = "ids"
    }
}
