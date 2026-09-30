package com.look.chat.voice.audio

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.look.chat.logic.SpeechText
import java.util.Locale

class TtsSpeaker(
    private val onSystemMessage: (String) -> Unit,
    private val onSpeakingChanged: (Boolean) -> Unit = {},
) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null

    var isReady = false
        private set

    var isSpeaking = false
        private set

    private var speakDoneCallback: (() -> Unit)? = null

    private var lastUtteranceId: String? = null

    private val finishFallback = Runnable { if (isSpeaking) finishSpeaking() }

    // Создаёт движок речи; безопасно вызывать сколько угодно раз.
    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            main.post {
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.forLanguageTag("ru-RU"))
                        ?: TextToSpeech.LANG_NOT_SUPPORTED
                    isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                    Log.d(TAG, "TTS ready=$isReady (language result=$result)")
                    if (!isReady) {
                        onSystemMessage("Русский голос в системе не найден — ответы будут только текстом.")
                    }
                } else {
                    Log.w(TAG, "TTS init failed: $status")
                    onSystemMessage("Синтез речи недоступен — ответы будут только текстом.")
                }
            }
        }.also { engine ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    onFinished(utteranceId)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onFinished(utteranceId, flushQueue = true)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    onFinished(utteranceId, flushQueue = true)
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    onFinished(utteranceId)
                }
            })
        }
    }

    fun speak(text: String, skipChars: String, onDone: () -> Unit) {
        if (!isReady || tts == null) {
            onDone()
            return
        }
        val prepared = SpeechText.prepare(text, skipChars)
        val chunks = SpeechText.split(prepared, TextToSpeech.getMaxSpeechInputLength())
        if (chunks.isEmpty()) {
            onDone()
            return
        }
        isSpeaking = true
        onSpeakingChanged(true)
        // Новая озвучка перебивает старую: её колбэк отменяется, отложенная
        // страховка stop() не должна завершить уже новую очередь.
        main.removeCallbacks(finishFallback)
        speakDoneCallback = onDone
        // Первый кусок перебивает текущую озвучку, остальные встают в очередь.
        val stamp = System.currentTimeMillis()
        chunks.forEachIndexed { index, chunk ->
            val id = "look_${stamp}_$index"
            if (index == chunks.lastIndex) lastUtteranceId = id
            tts?.speak(
                chunk,
                if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                Bundle(),
                id,
            )
        }
    }

    fun stop() {
        if (!isSpeaking) return
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "tts stop", e)
        }
        // Обычно после stop() прилетает колбэк onStop; подстраховаться.
        main.postDelayed(finishFallback, 500)
    }

    fun shutdown() {
        main.removeCallbacksAndMessages(null)
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "tts shutdown", e)
        }
        tts = null
        isReady = false
        isSpeaking = false
        speakDoneCallback = null
        lastUtteranceId = null
    }

    private fun onFinished(utteranceId: String?, flushQueue: Boolean = false) {
        main.post {
            if (flushQueue) {
                try {
                    tts?.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "tts stop on error", e)
                }
            }
            if (utteranceId == null || utteranceId == lastUtteranceId) {
                finishSpeaking()
            }
        }
    }

    private fun finishSpeaking() {
        if (!isSpeaking) return
        isSpeaking = false
        onSpeakingChanged(false)
        val callback = speakDoneCallback
        speakDoneCallback = null
        callback?.invoke()
    }

    private companion object {
        const val TAG = "LookTts"
    }
}
