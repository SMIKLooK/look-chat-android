package com.look.chat.logic

internal object VoiceCommandParser {
    private val WHITESPACE = Regex("\\s+")
    private val END_SEPARATORS = Regex("[\\s,.!?;:]+")

    // Разбивает сырой текст распознавания на токены (без нормализации).
    fun tokenize(text: String): List<String> = text.trim().split(WHITESPACE)

    fun wakeTokenIndex(tokens: List<String>, wake: String): Int {
        val w = TextMatcher.normalize(wake)
        return tokens.indexOfFirst { matchesWake(TextMatcher.normalize(it), w) }
    }

    // Кодовое слово прощает небольшие описки распознавания («старта» → «старт»)
    fun isWakeToken(token: String, wake: String): Boolean =
        matchesWake(TextMatcher.normalize(token), TextMatcher.normalize(wake))

    fun isEndToken(token: String, end: String): Boolean =
        matchesEnd(TextMatcher.normalize(token), TextMatcher.normalize(end))

    fun heardContainsEnd(text: String, end: String): Boolean {
        val e = TextMatcher.normalize(end)
        return text.split(END_SEPARATORS).any { matchesEnd(TextMatcher.normalize(it), e) }
    }

    // Текст до слова окончания; если слова нет — текст как есть.
    fun textBeforeEnd(text: String, end: String): String {
        val e = TextMatcher.normalize(end)
        val tokens = text.trim().split(WHITESPACE)
        val idx = tokens.indexOfFirst { matchesEnd(TextMatcher.normalize(it), e) }
        return if (idx >= 0) tokens.take(idx).joinToString(" ") else text
    }

    // Текст после кодового слова: Vosk может прислать мусор до него
    fun afterWake(text: String, wake: String): String {
        val tokens = text.trim().split(WHITESPACE)
        val idx = indexOfWake(tokens, wake)
        return if (idx >= 0) tokens.drop(idx + 1).joinToString(" ") else text
    }

    fun fromWake(text: String, wake: String): String {
        val tokens = text.trim().split(WHITESPACE)
        val idx = indexOfWake(tokens, wake)
        return if (idx >= 0) tokens.drop(idx).joinToString(" ") else text
    }


    private fun indexOfWake(tokens: List<String>, wake: String): Int {
        val w = TextMatcher.normalize(wake)
        return tokens.indexOfFirst { matchesWake(TextMatcher.normalize(it), w) }
    }

    private fun matchesWake(t: String, w: String): Boolean =
        t == w || (w.length >= 3 && t.startsWith(w) && t.length <= w.length + 2)

    private fun matchesEnd(t: String, e: String): Boolean =
        t == e || (e.length >= 3 && t.startsWith(e) && t.length <= e.length + 1)
}
