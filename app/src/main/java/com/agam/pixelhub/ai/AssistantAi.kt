package com.agam.pixelhub.ai

import com.agam.pixelhub.data.AiProvider
import com.agam.pixelhub.data.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.io.InterruptedIOException

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
        AiProvider.GEMINI -> callGemini(systemPrompt, userText)
        AiProvider.GROK -> grok().generateJson(
            settings.grokApiKey.first(), settings.grokModel.first(), systemPrompt, userText, CommandParser.jsonSchema,
        )
    }

    /**
     * Gemini's Flash models often answer 503 "high demand" for minutes at a time, sometimes only
     * after a long wait. Give the chosen model [PRIMARY_TIMEOUT_S], then try the lighter Flash-Lite
     * model, which runs on separate capacity, before [generateCommand] switches to Groq.
     * Throws the first limit error if every attempt fails.
     */
    private suspend fun callGemini(systemPrompt: String, userText: String): String {
        val key = settings.apiKey.first()
        var firstError: Exception? = null
        for ((i, model) in geminiAttempts(settings.model.first()).withIndex()) {
            val timeout = if (i == 0) PRIMARY_TIMEOUT_S else BACKUP_TIMEOUT_S
            try {
                return gemini().generateJson(key, model, systemPrompt, userText, CommandParser.responseSchema, timeout)
            } catch (e: Exception) {
                // A non-limit error on the user's own model (bad key, bad model name) is final.
                if (i == 0 && !isLimitError(e)) throw e
                if (firstError == null) firstError = e
            }
        }
        throw firstError ?: IllegalStateException("Gemini did not answer")
    }

    companion object {
        /** HTTP statuses that mean "try the other provider": rate/quota limit and temporary overload. */
        val FALLBACK_CODES = setOf(429, 503)

        /** Alias for the newest Flash-Lite model, used when the chosen Gemini model is overloaded. */
        const val GEMINI_BACKUP_MODEL = "gemini-flash-lite-latest"
        private const val PRIMARY_TIMEOUT_S = 20L
        private const val BACKUP_TIMEOUT_S = 30L

        /** Models to try in order: the chosen one, then Flash-Lite. */
        fun geminiAttempts(model: String): List<String> {
            val chosen = model.trim().ifBlank { SettingsStore.DEFAULT_MODEL }
            return listOf(chosen, GEMINI_BACKUP_MODEL).distinct()
        }

        fun isLimitError(e: Throwable): Boolean {
            val code = when (e) {
                is GeminiException -> e.httpCode
                is GrokException -> e.httpCode
                else -> null
            }
            if (code in FALLBACK_CODES) return true
            // A call that timed out is treated like an overloaded one (no network at all is not).
            if (e is InterruptedIOException) return true
            val msg = e.message.orEmpty()
            return listOf("RESOURCE_EXHAUSTED", "quota", "rate limit").any { msg.contains(it, ignoreCase = true) }
        }

        private fun AiProvider.other() = if (this == AiProvider.GEMINI) AiProvider.GROK else AiProvider.GEMINI

        private fun keyFor(p: AiProvider, geminiKey: String, grokKey: String) =
            if (p == AiProvider.GEMINI) geminiKey else grokKey
    }
}
