package com.look.chat.ai

import okhttp3.OkHttpClient
import okhttp3.Request

class GptunnelProvider(
    private val apiKey: String,
    baseUrl: String? = null,
    models: List<String>? = null,
    private val maxTokens: Int = 0,
    httpClient: OkHttpClient = Http.client,
) : AiProvider {

    private val baseUrl = baseUrl?.trimEnd('/') ?: DEFAULT_BASE_URL

    override val models: List<String> = models ?: DEFAULT_MODELS

    override val name: String = "gptunnel"

    private val httpClient = httpClient

    override fun supports(model: String): Boolean =
        models.any { it.equals(model, ignoreCase = true) }

    override suspend fun complete(request: AiRequest): AiResponse {
        val model = resolveModel(request.model)
        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(Http.body(chatRequestJson(model, request.messages, maxTokens)))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .build()

        val result = Http.doCall(httpClient, name, httpRequest)
        val content = chatReply(name, result)
        return AiResponse(model = model, provider = name, content = content)
    }

    private fun resolveModel(model: String): String =
        models.firstOrNull { it.equals(model, ignoreCase = true) } ?: model

    companion object {
        const val DEFAULT_BASE_URL = "https://gptunnel.ru/v1"

        val DEFAULT_MODELS = listOf(
            "gpt-4o",
            "gpt-4o-mini",
            "o3-mini",
            "claude-4.5-haiku",
            "claude-5-sonnet",
            "deepseek-v4-flash",
        )
    }
}
