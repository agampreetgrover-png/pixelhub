package com.agam.pixelhub.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** [httpCode] is set when the API answered with an error status (429 = rate/quota limit). */
class GrokException(message: String, val httpCode: Int? = null) : Exception(message)

/** The two OpenAI-compatible services this client speaks to, told apart by the key's prefix. */
enum class ChatService(val base: String, val defaultModel: String) {
    /** groq.com: keys start with "gsk_". */
    GROQ("https://api.groq.com/openai/v1", "openai/gpt-oss-120b"),
    /** x.ai: keys start with "xai-". */
    XAI("https://api.x.ai/v1", "grok-4.7");

    companion object {
        fun forKey(apiKey: String): ChatService = if (apiKey.trim().startsWith("gsk_")) GROQ else XAI

        /** The model to send: blank, or an xAI "grok-…" name used with a Groq key, becomes the service default. */
        fun resolveModel(apiKey: String, model: String): String {
            val service = forKey(apiKey)
            val m = model.trim()
            return when {
                m.isBlank() -> service.defaultModel
                service == GROQ && m.startsWith("grok") -> service.defaultModel
                else -> m
            }
        }

        /** Groq also lists speech, TTS and guard models that can't answer chat requests. */
        private val NON_CHAT = listOf("whisper", "orpheus", "guard", "tts", "playai")
        fun isChatModel(id: String): Boolean = NON_CHAT.none { id.contains(it, ignoreCase = true) }
    }
}

/**
 * Groq (groq.com) or xAI Grok (x.ai) via their OpenAI-compatible chat completions APIs, with
 * JSON-schema structured output. Which service is used follows from the API key (see [ChatService]).
 */
class GrokClient(private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateJson(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userText: String,
        schema: JsonObject,
    ): String = withContext(Dispatchers.IO) {
        val service = ChatService.forKey(apiKey)
        val resolved = ChatService.resolveModel(apiKey, model)
        try {
            post(service, apiKey, body(resolved, systemPrompt, userText, schema, schemaMode = true))
        } catch (e: GrokException) {
            // Some Groq models only offer plain JSON mode; describe the schema in the prompt instead.
            if (e.httpCode != 400 || !mentionsResponseFormat(e.message)) throw e
            val prompt = "$systemPrompt\n\nReply with only a JSON object that matches this JSON Schema:\n$schema"
            post(service, apiKey, body(resolved, prompt, userText, schema, schemaMode = false))
        }
    }

    private fun body(model: String, systemPrompt: String, userText: String, schema: JsonObject, schemaMode: Boolean) =
        buildJsonObject {
            put("model", model)
            put("temperature", 0.2)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", userText)
                }
            }
            putJsonObject("response_format") {
                if (schemaMode) {
                    put("type", "json_schema")
                    putJsonObject("json_schema") {
                        put("name", "pixelhub_command")
                        put("schema", schema)
                    }
                } else {
                    put("type", "json_object")
                }
            }
        }

    private fun post(service: ChatService, apiKey: String, body: JsonObject): String {
        val request = Request.Builder()
            .url("${service.base}/chat/completions")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GrokException(errorMessage(raw) ?: "HTTP ${response.code}", response.code)
            return runCatching {
                json.parseToJsonElement(raw).jsonObject["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: throw GrokException("${service.label()} returned no text")
        }
    }

    private fun mentionsResponseFormat(message: String?): Boolean =
        listOf("response_format", "json_schema", "response format").any { message.orEmpty().contains(it, ignoreCase = true) }

    private fun ChatService.label() = if (this == ChatService.GROQ) "Groq" else "Grok"

    /** Chat model ids (and xAI aliases) from GET /models, sorted, for a picker in Settings. */
    suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${ChatService.forKey(apiKey).base}/models")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GrokException(errorMessage(raw) ?: "HTTP ${response.code}", response.code)
            val data = json.parseToJsonElement(raw).jsonObject["data"]?.jsonArray.orEmpty()
            data.flatMap { item ->
                val obj = item.jsonObject
                listOfNotNull(obj["id"]?.jsonPrimitive?.contentOrNull) +
                    (obj["aliases"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
            }.filter(ChatService::isChatModel).distinct().sorted()
        }
    }

    /** xAI errors come as {"error": "text"} or {"error": {"message": "text"}}. */
    private fun errorMessage(raw: String): String? = runCatching {
        when (val e = json.parseToJsonElement(raw).jsonObject["error"]) {
            is JsonPrimitive -> e.contentOrNull
            is JsonObject -> e["message"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
    }.getOrNull()
}

/** Converts the Gemini-style schema (upper-case types, format/propertyOrdering) to standard JSON Schema. */
object SchemaConvert {
    private val DROP = setOf("format", "propertyOrdering")

    fun toJsonSchema(gemini: JsonElement): JsonElement = when (gemini) {
        is JsonObject -> JsonObject(
            gemini.filterKeys { it !in DROP }.mapValues { (key, value) ->
                if (key == "type" && value is JsonPrimitive && value.isString) JsonPrimitive(value.content.lowercase())
                else toJsonSchema(value)
            },
        )
        is JsonArray -> JsonArray(gemini.map { toJsonSchema(it) })
        else -> gemini
    }
}
