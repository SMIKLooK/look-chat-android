package com.look.chat

import android.content.Context
import android.util.Log
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.look.chat.ai.AiError
import com.look.chat.ai.LocalBackend
import com.look.chat.data.Settings
import com.look.chat.data.model.ChatMessage
import com.look.chat.data.model.ModelsState
import com.look.chat.logic.ModelSuggestions
import com.look.chat.logic.RequestText
import com.look.chat.voice.VoiceSessionManager
import com.look.chat.voice.audio.BeepGenerator
import com.look.chat.voice.audio.TtsSpeaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

object AssistantEngine {
    private const val TAG = "LookAssistant"
    private val WHITESPACE_REGEX = Regex("\\s+")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var appContext: Context? = null
    private var settings: Settings? = null

    internal var backend: LocalBackend = LocalBackend(LocalBackend.defaultRegistry())

    private val nextId = AtomicLong(1L)
    private fun nextMessageId(): Long = nextId.getAndIncrement()

    internal val _messages = MutableStateFlow(listOf(welcomeMessage()))
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    internal val _input = MutableStateFlow(TextFieldValue(""))
    val input: StateFlow<TextFieldValue> = _input.asStateFlow()

    internal val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    internal val _models = MutableStateFlow(ModelsState())
    val models: StateFlow<ModelsState> = _models.asStateFlow()

    internal val _assistantActive = MutableStateFlow(false)
    val assistantActive: StateFlow<Boolean> = _assistantActive.asStateFlow()

    internal val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    internal val _wakeWord = MutableStateFlow(Settings.DEFAULT_WAKE_WORD)
    val wakeWord: StateFlow<String> = _wakeWord.asStateFlow()

    internal val _endWord = MutableStateFlow(Settings.DEFAULT_END_WORD)
    val endWord: StateFlow<String> = _endWord.asStateFlow()

    internal val _beepIntervalSec = MutableStateFlow(Settings.DEFAULT_BEEP_INTERVAL_SEC)
    val beepIntervalSec: StateFlow<Int> = _beepIntervalSec.asStateFlow()

    internal val _ttsSkipChars = MutableStateFlow(Settings.DEFAULT_TTS_SKIP_CHARS)
    val ttsSkipChars: StateFlow<String> = _ttsSkipChars.asStateFlow()

    internal val _customModelWords = MutableStateFlow<Map<String, String>>(emptyMap())
    val customModelWords: StateFlow<Map<String, String>> = _customModelWords.asStateFlow()

    internal val _dictating = MutableStateFlow(false)
    val dictating: StateFlow<Boolean> = _dictating.asStateFlow()

    internal val _heard = MutableStateFlow("")
    val heard: StateFlow<String> = _heard.asStateFlow()

    private val _assistantStatus = MutableStateFlow("")
    val assistantStatus: StateFlow<String> = _assistantStatus.asStateFlow()

    private var session: VoiceSessionManager? = null

    private val ttsSpeaker = TtsSpeaker(
        onSystemMessage = ::addSystem,
        onSpeakingChanged = { isSpeaking ->
            _speaking.value = isSpeaking
            if (isSpeaking && _assistantActive.value) pushAssistantStatus("Отвечаю голосом…")
        },
    )

    private val beeper = BeepGenerator(
        canBeep = {
            val s = session
            (s == null || (!s.voiceArmed && !s.voiceBusy)) && !_speaking.value
        },
        intervalSec = { _beepIntervalSec.value },
    )

    private fun welcomeMessage() = ChatMessage(
        id = 0,
        fromUser = false,
        text = "Привет! Тут два режима.\n\n" +
            "Руками: пишите просто вопрос — модель подставится сама " +
            "(по умолчанию фри). Можно и по-старому: «старт <модель> <запрос>».\n\n" +
            "Голосом: нажмите 🎙 в шапке, скажите кодовое слово (по умолчанию «старт»), " +
            "наговорите запрос и завершите словом «стоп» — или просто помолчите " +
            "3,5 секунды, запрос уйдёт сам. Пока ассистент включён, он раз в " +
            "несколько секунд подаёт короткий сигнал «я работаю». " +
            "Всё настраивается в ⚙.\n\n" +
            "Во время озвучки тап по строке статуса останавливает голос.\n\n" +
            "Долгое нажатие на сообщение — копирование.",
        createdAt = System.currentTimeMillis(),
    )

    private fun addSystem(text: String) {
        val msg = ChatMessage(
            id = nextMessageId(),
            fromUser = false,
            text = text,
            createdAt = System.currentTimeMillis(),
        )
        _messages.update { it + msg }
    }

