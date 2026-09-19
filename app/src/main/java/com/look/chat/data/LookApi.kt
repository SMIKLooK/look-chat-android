package com.look.chat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP-клиент look-backend.
 *
 * Адрес сервера передаётся в каждом вызове, потому что пользователь может
 * поменять его в настройках приложения (эмулятор → 10.0.2.2, телефон → IP
 * компьютера в локальной сети).
 */
class LookApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /** Отправляет текст в формате "<модель> <запрос>" и ждёт ответ модели. */
    suspend fun process(serverUrl: String, text: String): ProcessResponse =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(ProcessRequest(text))
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(processUrl(serverUrl))
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                // Бекенд и ошибки возвращает тем же JSON-конвертом — пробуем разобрать,
                // иначе подставляем HTTP-код.
                try {
                    json.decodeFromString<ProcessResponse>(raw)
                } catch (e: Exception) {
                    if (response.isSuccessful) throw e
                    throw IOException("HTTP ${response.code}")
                }
            }
        }

    /** Список ключевых слов, псевдонимов и моделей (для подсказок в чате). */
    suspend fun models(serverUrl: String): ModelsResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(modelsUrl(serverUrl))
            .get()
            .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                json.decodeFromString<ModelsResponse>(raw)
            }
        }

    companion object {
        fun processUrl(base: String) = base.trimEnd('/') + "/api/v1/process"
        fun modelsUrl(base: String) = base.trimEnd('/') + "/api/v1/models"
    }
}
