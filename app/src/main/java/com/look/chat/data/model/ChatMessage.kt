package com.look.chat.data.model

/** Одно сообщение в чате. */
data class ChatMessage(
    val id: Long,
    val fromUser: Boolean,
    val text: String,
    val model: String? = null,
    val provider: String? = null,
    val elapsedMs: Long? = null,
    val isError: Boolean = false,
    val voice: Boolean = false,
)
