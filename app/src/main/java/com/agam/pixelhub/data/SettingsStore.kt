package com.agam.pixelhub.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val keyCalendar = longPreferencesKey("calendar_id")
    private val keyStepGoal = longPreferencesKey("step_goal")

    /** Calendar that reminders are copied into, or null when calendar sync is off. */
    val calendarId: Flow<Long?> = context.settingsDataStore.data.map { it[keyCalendar] }

    /** Daily step goal shown on the Health page (default 8,000). */
    val stepGoal: Flow<Long> = context.settingsDataStore.data.map { it[keyStepGoal] ?: DEFAULT_STEP_GOAL }

    suspend fun setStepGoal(steps: Long) {
        context.settingsDataStore.edit { it[keyStepGoal] = steps.coerceIn(500, 100_000) }
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
    }
}
