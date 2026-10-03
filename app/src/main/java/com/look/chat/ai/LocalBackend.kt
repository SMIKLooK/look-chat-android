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
                "не задан ни один API-ключ: открой настройки (⚙) и добавь свой ключ " +
                    "(Gemini, OpenRouter или Gptunnel)",
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

        fun defaultRegistry(userKeys: Map<String, String> = emptyMap()): ProviderRegistry {
            val registry = ProviderRegistry()

            fun effective(id: String, builtin: String): String =
                userKeys[id]?.trim()?.takeIf { it.isNotEmpty() } ?: builtin

            val gemini = effective("gemini", Keys.Gemini)
            val gptunnel = effective("gptunnel", Keys.Gptunnel)
            val openrouter = effective("openrouter", Keys.OpenRouter)

            if (gemini.isNotEmpty()) {
                registry.register(
                    GeminiProvider(
                        apiKey = gemini,
                        models = Keys.GeminiModels.ifEmpty { null },
                    ),
                )
            }
            if (gptunnel.isNotEmpty()) {
                registry.register(
                    GptunnelProvider(
                        apiKey = gptunnel,
                        models = Keys.GptunnelModels.ifEmpty { null },
                    ),
                )
            }
            if (openrouter.isNotEmpty()) {
                registry.register(
                    OpenRouterProvider(
                        apiKey = openrouter,
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
