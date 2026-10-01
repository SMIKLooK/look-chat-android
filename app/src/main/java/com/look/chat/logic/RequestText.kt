package com.look.chat.logic

internal object RequestText {

    const val DEFAULT_MODEL = "фри"

    val PINNED_MODELS = listOf(
        "deepseek", "gemini", "claude", "gpt", "perplexity", "фри",
    )

    val ALIASES = mapOf(
        "гемини" to "gemini",
        "джемини" to "gemini",
        "геминис" to "gemini",
        "дипсик" to "deepseek",
        "депсик" to "deepseek",
        "клод" to "claude",
        "гпт" to "gpt",
        "перплексити" to "perplexity",
    )
    val PASSTHROUGH_MODELS = setOf("фри", "free", "deepseek", "claude", "gpt")
    val PHRASE_ALIASES = mapOf(
        "дип сик" to "deepseek",
        "ди псих" to "deepseek",
        "дип сикс" to "deepseek",
        "дип сок" to "deepseek",
        "деп сик" to "deepseek",
        "депп сик" to "deepseek",
        "деп сок" to "deepseek",
        "инклинг мини" to "инклинг-мини",
        "немотрон ультра" to "немотрон-ультра",
        "немотрон супер" to "немотрон-супер",
        "немотрон лайт" to "немотрон-лайт",
        "немотрон омни" to "немотрон-омни",
        "гемма мини" to "гемма-мини",
        "лагуна мини" to "лагуна-мини",
        "норд код" to "норд-код",
        "линг мед" to "линг-мед",
        "линг фин" to "линг-фин",
        "некс мини" to "некс-мини",
    )

    fun build(
        tokens: List<String>,
        customWords: Map<String, String>,
        serverModels: List<String>,
        insertDefaultModel: Boolean,
    ): String {
        if (tokens.isEmpty()) return ""
        val normTokens = tokens.map { TextMatcher.normalize(it) }

        customWords.forEach { (word, model) ->
            val wordTokens = TextMatcher.splitTokens(word)
            if (wordTokens.isEmpty() || wordTokens.size > normTokens.size) return@forEach
            for (start in 0..normTokens.size - wordTokens.size) {
                val segment = normTokens.subList(start, start + wordTokens.size)
                val matched = wordTokens.indices.all { k ->
                    TextMatcher.matches(segment[k], wordTokens[k])
                }
                if (matched) {
                    val restTokens = tokens.take(start) + tokens.drop(start + wordTokens.size)
                    return (listOf(model) + restTokens).joinToString(" ")
                }
            }
        }

        if (normTokens.size >= 2) {
            PHRASE_ALIASES["${normTokens[0]} ${normTokens[1]}"]?.let { phraseModel ->
                return (listOf(phraseModel) + tokens.drop(2)).joinToString(" ")
            }
        }

        ALIASES[normTokens[0]]?.let { alias ->
            return (listOf(alias) + tokens.drop(1)).joinToString(" ")
        }

        // Известные движку слова уходят как есть — там свои алиасы.
        if (normTokens[0] in PASSTHROUGH_MODELS) {
            return tokens.joinToString(" ")
        }

        // Точно известная модель (весь список с сервера).
        if (serverModels.any { TextMatcher.normalize(it) == normTokens[0] }) {
            return tokens.joinToString(" ")
        }

        // Если модель не названа подставляем модель по умолчанию.
        if (insertDefaultModel) {
            return (listOf(DEFAULT_MODEL) + tokens).joinToString(" ")
        }
        return tokens.joinToString(" ")
    }

    fun backendKeywords(serverKeywords: List<String>): Set<String> {
        val fromServer = serverKeywords.map { it.lowercase() }.toSet()
        return if (fromServer.isEmpty()) setOf("старт", "start") else fromServer
    }
}
