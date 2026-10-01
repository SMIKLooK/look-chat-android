package com.look.chat.ai

data class AiMessage(val role: String, val content: String)

data class AiRequest(val model: String, val messages: List<AiMessage>)

data class AiResponse(val model: String, val provider: String, val content: String)

class AiError(val code: String, message: String) : Exception(message) {
    companion object {
        const val EMPTY_TEXT = "EMPTY_TEXT"
        const val MODEL_NOT_SPECIFIED = "MODEL_NOT_SPECIFIED"
        const val PROMPT_NOT_SPECIFIED = "PROMPT_NOT_SPECIFIED"
        const val UNKNOWN_MODEL = "UNKNOWN_MODEL"
        const val PROVIDER_ERROR = "PROVIDER_ERROR"
        const val TIMEOUT = "TIMEOUT"
        const val INTERNAL = "INTERNAL"
    }
}

interface AiProvider {
    val name: String

    val models: List<String>

    fun supports(model: String): Boolean

    suspend fun complete(request: AiRequest): AiResponse
}
