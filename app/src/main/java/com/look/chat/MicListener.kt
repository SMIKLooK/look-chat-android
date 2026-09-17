package com.look.chat

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/**
 * Непрерывное офлайн-распознавание речи на Vosk.
 *
 * В отличие от системного SpeechRecognizer у этого пути нет лимита сессий:
 * Vosk сам читает микрофон бесконечно, поэтому ассистент не «умирает»
 * после нескольких запросов и работает с выключенным экраном (wakelock
 * держит сервис). Модель — vosk-model-small-ru, распаковывается из assets
 * при первом запуске.
 */
class MicListener(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val tag = "LookMic"
    private val main = Handler(Looper.getMainLooper())

    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null

    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        Thread({
            try {
                // Распаковывает assets/model-small-ru во внутреннее хранилище
                // (идемпотентно) и возвращает путь к модели.
                val path = StorageService.sync(context, "model-small-ru", "model-small-ru")
                val model = cachedModel ?: Model(path).also { cachedModel = it }
                val rec = Recognizer(model, 16000f)
                recognizer = rec
                val service = SpeechService(rec, 16000f)
                speechService = service
                service.startListening(object : RecognitionListener {
                    override fun onPartialResult(hypothesis: String?) {
                        val text = jsonField(hypothesis, "partial")
                        if (text.isNotBlank()) main.post { onPartial(text) }
                    }

                    override fun onResult(result: String?) {
                        // Промежуточный полный результат (после reset/паузы).
                    }

                    override fun onFinalResult(result: String?) {
                        val text = jsonField(result, "text")
                        if (text.isNotBlank()) main.post { onFinal(text) }
                    }

                    override fun onError(e: Exception?) {
                        Log.w(tag, "vosk recognition error", e)
                    }

                    override fun onTimeout() {}
                })
                main.post { onReady() }
            } catch (t: Throwable) {
                Log.e(tag, "mic failed", t)
                running = false
                main.post { onError(t.message ?: t.javaClass.simpleName) }
            }
        }, "LookMic").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /** Приостановить распознавание (на время озвучки, чтобы не слушать себя).
     *  reset() сбрасывает недоговорённый хвост фразы. */
    fun suspendMic() {
        speechService?.setPause(true)
        speechService?.reset()
    }

    fun resumeMic() {
        speechService?.reset() // сбрасываем накопленные хвосты фраз
        speechService?.setPause(false)
    }

    fun stop() {
        running = false
        try {
            speechService?.stop()
        } catch (e: Exception) {
            Log.w(tag, "service stop", e)
        }
        try {
            speechService?.shutdown()
        } catch (e: Exception) {
            Log.w(tag, "service shutdown", e)
        }
        speechService = null
        try {
            recognizer?.close()
        } catch (e: Exception) {
            Log.w(tag, "recognizer close", e)
        }
        recognizer = null
    }

    private fun jsonField(json: String?, field: String): String =
        try {
            JSONObject(json ?: "").optString(field, "")
        } catch (e: Exception) {
            ""
        }

    companion object {
        // Модель тяжело грузить заново при каждом включении ассистента —
        // держим её на всё время жизни процесса.
        @Volatile
        private var cachedModel: Model? = null
    }
}
