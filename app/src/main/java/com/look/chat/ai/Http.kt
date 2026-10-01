package com.look.chat.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class HttpResult(val code: Int, val body: String)

internal object Http {

    val json = Json { ignoreUnknownKeys = true }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun body(content: String) = content.toRequestBody(jsonMedia)

    suspend fun doCall(client: OkHttpClient, name: String, request: Request): HttpResult {
        val response = try {
            execute(client, request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw AiError(AiError.TIMEOUT, "таймаут запроса к $name")
        } catch (e: IOException) {
            throw AiError(
                AiError.PROVIDER_ERROR,
                "$name недоступен: ${e.message ?: e.javaClass.simpleName}",
            )
        }
        return response.use { HttpResult(it.code, it.body?.string().orEmpty()) }
    }

    private suspend fun execute(client: OkHttpClient, request: Request): Response =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) cont.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            })
        }

    fun truncate(body: String): String =
        if (body.length > 512) body.take(512) + "..." else body
}

internal fun chatRequestJson(model: String, messages: List<AiMessage>, maxTokens: Int): String =
    buildJsonObject {
        put("model", model)
        put(
            "messages",
            JsonArray(messages.map { message ->
                buildJsonObject {
                    put("role", message.role)
                    put("content", message.content)
                }
            }),
        )
        if (maxTokens > 0) put("max_tokens", maxTokens)
    }.toString()

internal fun chatReply(
    providerName: String,
    result: HttpResult,
    onError: ((code: Int, errText: String?, msg: String) -> Unit)? = null,
): String {
    val root = try {
        Http.json.parseToJsonElement(result.body).jsonObject
    } catch (e: Exception) {
        throw AiError(
            AiError.PROVIDER_ERROR,
            "$providerName вернул некорректный ответ (HTTP ${result.code}): ${Http.truncate(result.body)}",
        )
    }
    val errText = extractErrorText(root["error"])
    if (result.code < 200 || result.code >= 300) {
        val msg = buildString {
            append("HTTP ${result.code}")
            if (!errText.isNullOrEmpty()) append(": ").append(errText)
        }
        if (onError != null) onError(result.code, errText, msg)
        throw AiError(AiError.PROVIDER_ERROR, "ошибка $providerName: $msg")
    }
    val choices = root["choices"] as? JsonArray
    if (choices.isNullOrEmpty()) {
        throw AiError(AiError.PROVIDER_ERROR, "$providerName вернул пустой список choices")
    }
    val content = (choices.firstOrNull() as? JsonObject)
        ?.get("message")?.let { it as? JsonObject }
        ?.get("content")?.let { it as? JsonPrimitive }
        ?.contentOrNull
    return content.orEmpty().trim()
}

internal fun extractErrorText(error: JsonElement?): String? = when (error) {
    is JsonObject -> ((error["message"] ?: error["description"]) as? JsonPrimitive)?.contentOrNull
    is JsonPrimitive -> error.contentOrNull
    else -> null
}
