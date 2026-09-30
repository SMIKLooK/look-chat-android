package com.look.chat.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.look.chat.logic.VoiceCommandParser
import com.look.chat.voice.audio.MicListener


class VoiceSessionManager(
    private val context: Context,
    private val wakeWord: () -> String,
    private val endWord: () -> String,
    private val onDictatingChanged: (Boolean) -> Unit,
    private val onHeardChanged: (String) -> Unit,
    private val onCommandFinalized: (cleanText: String) -> Unit,
    private val onStatusUpdate: (String) -> Unit,
    private val onSystemMessage: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var mic: MicListener? = null

    // --- состояние диктовки ---

    internal var voiceArmed = false
        private set

    // Кандидат в кодовое слово ждёт подтверждения полным распознавателем.
    internal var confirmingWake = false
        private set
    internal var confirmStartedAt = 0L

    // Запрос в полёте: события микрофона игнорируются.
    internal var voiceBusy = false
        private set

    private val voiceBuffer = StringBuilder()
    private var currentPartial = ""

    // Частичный результат, по которому сработало кодовое слово: финальный
    // результат той же фразы заменяет его содержимое (иначе текст задвоится).
    internal var pendingSegmentPartial: String? = null
        private set

    // Время последней речи: по нему считается тишина для автосохранения.
    internal var lastVoiceActivityAt = 0L

    private val silenceCheck = object : Runnable {
        override fun run() {
            if (confirmingWake && System.currentTimeMillis() - confirmStartedAt > CONFIRM_TIMEOUT_MS) {
                rejectWakeCandidate()
            }
            checkSilenceSend()
            main.postDelayed(this, 1000)
        }
    }

    // --- жизненный цикл ---

    fun start() {
        mic = MicListener(
            context = context,
            wakeWord = wakeWord,
            onPartial = ::onMicPartial,
            onFinal = ::onMicFinal,
            onReady = {
                onStatusUpdate("Слушаю кодовое слово «${wakeWord()}»")
            },
            onError = { message ->
                onSystemMessage("Микрофон недоступен: $message")
            },
        )
        mic?.start()
        main.postDelayed(silenceCheck, 1000)
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        mic?.stop()
        mic = null
        voiceBusy = false
        resetDictation()
    }

    fun suspendForRequest() {
        mic?.suspendMic()
        resetDictation()
    }

    fun resume() {
        mic?.resumeMic()
        // Пока микрофон был на паузе, «кандидат» мог подтвердиться по
        // остаточному звуку: после возобновления верим только новому
        // распознаванию, иначе первое же слово уйдёт как команда.
        confirmingWake = false
        onStatusUpdate("Слушаю кодовое слово «${wakeWord()}»")
    }

    fun clearBusy() {
        voiceBusy = false
    }

    fun refreshGrammar() {
        mic?.refreshIdleGrammar()
    }

    // --- события микрофона ---

    internal fun onMicPartial(partial: String) {
        if (voiceBusy) return
        lastVoiceActivityAt = System.currentTimeMillis()

        if (!voiceArmed) {
            val tokens = VoiceCommandParser.tokenize(partial)
            val wordIdx = VoiceCommandParser.wakeTokenIndex(tokens, wakeWord())
            if (wordIdx < 0) {
                // До подтверждения кодового слова услышанное не показываем.
                return
            }
            if (!confirmingWake) {
                // Экономный режим озвучивает словом «старт» любую речь — сразу
                // не верим: включаем полный словарь и ждём подтверждения.
                confirmingWake = true
                confirmStartedAt = System.currentTimeMillis()
                mic?.startDictation()
                return
            }
            armDictation(tokens, wordIdx, pendingPartial = partial.trim())
            return
        }

        currentPartial = partial.trim()
        val full = dictationText()
        onHeardChanged(VoiceCommandParser.fromWake(full, wakeWord()))

        val end = endWord()
        if (end.isNotEmpty() && VoiceCommandParser.heardContainsEnd(full, end)) {
            finishDictation(VoiceCommandParser.textBeforeEnd(full, end))
        }
    }

    internal fun onMicFinal(final: String) {
        if (voiceBusy) return
        lastVoiceActivityAt = System.currentTimeMillis()
        Log.d(TAG, "mic final: $final")

        if (!voiceArmed) {
            val tokens = VoiceCommandParser.tokenize(final)
            val wordIdx = VoiceCommandParser.wakeTokenIndex(tokens, wakeWord())
            if (wordIdx < 0) {
                // Фраза без кодового слова: кандидат не подтвердился —
                // тихо возвращаемся в ожидание, без сообщений в чат.
                if (confirmingWake) rejectWakeCandidate()
                return
            }
            if (!confirmingWake) {
                confirmingWake = true
                confirmStartedAt = System.currentTimeMillis()
                mic?.startDictation()
                return
            }
            armDictation(tokens, wordIdx, pendingPartial = null)
            checkEndOrArmDone()
            return
        }

        // Финал заменяет частичный текст той же фразы — иначе задвоится.
        if (pendingSegmentPartial != null) {
            voiceBuffer.clear()
            voiceBuffer.append(VoiceCommandParser.fromWake(final.trim(), wakeWord()))
            pendingSegmentPartial = null
        } else {
            voiceBuffer.append(' ').append(final.trim())
        }
        currentPartial = ""
        checkEndOrArmDone()
    }

    internal fun checkSilenceSend() {
        if (!voiceArmed || voiceBusy) return
        val idle = System.currentTimeMillis() - lastVoiceActivityAt
        if (idle < SILENCE_SEND_MS) return

        val text = VoiceCommandParser.textBeforeEnd(dictationText(), endWord()).trim()
        if (text.isBlank()) {
            // Сказали только кодовое слово и молчим — сбрасываем диктовку.
            resetDictation()
            onSystemMessage("После «${wakeWord()}» не было запроса — слушаю дальше.")
            onStatusUpdate("Слушаю кодовое слово «${wakeWord()}»")
            mic?.resumeMic()
            return
        }
        Log.d(TAG, "silence send: $text")
        finishDictation(text)
    }

    internal fun rejectWakeCandidate() {
        confirmingWake = false
        voiceArmed = false
        onDictatingChanged(false)
        voiceBuffer.clear()
        currentPartial = ""
        pendingSegmentPartial = null
        onHeardChanged("")
        mic?.resumeMic()
    }

    // --- внутренняя кухня ---

    // Кодовое слово подтверждено полным распознавателем — начинаем диктовку.
    private fun armDictation(tokens: List<String>, wordIdx: Int, pendingPartial: String?) {
        confirmingWake = false
        voiceArmed = true
        onDictatingChanged(true)
        voiceBuffer.clear()
        // Кодовое слово оставляем в буфере: в статусе видно «старт …».
        voiceBuffer.append(tokens.drop(wordIdx).joinToString(" "))
        if (pendingPartial != null) {
            pendingSegmentPartial = pendingPartial
            onHeardChanged(voiceBuffer.toString().trim())
        }
        mic?.startDictation() // если полный словарь уже включён — ничего не сделает
        onStatusUpdate(dictatingStatus())
    }

    /** После обновления диктовки: «стоп» — отправить, иначе ждём тишину. */
    private fun checkEndOrArmDone() {
        onHeardChanged(VoiceCommandParser.fromWake(dictationText(), wakeWord()))
        val end = endWord()
        if (end.isNotEmpty()) {
            val full = dictationText()
            if (VoiceCommandParser.heardContainsEnd(full, end)) {
                finishDictation(VoiceCommandParser.textBeforeEnd(full, end))
            }
            // Иначе ждём: слово окончания или тишина (3,5 с).
        }
        // Пустое слово окончания: отправку сделает таймер тишины —
        // короткие паузы в речи запрос не разрывают.
    }

    private fun dictationText(): String {
        val buffered = voiceBuffer.toString().trim()
        val current = currentPartial.trim()
        // Фраза с кодовым словом ещё не закрыта финалом: currentPartial содержит
        // её целиком, вместе с мусором до «старт» и с уже учтённым куском из
        // буфера. Берём только её — от «старт» включительно, иначе задвоится.
        if (pendingSegmentPartial != null && current.isNotEmpty()) {
            return VoiceCommandParser.fromWake(current, wakeWord())
        }
        return if (current.isEmpty()) buffered else "$buffered $current".trim()
    }

    private fun dictatingStatus(): String =
        if (endWord().isEmpty()) "Записываю… пауза отправит запрос"
        else "Записываю… закончите словом «${endWord()}»"

    private fun finishDictation(text: String) {
        voiceArmed = false
        onDictatingChanged(false)
        currentPartial = ""
        pendingSegmentPartial = null
        onHeardChanged("")

        // В запрос и в чат идёт только то, что сказано после кодового слова:
        // Vosk может прислать фразу целиком («раз раз раз старт привет»).
        val clean = VoiceCommandParser.afterWake(text.trim(), wakeWord()).trim()
        if (clean.isBlank()) {
            onSystemMessage("После «${wakeWord()}» не было запроса — слушаю дальше.")
            onStatusUpdate("Слушаю кодовое слово «${wakeWord()}»")
            mic?.resumeMic()
            return
        }
        voiceBusy = true
        mic?.suspendMic()
        onCommandFinalized(clean)
    }

    private fun resetDictation() {
        voiceArmed = false
        confirmingWake = false
        onDictatingChanged(false)
        voiceBuffer.clear()
        currentPartial = ""
        pendingSegmentPartial = null
        onHeardChanged("")
    }

    companion object {
        private const val TAG = "LookVoice"
        private const val SILENCE_SEND_MS = 3500L
        private const val CONFIRM_TIMEOUT_MS = 10_000L
    }
}
