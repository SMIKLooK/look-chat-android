package com.look.chat.voice.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.sun.jna.Library
import com.sun.jna.Native
import com.look.chat.data.Settings
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService

/**
 * Непрерывное офлайн-распознавание речи на Vosk с двумя режимами.
 *
 * ОЖИДАНИЕ (кодовое слово не сказано): работает распознаватель с грамматикой
 * из одного слова. Граф поиска в таком режиме в разы меньше полного словаря,
 * поэтому в простое расходуется заметно меньше процессора. Обратная сторона:
 * такой распознаватель озвучивает словом «старт» ЛЮБУЮ похожую речь, поэтому
 * его срабатывание — только кандидат, который проверяет полный распознаватель.
 *
 * ПРОВЕРКА И ДИКТОВКА (кандидат появился): работает распознаватель с полным
 * словарём. Сначала через него прокручиваются последние секунды записанного
 * звука: он прогревается адаптацией под голос и подтверждает (или отвергает)
 * кодовое слово по настоящей расшифровке. Подтвердил — идёт разбор запроса.
 *
 * Распознаватели подменяются прямо в цикле чтения микрофона — звук при этом
 * непрерывно копится в очереди и не теряется. В отличие от системного
 * SpeechRecognizer лимитов сессий нет: микрофон слушает бесконечно и работает
 * с выключенным экраном (wakelock держит сервис). Модель — vosk-model-small-ru,
 * распаковывается из assets при первом запуске.
 */
