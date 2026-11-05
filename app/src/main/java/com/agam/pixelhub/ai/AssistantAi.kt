package com.agam.pixelhub.ai

import com.agam.pixelhub.data.AiProvider
import com.agam.pixelhub.data.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** A model reply plus who produced it. */
data class AiReply(
    /** JSON for CommandParser.parse. */
    val text: String,
    val provider: AiProvider,
    /** Set when [provider] stood in because the selected one hit a limit; holds that one's error. */
    val fallbackReason: String? = null,
) {
    val usedFallback: Boolean get() = fallbackReason != null
}

/**
 * The Assistant's single entry point. Sends to the provider chosen in Settings; if that one
 * is rate-limited / out of quota (429) or overloaded (503) and the other provider has a key,
 * the same request is retried there.
 */
class AssistantAi(
    private val settings: SettingsStore,
    private val gemini: () -> GeminiClient,
    private val grok: () -> GrokClient,
) {
    val provider: Flow<AiProvider> = settings.aiProvider

    /** True when at least one provider has a key (the selected one, or the fallback). */
    val hasKey: Flow<Boolean> = combine(settings.apiKey, settings.grokApiKey, settings.aiFallback, settings.aiProvider) {
            geminiKey, grokKey, fallback, selected ->
        val selectedHasKey = keyFor(selected, geminiKey, grokKey).isNotBlank()
        selectedHasKey || (fallback && keyFor(selected.other(), geminiKey, grokKey).isNotBlank())
    }

    suspend fun generateCommand(systemPrompt: String, userText: String): AiReply {
        val selected = settings.aiProvider.first()
        val geminiKey = settings.apiKey.first()
        val grokKey = settings.grokApiKey.first()
        val backup = selected.other()
        val backupReady = settings.aiFallback.first() && keyFor(backup, geminiKey, grokKey).isNotBlank()

        // No key for the selected provider at all: go straight to the backup if there is one.
        if (keyFor(selected, geminiKey, grokKey).isBlank()) {
            if (backupReady) return AiReply(call(backup, systemPrompt, userText), backup, "${selected.label} has no API key")
            throw IllegalStateException("Add a ${selected.label} API key in Settings first.")
        }
        return try {
            AiReply(call(selected, systemPrompt, userText), selected)
        } catch (e: Exception) {
            if (!backupReady || !isLimitError(e)) throw e
            AiReply(call(backup, systemPrompt, userText), backup, "${selected.label}: ${e.message}")
        }
    }

    /** Live Grok model ids/aliases for the given key (for the Settings model picker). */
    suspend fun listGrokModels(apiKey: String): List<String> = grok().listModels(apiKey)

    private suspend fun call(p: AiProvider, systemPrompt: String, userText: String): String = when (p) {
        AiProvider.GEMINI -> gemini().generateJson(
            settings.apiKey.first(), settings.model.first(), systemPrompt, userText, CommandParser.responseSchema,
        )
        AiProvider.GROK -> grok().generateJson(
            settings.grokApiKey.first(), settings.grokModel.first(), systemPrompt, userText, CommandParser.jsonSchema,
        )
    }

    companion object {
        /** HTTP statuses that mean "try the other provider": rate/quota limit and temporary overload. */
        val FALLBACK_CODES = setOf(429, 503)

        fun isLimitError(e: Throwable): Boolean {
            val code = when (e) {
                is GeminiException -> e.httpCode
                is GrokException -> e.httpCode
                else -> null
            }
            if (code in FALLBACK_CODES) return true
            val msg = e.message.orEmpty()
            return listOf("RESOURCE_EXHAUSTED", "quota", "rate limit").any { msg.contains(it, ignoreCase = true) }
        }

        private fun AiProvider.other() = if (this == AiProvider.GEMINI) AiProvider.GROK else AiProvider.GEMINI

        private fun keyFor(p: AiProvider, geminiKey: String, grokKey: String) =
            if (p == AiProvider.GEMINI) geminiKey else grokKey
    }
}
