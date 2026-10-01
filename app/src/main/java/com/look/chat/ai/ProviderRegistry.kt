package com.look.chat.ai

class ProviderRegistry(providers: List<AiProvider> = emptyList()) {

    private val items = providers.toMutableList()

    @Volatile
    var aliases: Map<String, String> = emptyMap()
        private set

    val providers: List<AiProvider>
        get() = synchronized(items) { items.toList() }

    fun register(provider: AiProvider) {
        synchronized(items) {
            items.removeAll { it.name == provider.name }
            items.add(provider)
        }
    }

    fun setAliases(aliases: Map<String, String>) {
        this.aliases = aliases.mapKeys { it.key.lowercase() }
    }

    fun resolve(model: String): Pair<AiProvider, String> {
        val target = aliases[model.lowercase()] ?: model
        providers.forEach { provider ->
            if (provider.supports(target)) return provider to target
        }
        throw AiError(AiError.UNKNOWN_MODEL, "неизвестная модель: $model")
    }
}
