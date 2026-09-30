package com.look.chat.logic

internal object TextMatcher {
    private val WHITESPACE = Regex("\\s+")

    private val LATIN_TO_RU = mapOf(
        'a' to "а", 'b' to "б", 'c' to "к", 'd' to "д", 'e' to "е", 'f' to "ф",
        'g' to "г", 'h' to "х", 'i' to "и", 'j' to "й", 'k' to "к", 'l' to "л",
        'm' to "м", 'n' to "н", 'o' to "о", 'p' to "п", 'q' to "к", 'r' to "р",
        's' to "с", 't' to "т", 'u' to "у", 'v' to "в", 'w' to "в", 'x' to "кс",
        'y' to "и", 'z' to "з",
    )

    fun normalize(word: String): String =
        word.lowercase().replace('ё', 'е').trim(',', '.', '!', '?', ';', ':', ' ')

    fun splitTokens(text: String): List<String> =
        normalize(text).split(WHITESPACE).filter { it.isNotEmpty() }

    fun translateToRu(text: String): String = buildString {
        for (ch in text.lowercase()) append(LATIN_TO_RU[ch] ?: ch)
    }

    fun matches(spoken: String, configured: String): Boolean {
        val a = normalize(spoken)
        val b = normalize(configured)
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val ta = translateToRu(a)
        val tb = translateToRu(b)
        if (ta == tb) return true
        return b.length >= 5 && levenshtein(ta, tb) <= 2
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in a.indices) {
            curr[0] = i + 1
            for (j in b.indices) {
                curr[j + 1] = minOf(
                    prev[j + 1] + 1,
                    curr[j] + 1,
                    prev[j] + if (a[i] == b[j]) 0 else 1,
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        return prev[b.length]
    }
}
