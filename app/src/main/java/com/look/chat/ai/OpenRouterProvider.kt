package com.look.chat.ai

import okhttp3.OkHttpClient
import okhttp3.Request

class OpenRouterProvider(
    private val apiKey: String,
    baseUrl: String? = null,
    models: List<String>? = null,
    private val maxTokens: Int = 0,
    httpClient: OkHttpClient = Http.client,
) : AiProvider {

    private val baseUrl = baseUrl?.trimEnd('/') ?: DEFAULT_BASE_URL

    override val models: List<String> = models ?: DEFAULT_MODELS

    override val name: String = "openrouter"

    private val httpClient = httpClient

    override fun supports(model: String): Boolean {
        val m = model.lowercase()
        if (m.contains('/')) return true
        return models.any { it.equals(model, ignoreCase = true) || slugSuffix(it) == m }
    }

    override suspend fun complete(request: AiRequest): AiResponse {
        val slug = resolveSlug(request.model)
        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(Http.body(chatRequestJson(slug, request.messages, maxTokens)))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .header("X-Title", APP_TITLE)
            .build()

        val result = Http.doCall(httpClient, name, httpRequest)
        val content = chatReply(name, result) { code, errText, _ ->
            if (code == 403 && errText?.contains(WAF_BLOCKED) == true) {
                throw AiError(
                    AiError.PROVIDER_ERROR,
                    "openrouter блокирует запросы с этой сети (HTTP 403, WAF): регион/IP не поддерживается. " +
                        "Это не ошибка кода и не проблема ключа — запросы просто не доходят до API",
                )
            }
        }
        return AiResponse(model = slug, provider = name, content = content)
    }

    private fun resolveSlug(model: String): String {
        models.forEach { if (it.equals(model, ignoreCase = true)) return it }
        val m = model.lowercase()
        models.forEach { if (slugSuffix(it) == m) return it }
        return if (m.contains('/')) m else model
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://openrouter.ai/api/v1"
        const val APP_TITLE = "look-chat"
        const val WAF_BLOCKED = "Access denied by security policy"

        val DEFAULT_MODELS = listOf(
            "openrouter/auto",
            "openrouter/free",
            "google/gemini-3.8-flash",
            "google/gemini-3.6-flash",
            "openai/gpt-5.6-terra",
            "anthropic/claude-sonnet-5",
            "deepseek/deepseek-v4-flash",
            "z-ai/glm-5.3-flash",
            "meta-llama/llama-4-maverick",
        )

        private fun slugSuffix(slug: String): String {
            val slash = slug.indexOf('/')
            return if (slash >= 0) slug.substring(slash + 1).lowercase() else ""
        }
    }
}
