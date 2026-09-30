package com.look.chat.logic

internal object SpeechText {

    fun prepare(text: String, skipChars: String): String {
        val filtered = if (skipChars.isEmpty()) text else text.filter { !skipChars.contains(it) }
        return filtered.replace(Regex("\\s+"), " ").trim()
    }


    fun split(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotBlank()) chunks.add(current.toString().trim())
            current.clear()
        }
        for (sentence in text.split(Regex("(?<=[.!?…])\\s+"))) {
            var piece = sentence
            if (piece.length > max) {
                flush()
                while (piece.length > max) {
                    val space = piece.lastIndexOf(' ', max)
                    val cut = if (space > 0) space else max
                    chunks.add(piece.take(cut).trim())
                    piece = piece.drop(cut).trimStart()
                }
                current.append(piece)
                continue
            }
            if (current.length + piece.length + 1 > max) flush()
            if (current.isNotEmpty()) current.append(' ')
            current.append(piece)
        }
        flush()
        return chunks.filter { it.isNotBlank() }
    }
}
