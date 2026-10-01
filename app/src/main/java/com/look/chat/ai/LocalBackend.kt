package com.look.chat.ai

data class LocalResult(
    val model: String,
    val provider: String,
    val answer: String,
    val elapsedMs: Long,
)

class LocalBackend(val registry: ProviderRegistry) {

    suspend fun process(text: String): LocalResult {
        val parsed = RequestParser.parse(text)
        if (registry.providers.isEmpty()) {
            throw AiError(
                AiError.PROVIDER_ERROR,
                "не задан ни один API-ключ: впиши ключи в ai/Keys.kt " +
                    "(Gemini, OpenRouter, Gptunnel) и пересобери приложение",
            )
        }
        val (provider, model) = registry.resolve(parsed.model)

        val start = System.nanoTime()
        val response = provider.complete(
            AiRequest(model, listOf(AiMessage(role = "user", content = parsed.prompt))),
        )
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        return LocalResult(
            model = response.model,
            provider = response.provider,
            answer = response.content,
            elapsedMs = elapsedMs,
        )
    }

    companion object {

        fun defaultRegistry(): ProviderRegistry {
            val registry = ProviderRegistry()

            if (Keys.Gemini.isNotEmpty()) {
                registry.register(
                    GeminiProvider(
                        apiKey = Keys.Gemini,
                        models = Keys.GeminiModels.ifEmpty { null },
                    ),
                )
            }
            if (Keys.Gptunnel.isNotEmpty()) {
                registry.register(
                    GptunnelProvider(
                        apiKey = Keys.Gptunnel,
                        models = Keys.GptunnelModels.ifEmpty { null },
                    ),
                )
            }
            if (Keys.OpenRouter.isNotEmpty()) {
                registry.register(
                    OpenRouterProvider(
                        apiKey = Keys.OpenRouter,
                        models = Keys.OpenRouterModels.ifEmpty { null },
                    ),
                )
            }

            registry.setAliases(
                DefaultModelAliases + DefaultFreeModelAliases + Keys.ExtraModelAliases,
            )
            return registry
        }
    }
}
