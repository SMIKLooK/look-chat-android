
package com.look.chat.ai

data class ParsedRequest(val model: String, val prompt: String)

object RequestParser {

    fun parse(text: String): ParsedRequest {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            throw AiError(AiError.EMPTY_TEXT, "text обязателен и не может быть пустым")
        }

        val (model, prompt) = splitModelAndPrompt(trimmed)
        if (model.isEmpty()) {
            throw AiError(
                AiError.MODEL_NOT_SPECIFIED,
                "не указана модель; формат: \"<модель> <запрос>\"",
            )
        }
        if (prompt.isEmpty()) {
            throw AiError(
                AiError.PROMPT_NOT_SPECIFIED,
                "после модели не указан запрос; формат: \"<модель> <запрос>\"",
            )
        }
        return ParsedRequest(model, prompt)
    }

    private fun splitModelAndPrompt(text: String): Pair<String, String> {
        var i = 0
        val n = text.length
        while (i < n && text[i].isSeparatorRune()) i++
        val start = i
        while (i < n && !text[i].isWhitespace()) i++
        var token = text.substring(start, i)
        var tail = text.substring(i)
        if (token.isEmpty()) return "" to ""

        val colon = token.indexOf(':')
        if (colon >= 0) {
            tail = token.substring(colon + 1) + tail
            token = token.substring(0, colon)
        }
        token = token.trimEnd(',', ';', ':', '.', '?', '!')
        var prompt = tail.trim()
        if (prompt.startsWith(":")) prompt = prompt.substring(1).trim()
        return token to prompt.trim()
    }

    private fun Char.isSeparatorRune(): Boolean =
        isWhitespace() || this == ':' || this == ',' || this == ';' ||
            this == '.' || this == '?' || this == '!'
}
