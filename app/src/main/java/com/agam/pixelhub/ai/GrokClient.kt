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

/** xAI Grok via its OpenAI-compatible chat completions API, with JSON-schema structured output. */
class GrokClient(private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateJson(
        apiKey: String,
        model: String,
        systemPrompt: String,
        userText: String,
        schema: JsonObject,
    ): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model.trim())
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
                put("type", "json_schema")
                putJsonObject("json_schema") {
                    put("name", "pixelhub_command")
                    put("schema", schema)
                }
            }
        }
        val request = Request.Builder()
            .url("$BASE/chat/completions")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GrokException(errorMessage(raw) ?: "HTTP ${response.code}", response.code)
            runCatching {
                json.parseToJsonElement(raw).jsonObject["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: throw GrokException("Grok returned no text")
        }
    }

    /** Model ids and their aliases from GET /v1/models, sorted, for a picker in Settings. */
    suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE/models")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GrokException(errorMessage(raw) ?: "HTTP ${response.code}")
            val data = json.parseToJsonElement(raw).jsonObject["data"]?.jsonArray.orEmpty()
            data.flatMap { item ->
                val obj = item.jsonObject
                listOfNotNull(obj["id"]?.jsonPrimitive?.contentOrNull) +
                    (obj["aliases"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
            }.distinct().sorted()
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

    private companion object {
        const val BASE = "https://api.x.ai/v1"
    }
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
