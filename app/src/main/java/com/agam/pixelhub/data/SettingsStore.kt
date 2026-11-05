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

/** Which AI service the Assistant uses. */
enum class AiProvider(val label: String) {
    GEMINI("Google Gemini"),
    GROK("xAI Grok"),
}

class SettingsStore(private val context: Context) {
    private val keyApi = stringPreferencesKey("gemini_api_key")
    private val keyModel = stringPreferencesKey("gemini_model")
    private val keyCalendar = longPreferencesKey("calendar_id")
    private val keyStepGoal = longPreferencesKey("step_goal")
    private val keyProvider = stringPreferencesKey("ai_provider")
    private val keyGrokApi = stringPreferencesKey("grok_api_key")
    private val keyGrokModel = stringPreferencesKey("grok_model")
    private val keyFallback = booleanPreferencesKey("ai_fallback")

    val aiProvider: Flow<AiProvider> = context.settingsDataStore.data.map { prefs ->
        AiProvider.entries.firstOrNull { it.name == prefs[keyProvider] } ?: AiProvider.GEMINI
    }
    val grokApiKey: Flow<String> = context.settingsDataStore.data.map { it[keyGrokApi].orEmpty() }
    val grokModel: Flow<String> = context.settingsDataStore.data.map { it[keyGrokModel] ?: DEFAULT_GROK_MODEL }

    /** Retry on the other provider when the selected one is rate-limited (default on). */
    val aiFallback: Flow<Boolean> = context.settingsDataStore.data.map { it[keyFallback] ?: true }

    suspend fun setAiFallback(enabled: Boolean) {
        context.settingsDataStore.edit { it[keyFallback] = enabled }
    }

    suspend fun setAiProvider(provider: AiProvider) {
        context.settingsDataStore.edit { it[keyProvider] = provider.name }
    }

    suspend fun saveGrok(apiKey: String, model: String) {
        context.settingsDataStore.edit {
            it[keyGrokApi] = apiKey.trim()
            it[keyGrokModel] = model.trim().ifBlank { DEFAULT_GROK_MODEL }
        }
    }

    val apiKey: Flow<String> = context.settingsDataStore.data.map { it[keyApi].orEmpty() }
    val model: Flow<String> = context.settingsDataStore.data.map { it[keyModel] ?: DEFAULT_MODEL }

    /** Calendar that reminders are copied into, or null when calendar sync is off. */
    val calendarId: Flow<Long?> = context.settingsDataStore.data.map { it[keyCalendar] }

    suspend fun save(apiKey: String, model: String) {
        context.settingsDataStore.edit {
            it[keyApi] = apiKey.trim()
            it[keyModel] = model.trim().ifBlank { DEFAULT_MODEL }
        }
    }

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

    companion object {
        /** Google's alias for the newest Flash model. */
        const val DEFAULT_MODEL = "gemini-flash-latest"
        const val DEFAULT_STEP_GOAL = 8_000L
        /** xAI renames models often; Settings can load the live list via AssistantAi.listGrokModels. */
        const val DEFAULT_GROK_MODEL = "grok-4.7"
    }
}
