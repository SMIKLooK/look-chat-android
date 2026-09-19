package com.look.chat.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Контракт look-backend -----------------------------------------------

/** Тело POST /api/v1/process: текст формата "<модель> <запрос>". */
@Serializable
data class ProcessRequest(val text: String)

/** Ошибочный ответ бекенда: {"status":"error","error":{"code","message"}}. */
@Serializable
data class ApiError(val code: String = "", val message: String = "")

/** Ответ POST /api/v1/process (и ok-, и error-конверт — поля совпадают). */
@Serializable
data class ProcessResponse(
    val status: String = "",
    val model: String? = null,
    val provider: String? = null,
    val answer: String? = null,
    @SerialName("elapsed_ms") val elapsedMs: Long? = null,
    val error: ApiError? = null,
)

/** Один провайдер из GET /api/v1/models. */
@Serializable
data class ProviderInfo(val name: String = "", val models: List<String> = emptyList())

/** Ответ GET /api/v1/models: ключевые слова, псевдонимы, провайдеры. */
@Serializable
data class ModelsResponse(
    val keywords: List<String> = emptyList(),
    val aliases: Map<String, String> = emptyMap(),
    val providers: List<ProviderInfo> = emptyList(),
)