class MicListener(
    private val context: Context,
    private val wakeWord: () -> String,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var running = false
    @Volatile private var paused = false

    // Сейчас идёт диктовка (распознаватель с полным словарём).
    @Volatile private var dictating = false

    // Флаги для цикла чтения микрофона: он сам подменяет распознаватель
    // и делает reset — нативные вызовы идут только из одного потока.
    @Volatile private var swapToDictation = false
    @Volatile private var swapToIdle = false
    @Volatile private var pendingReset = false

    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            runLoop()
        }, "LookMic").apply {
            start()
        }
    }

    // Кандидат в кодовое слово: полный словарь проверит и разберёт запрос.
    fun startDictation() {
        if (!running || dictating) return
        dictating = true
        swapToDictation = true
        swapToIdle = false
    }

    // Запрос отправлен, отменён или кандидат отвергнут: назад к поиску слова.
    fun resumeMic() {
        paused = false
        pendingReset = true
        if (dictating) {
            dictating = false
            swapToIdle = true
            swapToDictation = false
        }
    }

    // Приостановить распознавание (на время запроса и озвучки ответа).
    fun suspendMic() {
        paused = true
        pendingReset = true
    }

    // Кодовое слово изменилось в настройках — пересобрать грамматику ожидания.
    fun refreshIdleGrammar() {
        if (running && !dictating) {
            swapToIdle = true
            pendingReset = true
        }
    }

    fun stop() {
        running = false
        try {
            thread?.join(2000)
        } catch (_: InterruptedException) {
        }
        thread = null
    }

    @SuppressLint("MissingPermission")
    private fun runLoop() {
        var recorder: AudioRecord? = null
        var recognizer: Recognizer? = null
        try {
            val path = StorageService.sync(context, "model-small-ru", "model-small-ru")
            val model = cachedModel ?: Model(path).also { cachedModel = it }
            recognizer = newRecognizer(model, forDictation = false)

            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            )
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, SAMPLE_RATE) * 2, // запас ~0.5 с на подмену распознавателя
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("аудиозапись не инициализировалась")
            }
            recorder.startRecording()
            main.post { onReady() }

            var rec: Recognizer = recognizer
            val buffer = ShortArray(SAMPLE_RATE / 10)
            val live = ArrayDeque<ShortArray>() // звук, накопленный за время подмены
            val recent = ArrayDeque<ShortArray>() // последние секунды звука для прокрутки
            var recentSamples = 0
            var replay: ArrayDeque<ShortArray>? = null

            while (running) {
                // Микрофон опрашиваем в каждом проходе, даже пока крутится
                // история, — иначе внутренний буфер устройства переполнится.
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    if (paused) {
                        // Пока слушаем себя/ждём ответ — историю сбрасываем,
                        // чтобы в неё не попала озвучка самого ассистента.
                        recent.clear()
                        recentSamples = 0
                        // и хвост звука, накопленный до паузы: после
                        // возобновления он должен остаться неуслышанным
                        live.clear()
                    } else {
                        live.addLast(buffer.copyOf(read))
                    }
                }

                if (swapToDictation) {
                    swapToDictation = false
                    rec.close()
                    rec = newRecognizer(model, forDictation = true)
                    recognizer = rec
                    replay = ArrayDeque(recent)
                    recent.clear()
                    recentSamples = 0
                } else if (swapToIdle) {
                    swapToIdle = false
                    rec.close()
                    rec = newRecognizer(model, forDictation = false)
                    recognizer = rec
                    replay = null
                    recent.clear()
                    recentSamples = 0
                }
                if (pendingReset) {
                    pendingReset = false
                    rec.reset()
                }

                // Историю прокручиваем по кусочку за проход — между кусками
                // микрофон успевает опрашиваться, и ничего не теряется.
                val next = replay?.removeFirstOrNull()
                if (next != null) {
                    // Прокрутка нужна только живому распознаванию; на паузе
                    // старый звук должен молчать, а не рождать события.
                    if (!paused) emitWave(rec, next)
                    continue
                }

                // На паузе распознаватель не получает звук вовсе:
                // ни накопленный хвост, ни события из него не нужны.
                if (!paused) {
                    while (live.isNotEmpty()) {
                        val chunk = live.removeFirst()
                        emitWave(rec, chunk)
                        if (!dictating) {
                            // историю копим только в режиме ожидания: она нужна,
                            // чтобы прогреть следующий полный распознаватель
                            recent.addLast(chunk)
                            recentSamples += chunk.size
                            while (recentSamples > REPLAY_SECONDS * SAMPLE_RATE) {
                                recentSamples -= recent.removeFirst().size
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "mic failed", t)
            running = false
            main.post { onError(t.message ?: t.javaClass.simpleName) }
        } finally {
            try {
                recognizer?.close()
            } catch (_: Exception) {
            }
            try {
                recorder?.stop()
            } catch (_: Exception) {
            }
            try {
                recorder?.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun emitWave(rec: Recognizer, data: ShortArray) {
        if (rec.acceptWaveForm(data, data.size)) {
            val text = jsonField(rec.result, "text")
            if (text.isNotBlank()) main.post { onFinal(text) }
        } else {
            val text = jsonField(rec.partialResult, "partial")
            if (text.isNotBlank()) main.post { onPartial(text) }
        }
    }

    //Распознаватель: с грамматикой одного слова для ожидания, полный — для диктовки.
    private fun newRecognizer(model: Model, forDictation: Boolean): Recognizer {
        val grammar = if (forDictation) null else idleGrammar(model)
        return if (grammar != null) Recognizer(model, SAMPLE_RATE.toFloat(), grammar)
        else Recognizer(model, SAMPLE_RATE.toFloat())
    }

    private fun idleGrammar(model: Model): String? {
        val word = wakeWord().trim().ifEmpty { Settings.DEFAULT_WAKE_WORD }
        if (wordInVocabulary(model, word) < 0) {
            Log.w(TAG, "слова «$word» нет в словаре модели — слушаю полный словарь")
            return null
        }
        val escaped = word.replace("\\", "\\\\").replace("\"", "\\\"")
        return "[\"$escaped\"]"
    }
    private fun wordInVocabulary(model: Model, word: String): Int = try {
        CLIB?.vosk_model_find_word(model, word) ?: 1
    } catch (t: Throwable) {
        Log.w(TAG, "проверка словаря недоступна", t)
        1 // при сбое проверки считаем слово существующим, чтобы не оглохнуть
    }

    private fun jsonField(json: String?, field: String): String =
        try {
            JSONObject(json ?: "").optString(field, "")
        } catch (e: Exception) {
            ""
        }

    // Прямой доступ к нативному vosk_model_find_word — в Java-обёртке его нет.
    private interface VoskCLib : Library {
        fun vosk_model_find_word(model: Model?, word: String?): Int
    }

    companion object {
        private const val TAG = "LookMic"
        private const val SAMPLE_RATE = 16000

        private const val REPLAY_SECONDS = 4

        // Модель тяжело грузить заново при каждом включении ассистента —
        // держим её на всё время жизни процесса.
        @Volatile
        private var cachedModel: Model? = null

        private val CLIB: VoskCLib? by lazy {
            try {
                Native.load("vosk", VoskCLib::class.java)
            } catch (t: Throwable) {
                Log.w(TAG, "native vosk lib load failed", t)
                null
            }
        }
    }
}