    private fun pushAssistantStatus(text: String) {
        _assistantStatus.value = text
    }

    fun ensureInit(context: Context) {
        val app = context.applicationContext
        if (appContext === app) return
        appContext = app
        val s = Settings(app)
        settings = s
        _wakeWord.value = s.wakeWord
        _beepIntervalSec.value = s.beepIntervalSec
        _endWord.value = s.endWord
        _ttsSkipChars.value = s.ttsSkipChars
        _customModelWords.value = s.customModelWords
        refreshModels()
    }


    fun onInputChange(value: TextFieldValue) {
        _input.value = value
    }

    fun onModelPicked(model: String) {
        val current = _input.value
        val base = current.text.trimEnd()
        val newText = if (base.isEmpty()) "$model " else "$base $model "
        _input.value = TextFieldValue(text = newText, selection = TextRange(newText.length))
    }

    fun saveSettings(wake: String, end: String, skipChars: String, beepSec: Int) {
        val cleanedWake = wake.trim().lowercase()
        if (cleanedWake.isNotEmpty() && cleanedWake != _wakeWord.value) {
            settings?.wakeWord = cleanedWake
            _wakeWord.value = cleanedWake
            addSystem("Кодовое слово ассистента: «$cleanedWake»")
            if (_assistantActive.value) {
                pushAssistantStatus("Слушаю кодовое слово «$cleanedWake»")
                session?.refreshGrammar()
            }
        }

        val cleanedEnd = end.trim().lowercase()
        if (cleanedEnd != _endWord.value) {
            settings?.endWord = cleanedEnd
            _endWord.value = cleanedEnd
            addSystem(
                if (cleanedEnd.isEmpty()) "Голосовой ввод отправляется после паузы."
                else "Голосовой ввод завершается словом: «$cleanedEnd»"
            )
        }

        if (skipChars != _ttsSkipChars.value) {
            settings?.ttsSkipChars = skipChars
            _ttsSkipChars.value = skipChars
            addSystem(
                if (skipChars.isEmpty()) "Все символы озвучиваются как есть."
                else "Перед озвучкой вырезаются символы: $skipChars"
            )
        }

        if (beepSec != _beepIntervalSec.value) {
            settings?.beepIntervalSec = beepSec
            _beepIntervalSec.value = beepSec
            addSystem(
                if (beepSec <= 0) "Сигнал «я работаю» выключен."
                else "Сигнал «я работаю»: раз в $beepSec с."
            )
        }
    }

    fun saveCustomWord(word: String, model: String) {
        val w = word.trim().lowercase()
        val m = model.trim()
        if (w.isEmpty() || m.isEmpty()) return
        val updated = _customModelWords.value + (w to m)
        settings?.customModelWords = updated
        _customModelWords.value = updated
        addSystem("Слово «$w» теперь означает модель $m")
    }

    fun removeCustomWord(word: String) {
        val updated = _customModelWords.value - word
        settings?.customModelWords = updated
        _customModelWords.value = updated
        addSystem("Слово «$word» удалено")
    }

    fun refreshModels() {
        val registry = backend.registry
        val providers = registry.providers
        val aliases = registry.aliases
        val servable = aliases.filterValues { target ->
            providers.any { it.supports(target) }
        }
        val allNames = (
            aliases.keys +
                providers.flatMap { it.models } +
                providers.map { it.name }
            ).distinct()
        _models.value = ModelsState(
            keywords = emptyList(),
            suggestions = ModelSuggestions.visible(servable),
            serverModels = allNames,
            providers = providers.map { it.name },
        )
    }

    /**
     * Отправка сообщения, набранного руками. Ключевое слово («старт»)
     * не обязательно и в чате показывается как есть: движку оно
     * не уходит — там формат «<модель> <запрос>».
     */
    fun send() {
        val text = _input.value.text.trim()
        if (text.isEmpty() || _loading.value) return
        _input.value = TextFieldValue("")

        val tokens = text.split(WHITESPACE_REGEX)
        val keywordIdx = tokens.indexOfFirst {
            it.lowercase().trim(',', '.', '!', '?', ';', ':') in
                RequestText.backendKeywords(_models.value.keywords)
        }
        val rest = if (keywordIdx >= 0) tokens.drop(keywordIdx + 1) else tokens
        submit(
            text,
            RequestText.build(
                tokens = rest,
                customWords = _customModelWords.value,
                serverModels = _models.value.serverModels,
                insertDefaultModel = true,
            ),
            fromVoice = false,
        )
    }

    fun onMicPermissionDenied() {
        addSystem("Без разрешения на микрофон голосовой ассистент не работает. " +
            "Нажмите 🎙 и разрешите доступ.")
    }

