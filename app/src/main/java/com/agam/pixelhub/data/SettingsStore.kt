package com.agam.pixelhub.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val keyApi = stringPreferencesKey("gemini_api_key")
    private val keyModel = stringPreferencesKey("gemini_model")

    val apiKey: Flow<String> = context.settingsDataStore.data.map { it[keyApi].orEmpty() }
    val model: Flow<String> = context.settingsDataStore.data.map { it[keyModel] ?: DEFAULT_MODEL }

    suspend fun save(apiKey: String, model: String) {
        context.settingsDataStore.edit {
            it[keyApi] = apiKey.trim()
            it[keyModel] = model.trim().ifBlank { DEFAULT_MODEL }
        }
    }

    companion object {
        /** Google's alias for the newest Flash model. */
        const val DEFAULT_MODEL = "gemini-flash-latest"
    }
}
