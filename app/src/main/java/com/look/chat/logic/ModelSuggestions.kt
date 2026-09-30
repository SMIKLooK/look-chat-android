package com.look.chat.logic

internal object ModelSuggestions {

    fun visible(
        aliases: Map<String, String>,
        pinned: List<String> = RequestText.PINNED_MODELS,
    ): List<String> {
        val pinnedModels = pinned.filter { p ->
            aliases.keys.any { it.equals(p, ignoreCase = true) }
        }
        val pinnedTargets = pinnedModels.mapNotNull { p ->
            aliases.entries.firstOrNull { it.key.equals(p, ignoreCase = true) }?.value
        }.toSet()

        val freeByTarget = mutableMapOf<String, String>()
        aliases.forEach { (alias, target) ->
            val free = target.endsWith(":free") || target.substringAfterLast('/') == "free"
            if (!free || target in pinnedTargets) return@forEach
            val current = freeByTarget[target]
            if (current == null || (isCyrillic(current) && !isCyrillic(alias))) {
                freeByTarget[target] = alias
            }
        }
        return pinnedModels + freeByTarget.values.sorted()
    }

    private fun isCyrillic(text: String): Boolean =
        text.any { it.code in 0x0410..0x04FF }
}