    @Volatile
    private var requestJob: Job? = null

    private fun submit(displayText: String, requestText: String, fromVoice: Boolean) {
        Log.d(TAG, "submit display=$displayText request=$requestText")
        val userMsg = ChatMessage(
            id = nextMessageId(),
            fromUser = true,
            text = displayText,
            voice = fromVoice,
            createdAt = System.currentTimeMillis(),
        )
        _messages.update { it + userMsg }
        _loading.value = true
        if (_assistantActive.value) {
            session?.suspendForRequest()
            pushAssistantStatus("Отправляю запрос…")
        }

        requestJob = scope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    try {
                        val result = backend.process(requestText)
                        ChatMessage(
                            id = nextMessageId(),
                            fromUser = false,
                            text = result.answer,
                            model = result.model,
                            provider = result.provider,
                            elapsedMs = result.elapsedMs,
                            createdAt = System.currentTimeMillis(),
                        )
                    } catch (e: AiError) {
                        ChatMessage(
                            id = nextMessageId(),
                            fromUser = false,
                            text = "Ошибка ${e.code}\n${e.message}",
                            isError = true,
                            createdAt = System.currentTimeMillis(),
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ChatMessage(
                            id = nextMessageId(),
                            fromUser = false,
                            text = "Не удалось обратиться к модели\n" +
                                "(${e.message ?: e.javaClass.simpleName})\n\n" +
                                "Проверь интернет-соединение и ключи провайдеров (ai/Keys.kt).",
                            isError = true,
                            createdAt = System.currentTimeMillis(),
                        )
                    }
                }
                _messages.update { it + reply }
                requestJob = null

                when {
                    fromVoice -> {
                        session?.clearBusy()
                        speakAndThenResume(if (reply.isError) "Ошибка. ${reply.text}" else reply.text)
                    }
                    _assistantActive.value && !reply.isError -> speakAndThenResume(reply.text)
                    _assistantActive.value -> resumeListening()
                }
            } finally {
                _loading.value = false
            }
        }
    }

    fun cancelRequest() {
        val job = requestJob ?: return
        if (!job.isActive) return
        job.cancel()
        requestJob = null
        _loading.value = false
        addSystem("Запрос отменён")
        if (_assistantActive.value) {
            session?.clearBusy()
            resumeListening()
        }
    }

    fun onAssistantStarted(context: Context) {
        if (_assistantActive.value && session != null) return
        ensureInit(context)
        _assistantActive.value = true
        addSystem(
            "🎙 Ассистент включён. Скажите: «${_wakeWord.value} <модель> <запрос>» и закончите " +
                "словом «${_endWord.value}» — например, «${_wakeWord.value} гемини расскажи анекдот ${_endWord.value}». " +
                "Или просто помолчите 3,5 секунды после фразы — запрос уйдёт сам. " +
                "Модель можно не называть — подставится ${RequestText.DEFAULT_MODEL}. " +
                "Ответ придёт текстом и голосом; тап по строке статуса останавливает озвучку."
        )
        ttsSpeaker.init(context)
        session = VoiceSessionManager(
            context = context,
            wakeWord = { _wakeWord.value },
            endWord = { _endWord.value },
            onDictatingChanged = { _dictating.value = it },
            onHeardChanged = { _heard.value = it },
            onCommandFinalized = ::submitVoiceCommand,
            onStatusUpdate = ::pushAssistantStatus,
            onSystemMessage = ::addSystem,
        ).also { it.start() }
        beeper.start()
    }

    fun onAssistantStopped() {
        _assistantActive.value = false
        beeper.stop()
        session?.stop()
        session = null
        ttsSpeaker.shutdown()
        _speaking.value = false
        _dictating.value = false
        _heard.value = ""
        _assistantStatus.value = ""
        addSystem("Ассистент выключен")
    }

    fun stopSpeaking() {
        ttsSpeaker.stop()
    }

    internal fun submitVoiceCommand(clean: String) {
        submit(
            displayText = clean,
            requestText = RequestText.build(
                tokens = clean.split(WHITESPACE_REGEX),
                customWords = _customModelWords.value,
                serverModels = _models.value.serverModels,
                insertDefaultModel = true,
            ),
            fromVoice = true,
        )
    }

    private fun speakAndThenResume(text: String) {
        ttsSpeaker.speak(text, _ttsSkipChars.value, onDone = { resumeListening() })
    }

    private fun resumeListening() {
        val s = session
        if (s != null) s.resume() else pushAssistantStatus("Слушаю кодовое слово «${_wakeWord.value}»")
    }
}
