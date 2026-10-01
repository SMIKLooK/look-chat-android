package com.look.chat.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class GeminiProvider(
    private val apiKey: String,
    baseUrl: String? = null,
    models: List<String>? = null,
    modelPrefixes: List<String>? = null,
    private val maxTokens: Int = 0,
    httpClient: OkHttpClient = Http.client,
) : AiProvider {

    private val baseUrl = baseUrl?.trimEnd('/') ?: DEFAULT_BASE_URL

    override val models: List<String> = models ?: DEFAULT_MODELS

    private val prefixes = modelPrefixes ?: DEFAULT_MODEL_PREFIXES

    override val name: String = "gemini"

    private val httpClient = httpClient

    override fun supports(model: String): Boolean {
        if (models.any { it.equals(model, ignoreCase = true) }) return true
        val m = model.lowercase()
        return prefixes.any { m.startsWith(it.lowercase()) }
    }

    override suspend fun complete(request: AiRequest): AiResponse {
        val payload = buildJsonObject {
            put(
                "contents",
                JsonArray(request.messages.map { message ->
                    buildJsonObject {
                        put(
                            "role",
                            if (message.role.equals("assistant", ignoreCase = true)) "model"
                            else message.role,
                        )
                        put(
                            "parts",
                            JsonArray(listOf(buildJsonObject { put("text", message.content) })),
                        )
                    }
                }),
            )
            if (maxTokens > 0) {
                put("generationConfig", buildJsonObject { put("maxOutputTokens", maxTokens) })
            }
        }

        val endpoint = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("models")
            .addPathSegment(request.model + ":generateContent")
            .build()
        val httpRequest = Request.Builder()
            .url(endpoint)
            .post(Http.body(payload.toString()))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .build()

        val result = Http.doCall(httpClient, name, httpRequest)

        val parsed = try {
            Http.json.parseToJsonElement(result.body)
        } catch (e: Exception) {
            throw AiError(
                AiError.PROVIDER_ERROR,
                "gemini вернул некорректный ответ (HTTP ${result.code}): ${Http.truncate(result.body)}",
            )
        }

        if (result.code < 200 || result.code >= 300) {
            val errText = extractErrorText((parsed as? JsonObject)?.get("error"))
            val msg = buildString {
                append("HTTP ${result.code}")
                if (!errText.isNullOrEmpty()) append(": ").append(errText)
            }
            throw AiError(AiError.PROVIDER_ERROR, "ошибка gemini: $msg")
        }

        val blockReason = (parsed as? JsonObject)?.get("promptFeedback")
            ?.let { pf -> (pf as? JsonObject)?.get("blockReason")?.let { (it as? JsonPrimitive)?.contentOrNull } }
        if (!blockReason.isNullOrEmpty()) {
            throw AiError(
                AiError.PROVIDER_ERROR,
                "gemini заблокировал запрос (blockReason: $blockReason)",
            )
        }

        val candidates = (parsed as? JsonObject)?.get("candidates") as? JsonArray
        candidates?.forEach { candidate ->
            val parts = ((candidate as? JsonObject)?.get("content") as? JsonObject)
                ?.get("parts") as? JsonArray ?: return@forEach
            val text = parts.joinToString("") { part ->
                ((part as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull ?: ""
            }
            if (text.isNotBlank()) {
                return AiResponse(
                    model = request.model,
                    provider = name,
                    content = text.trim(),
                )
            }
        }
        throw AiError(AiError.PROVIDER_ERROR, "gemini не вернул текст в ответе")
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

        val DEFAULT_MODELS = listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-2.5-pro")

        val DEFAULT_MODEL_PREFIXES = listOf("gemini")
    }
}
