package com.look.chat

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.look.chat.data.LookApi
import com.look.chat.data.ModelsResponse
import com.look.chat.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

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

/** Что показывать в строке подсказок над полем ввода и в боковом меню. */
data class ModelsState(
    val keywords: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    /** Полный список моделей с сервера — для сопоставления в запросах. */
    val serverModels: List<String> = emptyList(),
)

/**
 * Общий движок приложения: чат, голосовой ассистент (офлайн Vosk-слушание
 * кодового слова), запросы к бекенду и озвучка ответов.
 *
 * Живёт вне активити: им пользуется и UI, и AssistantService, поэтому
 * состояние — синглтон с StateFlow.
 */
object AssistantEngine {
    private const val TAG = "LookAssistant"

    // Модель по умолчанию, если в команде модель не названа.
    private const val DEFAULT_VOICE_MODEL = "фри"

    // Главные модели — всегда первыми в подсказках и боковом меню;
    // дальше к ним добавляются все бесплатные (см. visibleSuggestions).
    private val VISIBLE_MODELS = listOf("deepseek", "gemini", "фри")

    // Тишина после последней речи, после которой диктовка уходит сама
    // (слово «стоп» отправляет сразу, не дожидаясь тишины).
    private const val SILENCE_SEND_MS = 3500L

    /**
     * Клиентские псевдонимы: русские названия моделей, которые бекенд сам
     * не знает. «фри»/«deepseek» и прочее настроено алиасами прямо на
     * бекенде (LOOK_MODEL_ALIASES) — туда слово уходит как есть.
     */
    private val MODEL_ALIASES = mapOf(
        "гемини" to "gemini",
        "джемини" to "gemini",
        "геминис" to "gemini",
        "дипсик" to "deepseek",
        "депсик" to "deepseek",
        // Эти слова уходят на бекенд как есть: там свои алиасы
        // («фри» → openrouter/free, «deepseek» → deepseek-v4-flash).
        "фри" to "фри",
        "free" to "free",
        "deepseek" to "deepseek",
    )

    /** Псевдонимы из двух слов — так распознавание слышит «deepseek». */
    private val PHRASE_ALIASES = mapOf(
        "дип сик" to "deepseek",
        "дип сикс" to "deepseek",
        "дип сок" to "deepseek",
        "деп сик" to "deepseek",
        "депп сик" to "deepseek",
        "деп сок" to "deepseek",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var settings: Settings? = null
    private val api = LookApi()
    private var nextId = 1L
    private fun nextMessageId() = nextId++

    // --- состояние чата ---
    private val _messages = MutableStateFlow(listOf(welcomeMessage()))
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    val input = MutableStateFlow(TextFieldValue(""))
    val loading = MutableStateFlow(false)
    val serverUrl = MutableStateFlow("")
    val models = MutableStateFlow(ModelsState())

    // --- состояние ассистента ---
    val assistantActive = MutableStateFlow(false)
    val speaking = MutableStateFlow(false)

    /** Кодовое слово, на которое просыпается микрофон (меняется в настройках). */
    val wakeWord = MutableStateFlow(Settings.DEFAULT_WAKE_WORD)

    /** Слово, завершающее голосовой ввод («стоп»); пусто = отправлять после паузы. */
    val endWord = MutableStateFlow(Settings.DEFAULT_END_WORD)

    /** Символы, которые вырезаются из ответа перед озвучкой. */
    val ttsSkipChars = MutableStateFlow(Settings.DEFAULT_TTS_SKIP_CHARS)

    /** Свои слова для моделей: слово (или два слова через пробел) → модель. */
    val customModelWords = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Идёт диктовка: кодовое слово сказано, ждём слово окончания. */
    val dictating = MutableStateFlow(false)

    /** Живой текст с микрофона — показывается в строке статуса. */
    val heard = MutableStateFlow("")

    /** Сервис подписывается на статусы ассистента — обновляет уведомление. */
    var assistantStatusListener: ((String) -> Unit)? = null

    private var mic: MicListener? = null

    // --- состояние диктовки ---
    private var voiceArmed = false
    private val voiceBuffer = StringBuilder()
    private var currentPartial = ""

    // Частичный результат, по которому сработало кодовое слово: финальный
    // результат той же фразы заменяет его содержимое (иначе текст задвоится).
    private var pendingSegmentPartial: String? = null

    private var lastVoiceActivityAt = 0L
    private var lastFinalizeAt = 0L
    private var voiceBusy = false

    private var tts: android.speech.tts.TextToSpeech? = null
    private var ttsReady = false
    private var speakDoneCallback: (() -> Unit)? = null

    private fun welcomeMessage() = ChatMessage(
        id = 0,
        fromUser = false,
        text = "Привет! Тут два режима.\n\n" +
            "Руками: пишите просто вопрос — модель подставится сама " +
            "(по умолчанию фри). Можно и по-старому: «лук <модель> <запрос>».\n\n" +
            "Голосом: нажмите 🎙 в шапке, скажите кодовое слово (по умолчанию «лук»), " +
            "наговорите запрос и завершите словом «стоп» — или просто помолчите " +
            "5 секунд, запрос уйдёт сам. Всё настраивается в ⚙.\n\n" +
            "Во время озвучки тап по строке статуса останавливает голос.\n\n" +
            "Долгое нажатие на сообщение — копирование.",
    )

    private fun addSystem(text: String) {
        _messages.value += ChatMessage(id = nextMessageId(), fromUser = false, text = text)
    }

    /** Пушит статус в уведомление сервиса (если оно показано). */
    private fun pushAssistantStatus(text: String) {
        assistantStatusListener?.invoke(text)
    }

    /** Инициализация контекстом; безопасно вызывать сколько угодно раз. */
    fun ensureInit(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val s = Settings(appContext!!)
        settings = s
        serverUrl.value = s.serverUrl
        wakeWord.value = s.wakeWord
        endWord.value = s.endWord
        ttsSkipChars.value = s.ttsSkipChars
        customModelWords.value = s.customModelWords
        refreshModels()
    }

    // --- чат: ручной ввод ---

    fun onInputChange(value: TextFieldValue) {
        input.value = value
    }

    /** Подставляет выбранную из подсказок модель и ставит курсор после неё. */
    fun onModelPicked(model: String) {
        val current = input.value
        val base = current.text.trimEnd()
        val newText = if (base.isEmpty()) "$model " else "$base $model "
        input.value = TextFieldValue(text = newText, selection = TextRange(newText.length))
    }

    /** Сохраняет адрес сервера, кодовые слова и символы озвучки из настроек. */
    fun saveSettings(url: String, wake: String, end: String, skipChars: String) {
        val cleanedUrl = url.trim()
        if (cleanedUrl.isNotEmpty()) {
            settings?.serverUrl = cleanedUrl
            serverUrl.value = settings?.serverUrl ?: cleanedUrl
            addSystem("Адрес сервера сохранён: ${serverUrl.value}")
            refreshModels()
        }

        val cleanedWake = wake.trim().lowercase()
        if (cleanedWake.isNotEmpty() && cleanedWake != wakeWord.value) {
            settings?.wakeWord = cleanedWake
            wakeWord.value = cleanedWake
            addSystem("Кодовое слово ассистента: «$cleanedWake»")
            if (assistantActive.value) {
                pushAssistantStatus("Слушаю кодовое слово «$cleanedWake»")
            }
        }

        val cleanedEnd = end.trim().lowercase()
        if (cleanedEnd != endWord.value) {
            settings?.endWord = cleanedEnd
            endWord.value = cleanedEnd
            addSystem(
                if (cleanedEnd.isEmpty()) "Голосовой ввод отправляется после паузы."
                else "Голосовой ввод завершается словом: «$cleanedEnd»"
            )
        }

        if (skipChars != ttsSkipChars.value) {
            settings?.ttsSkipChars = skipChars
            ttsSkipChars.value = skipChars
            addSystem(
                if (skipChars.isEmpty()) "Все символы озвучиваются как есть."
                else "Перед озвучкой вырезаются символы: $skipChars"
            )
        }
    }

    /** Добавляет/обновляет своё слово для модели (боковое меню). */
    fun saveCustomWord(word: String, model: String) {
        val w = word.trim().lowercase()
        val m = model.trim()
        if (w.isEmpty() || m.isEmpty()) return
        val updated = customModelWords.value + (w to m)
        settings?.customModelWords = updated
        customModelWords.value = updated
        addSystem("Слово «$w» теперь означает модель $m")
    }

    /** Удаляет своё слово для модели. */
    fun removeCustomWord(word: String) {
        val updated = customModelWords.value - word
        settings?.customModelWords = updated
        customModelWords.value = updated
        addSystem("Слово «$word» удалено")
    }

    /** Тихо подтягивает список моделей для подсказок; без сервера просто пусто. */
    fun refreshModels() {
        scope.launch {
            models.value = try {
                val response: ModelsResponse = api.models(serverUrl.value)
                val fromServer = (
                    response.aliases.keys +
                        response.providers.flatMap { it.models } +
                        response.providers.map { it.name }
                    ).distinct()
                ModelsState(
                    keywords = response.keywords,
                    suggestions = visibleSuggestions(response.aliases),
                    serverModels = fromServer,
                )
            } catch (e: Exception) {
                ModelsState()
            }
        }
    }

    /**
     * Список для подсказок и меню: закреплённые модели плюс все
     * бесплатные — алиасы бекенда, ведущие на :free-модели OpenRouter
     * или на роутер openrouter/free. Если у модели есть и латинское,
     * и русское короткое имя — показываем только латинское.
     */
    private fun visibleSuggestions(aliases: Map<String, String>): List<String> {
        val pinned = VISIBLE_MODELS.filter { p ->
            aliases.keys.any { it.lowercase() == p.lowercase() }
        }
        val pinnedTargets = pinned.mapNotNull { p ->
            aliases.entries.firstOrNull { it.key.lowercase() == p.lowercase() }?.value
        }.toSet()

        val freeByTarget = mutableMapOf<String, String>()
        aliases.forEach { (alias, target) ->
            val free = target.endsWith(":free") || target.substringAfterLast('/') == "free"
            if (!free || target in pinnedTargets) return@forEach
            val current = freeByTarget[target]
            if (current == null || (isCyrillic(current) && !isCyrillic(alias))) {
                freeByTarget[target] = alias
            }
        }
        return pinned + freeByTarget.values.sorted()
    }

    private fun isCyrillic(text: String): Boolean =
        text.any { it.code in 0x0410..0x04FF }

    /**
     * Отправка сообщения, набранного руками. Стартовое слово («лук»)
     * не обязательно и в чате показывается как есть: на бекенд кодовое
     * слово не уходит — там теперь формат «<модель> <запрос>».
     */
    fun send() {
        val text = input.value.text.trim()
        if (text.isEmpty() || loading.value) return
        input.value = TextFieldValue("")

        // Если пользователь по привычке начал с «лук»/«look» — срезаем
        // это слово из запроса, в чате оставляем как написал.
        val tokens = text.split(Regex("\\s+"))
        val keywordIdx = tokens.indexOfFirst {
            it.lowercase().trim(',', '.', '!', '?', ';', ':') in backendKeywords()
        }
        val rest = if (keywordIdx >= 0) tokens.drop(keywordIdx + 1) else tokens
        submit(text, buildRequest(rest, insertDefaultModel = true), fromVoice = false)
    }

    fun onMicPermissionDenied() {
        addSystem("Без разрешения на микрофон голосовой ассистент не работает. " +
            "Нажмите 🎙 и разрешите доступ.")
    }

    // --- общая отправка на бекенд ---

    /** displayText — что показать в чате, requestText — что уйдёт на бекенд. */
    private fun submit(displayText: String, requestText: String, fromVoice: Boolean) {
        Log.d(TAG, "submit display=$displayText request=$requestText")
        _messages.value += ChatMessage(id = nextMessageId(), fromUser = true, text = displayText, voice = fromVoice)
        loading.value = true
        if (assistantActive.value) {
            // Пока запрос в полёте — микрофон приостановлен, диктовка сброшена.
            mic?.suspendMic()
            voiceArmed = false
            dictating.value = false
            voiceBuffer.clear()
            currentPartial = ""
            heard.value = ""
            pushAssistantStatus("Отправляю запрос…")
        }

        scope.launch {
            val reply = try {
                val response = api.process(serverUrl.value, requestText)
                when {
                    response.status == "ok" && response.answer != null -> ChatMessage(
                        id = nextMessageId(),
                        fromUser = false,
                        text = response.answer,
                        model = response.model,
                        provider = response.provider,
                        elapsedMs = response.elapsedMs,
                    )
                    response.error != null -> ChatMessage(
                        id = nextMessageId(),
                        fromUser = false,
                        text = "Ошибка ${response.error.code}\n${response.error.message}",
                        isError = true,
                    )
                    else -> ChatMessage(
                        id = nextMessageId(),
                        fromUser = false,
                        text = "Сервер вернул неожиданный ответ",
                        isError = true,
                    )
                }
            } catch (e: Exception) {
                ChatMessage(
                    id = nextMessageId(),
                    fromUser = false,
                    text = "Не удалось связаться с сервером ${serverUrl.value}\n" +
                        "(${e.message ?: e.javaClass.simpleName})\n\n" +
                        "Проверь, что бекенд запущен, и адрес в настройках (⚙).",
                    isError = true,
                )
            }
            _messages.value += reply
            loading.value = false

            when {
                // Голосовая команда: озвучиваем ответ, потом снова слушаем.
                fromVoice -> {
                    voiceBusy = false
                    speakAndThenResume(if (reply.isError) "Ошибка. ${reply.text}" else reply.text)
                }
                // Ручной запрос при включённом ассистенте тоже озвучиваем.
                assistantActive.value && !reply.isError -> speakAndThenResume(reply.text)
                // Ручной запрос с ошибкой: просто возвращаем микрофон.
                assistantActive.value -> {
                    mic?.resumeMic()
                    pushAssistantStatus("Слушаю кодовое слово «${wakeWord.value}»")
                }
            }
        }
    }

    // --- голосовой ассистент ---

    /** Вызывается из AssistantService, когда сервис поднят. */
    fun onAssistantStarted(context: Context) {
        ensureInit(context)
        assistantActive.value = true
        addSystem(
            "🎙 Ассистент включён. Скажите: «${wakeWord.value} <модель> <запрос>» и закончите " +
                "словом «${endWord.value}» — например, «${wakeWord.value} гемини расскажи анекдот ${endWord.value}». " +
                "Или просто помолчите 5 секунд после фразы — запрос уйдёт сам. " +
                "Модель можно не называть — подставится $DEFAULT_VOICE_MODEL. " +
                "Ответ придёт текстом и голосом; тап по строке статуса останавливает озвучку."
        )
        initTts()
        startMic()
        postSilenceCheck()
    }

    /** Вызывается при остановке сервиса. */
    fun onAssistantStopped() {
        voiceBusy = false
        voiceArmed = false
        voiceBuffer.clear()
        currentPartial = ""
        pendingSegmentPartial = null
        dictating.value = false
        assistantActive.value = false
        heard.value = ""
        main.removeCallbacksAndMessages(null)
        mic?.stop()
        mic = null
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "tts shutdown", e)
        }
        tts = null
        ttsReady = false
        speakDoneCallback = null
        addSystem("Ассистент выключен")
    }

    /** Останавливает текущую озвучку (тап по строке статуса). */
    fun stopSpeaking() {
        if (!speaking.value) return
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "tts stop", e)
        }
        // Обычно после stop() прилетает колбэк onStop; подстраховаться.
        main.postDelayed({ if (speaking.value) finishSpeaking() }, 500)
    }

    private fun startMic() {
        val ctx = appContext ?: return
        mic = MicListener(
            context = ctx,
            onPartial = { partial -> onMicPartial(partial) },
            onFinal = { text -> onMicFinal(text) },
            onReady = {
                pushAssistantStatus("Слушаю кодовое слово «${wakeWord.value}»")
            },
            onError = { message ->
                addSystem("Микрофон недоступен: $message")
            },
        )
        mic?.start()
    }

    /** Частичный результат Vosk (текущая недоговорённая фраза). */
    private fun onMicPartial(partial: String) {
        if (voiceBusy) return
        lastVoiceActivityAt = System.currentTimeMillis()

        if (!voiceArmed) {
            // Кодовое слово ловим уже на частичном тексте — без ожидания паузы.
            val tokens = partial.trim().split(Regex("\\s+"))
            val wordIdx = tokens.indexOfFirst { isWakeToken(it) }
            if (wordIdx < 0) {
                heard.value = partial
                return
            }
            voiceArmed = true
            dictating.value = true
            voiceBuffer.clear()
            voiceBuffer.append(tokens.drop(wordIdx + 1).joinToString(" "))
            pendingSegmentPartial = partial.trim()
            heard.value = voiceBuffer.toString().trim()
            pushAssistantStatus(dictatingStatus())
            return
        }

        currentPartial = partial.trim()
        val full = dictationText()
        heard.value = full

        val end = endWord.value
        if (end.isNotEmpty() && heardContainsEnd(full, end)) {
            finalizeVoice(textBeforeEnd(full, end))
        }
    }

    /** Финальный результат Vosk — авторитетная версия текущей фразы. */
    private fun onMicFinal(final: String) {
        if (voiceBusy) return
        lastVoiceActivityAt = System.currentTimeMillis()
        Log.d(TAG, "mic final: $final")

        if (!voiceArmed) {
            val tokens = final.trim().split(Regex("\\s+"))
            val wordIdx = tokens.indexOfFirst { isWakeToken(it) }
            if (wordIdx < 0) return

            voiceArmed = true
            dictating.value = true
            voiceBuffer.clear()
            voiceBuffer.append(tokens.drop(wordIdx + 1).joinToString(" "))
            pushAssistantStatus(dictatingStatus())
            checkEndOrArmDone()
            return
        }

        // Финал заменяет частичный текст той же фразы — иначе задвоится.
        if (pendingSegmentPartial != null) {
            voiceBuffer.clear()
            voiceBuffer.append(afterWake(final.trim()))
            pendingSegmentPartial = null
        } else {
            voiceBuffer.append(' ').append(final.trim())
        }
        currentPartial = ""
        checkEndOrArmDone()
    }

    /** После обновления диктовки: «стоп» — отправить, иначе ждём тишину (5 с). */
    private fun checkEndOrArmDone() {
        heard.value = dictationText()
        val end = endWord.value
        if (end.isNotEmpty()) {
            val full = dictationText()
            if (heardContainsEnd(full, end)) {
                finalizeVoice(textBeforeEnd(full, end))
            }
            // Иначе ждём: слово окончания или 5 секунд тишины.
        }
        // Пустое слово окончания: отправку сделает таймер тишины (5 с) —
        // короткие паузы в речи запрос не разрывают.
    }

    private fun dictationText(): String {
        val buffered = voiceBuffer.toString().trim()
        val current = currentPartial.trim()
        return if (current.isEmpty()) buffered else "$buffered $current".trim()
    }

    private fun dictatingStatus(): String =
        if (endWord.value.isEmpty()) "Записываю… пауза отправит запрос"
        else "Записываю… закончите словом «${endWord.value}»"

    private fun afterWake(text: String): String {
        val tokens = text.trim().split(Regex("\\s+"))
        val idx = tokens.indexOfFirst { isWakeToken(it) }
        return if (idx >= 0) tokens.drop(idx + 1).joinToString(" ") else text
    }

    /** Кодовое слово прощает небольшие описки распознавания («лука» → «лук»). */
    private fun isWakeToken(token: String): Boolean {
        val t = normalizeWord(token)
        val w = normalizeWord(wakeWord.value)
        return t == w || (w.length >= 3 && t.startsWith(w) && t.length <= w.length + 2)
    }

    private fun heardContainsEnd(text: String, end: String): Boolean =
        text.lowercase().replace('ё', 'е').split(Regex("[\\s,.!?;:]+")).any { isEndToken(it, end) }

    private fun isEndToken(token: String, end: String): Boolean {
        val t = normalizeWord(token)
        val e = normalizeWord(end)
        return t == e || (e.length >= 3 && t.startsWith(e) && t.length <= e.length + 1)
    }

    private fun textBeforeEnd(text: String, end: String): String {
        val tokens = text.trim().split(Regex("\\s+"))
        val idx = tokens.indexOfFirst { isEndToken(it, end) }
        return if (idx >= 0) tokens.take(idx).joinToString(" ") else text
    }

    /** Раз в секунду: диктовка молчит 5 секунд — отправляем накопленное. */
    private fun postSilenceCheck() {
        main.postDelayed({
            if (assistantActive.value) {
                checkSilenceSend()
                postSilenceCheck()
            }
        }, 1000)
    }

    private fun checkSilenceSend() {
        if (!voiceArmed || voiceBusy) return
        val idle = System.currentTimeMillis() - lastVoiceActivityAt
        if (idle < SILENCE_SEND_MS) return

        val text = textBeforeEnd(dictationText(), endWord.value).trim()
        if (text.isBlank()) {
            // Сказали только кодовое слово и молчим — сбрасываем диктовку.
            voiceArmed = false
            dictating.value = false
            voiceBuffer.clear()
            currentPartial = ""
            pendingSegmentPartial = null
            heard.value = ""
            addSystem("После «${wakeWord.value}» не было запроса — слушаю дальше.")
            pushAssistantStatus("Слушаю кодовое слово «${wakeWord.value}»")
            return
        }
        Log.d(TAG, "silence send: $text")
        finalizeVoice(text)
    }

    private fun finalizeVoice(text: String) {
        voiceArmed = false
        dictating.value = false
        currentPartial = ""
        pendingSegmentPartial = null
        heard.value = ""
        lastFinalizeAt = System.currentTimeMillis()

        if (text.isBlank()) {
            addSystem("После «${wakeWord.value}» не было запроса — слушаю дальше.")
            pushAssistantStatus("Слушаю кодовое слово «${wakeWord.value}»")
            return
        }
        voiceBusy = true
        mic?.suspendMic()
        submit(
            displayText = text,
            requestText = buildRequest(
                text.trim().split(Regex("\\s+")),
                insertDefaultModel = true,
            ),
            fromVoice = true,
        )
    }

    /** Ключевые слова бекенда (из GET /api/v1/models), пока сервер не ответил — лук/look. */
    private fun backendKeywords(): Set<String> {
        val fromServer = models.value.keywords.map { it.lowercase() }.toSet()
        return if (fromServer.isEmpty()) setOf("лук", "look") else fromServer
    }

    /**
     * Собирает текст запроса для бекенда: модель (с псевдонимами, иначе
     * модель по умолчанию) + сам запрос. Кодовое слово здесь не участвует:
     * бекенд теперь ждёт просто «<модель> <запрос>».
     */
    private fun buildRequest(tokens: List<String>, insertDefaultModel: Boolean): String {
        if (tokens.isEmpty()) return ""
        val normTokens = tokens.map { normalizeWord(it) }

        // Свои слова из бокового меню ищем в ЛЮБОМ месте фразы: голосом
        // слово может встать не первым. Найденное слово ВЫРЕЗАЕТСЯ из
        // запроса, а модель встаёт ПЕРВЫМ словом — так ждёт бекенд.
        // Сравнение — точное, через транслит (голосом «testword» слышится
        // как «тестворд») и по опечаткам.
        customModelWords.value.forEach { (word, model) ->
            val wordTokens = tokensOf(word)
            if (wordTokens.isEmpty() || wordTokens.size > normTokens.size) return@forEach
            for (start in 0..normTokens.size - wordTokens.size) {
                val segment = normTokens.subList(start, start + wordTokens.size)
                val matched = wordTokens.indices.all { k ->
                    matchesWord(segment[k], wordTokens[k])
                }
                if (matched) {
                    val restTokens = tokens.take(start) + tokens.drop(start + wordTokens.size)
                    return (listOf(model) + restTokens).joinToString(" ")
                }
            }
        }

        val lower = normTokens

        // Фразовые псевдонимы из двух слов: «дип сик», «депп сик» → deepseek.
        if (lower.size >= 2) {
            PHRASE_ALIASES["${lower[0]} ${lower[1]}"]?.let { phraseModel ->
                return (listOf(phraseModel) + tokens.drop(2)).joinToString(" ")
            }
        }

        // Одиночные псевдонимы; «фри»/«free»/«deepseek» проходят на бекенд
        // как есть — там для них свои алиасы.
        MODEL_ALIASES[lower[0]]?.let { alias ->
            return (listOf(alias) + tokens.drop(1)).joinToString(" ")
        }

        // Точно известная модель (весь список с сервера, включая echo).
        if (models.value.serverModels.any { normalizeWord(it) == lower[0] }) {
            return tokens.joinToString(" ")
        }

        // Модель не названа — подставляем модель по умолчанию.
        if (insertDefaultModel) {
            return (listOf(DEFAULT_VOICE_MODEL) + tokens).joinToString(" ")
        }
        return tokens.joinToString(" ")
    }

    /** Нормализация слова: нижний регистр, ё→е, без знаков препинания. */
    private fun normalizeWord(word: String): String =
        word.lowercase().replace('ё', 'е').trim(',', '.', '!', '?', ';', ':', ' ')

    /** Разбивает настроенное слово (возможно, из двух слов) на токены. */
    private fun tokensOf(word: String): List<String> =
        normalizeWord(word).split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** Таблица фонетической транслитерации латиницы в кириллицу:
     *  голосом «testword» распознаётся как «тестворд» — сравниваем в одном
     *  алфавите, иначе свои слова не работали бы через микрофон. */
    private val LATIN_TO_RU = mapOf(
        'a' to "а", 'b' to "б", 'c' to "к", 'd' to "д", 'e' to "е", 'f' to "ф",
        'g' to "г", 'h' to "х", 'i' to "и", 'j' to "й", 'k' to "к", 'l' to "л",
        'm' to "м", 'n' to "н", 'o' to "о", 'p' to "п", 'q' to "к", 'r' to "р",
        's' to "с", 't' to "т", 'u' to "у", 'v' to "в", 'w' to "в", 'x' to "кс",
        'y' to "и", 'z' to "з",
    )

    private fun translitRu(text: String): String = buildString {
        for (ch in text.lowercase()) append(LATIN_TO_RU[ch] ?: ch)
    }

    /** Сравнение произнесённого слова с настроенным: точно, через транслит
     *  или с опечатками (для слов длиной от 5 букв). */
    private fun matchesWord(spoken: String, configured: String): Boolean {
        val a = spoken.trim().trim(',', '.', '!', '?', ';', ':')
        val b = configured.trim().lowercase()
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val ta = translitRu(a)
        val tb = translitRu(b)
        if (ta == tb) return true
        if (b.length >= 5 && levenshtein(ta, tb) <= 2) return true
        return false
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in a.indices) {
            curr[0] = i + 1
            for (j in b.indices) {
                curr[j + 1] = minOf(
                    prev[j + 1] + 1,
                    curr[j] + 1,
                    prev[j] + if (a[i] == b[j]) 0 else 1,
                )
            }
            prev = curr.copyOf()
        }
        return prev[b.length]
    }

    // --- озвучка ответов ---

    private fun speakAndThenResume(text: String) {
        speak(text) {
            mic?.resumeMic()
            pushAssistantStatus("Слушаю кодовое слово «${wakeWord.value}»")
        }
    }

    private fun speak(text: String, onDone: () -> Unit) {
        if (appContext == null || !assistantActive.value || !ttsReady) {
            onDone()
            return
        }
        speaking.value = true
        speakDoneCallback = onDone
        if (assistantActive.value) {
            pushAssistantStatus("Отвечаю голосом…")
        }
        tts?.speak(
            prepareForSpeech(text).take(1200),
            android.speech.tts.TextToSpeech.QUEUE_FLUSH,
            android.os.Bundle(),
            "look_${System.currentTimeMillis()}",
        )
    }

    /** Вырезает символы из «не озвучивать» и приглаживает пробелы. */
    private fun prepareForSpeech(text: String): String {
        val chars = ttsSkipChars.value
        val filtered = if (chars.isEmpty()) text else text.filter { !chars.contains(it) }
        return filtered.replace(Regex("\\s+"), " ").trim()
    }

    private fun initTts() {
        val ctx = appContext ?: return
        if (tts != null) return
        tts = android.speech.tts.TextToSpeech(ctx) { status ->
            main.post {
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale("ru", "RU"))
                        ?: android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
                    ttsReady = result != android.speech.tts.TextToSpeech.LANG_MISSING_DATA &&
                        result != android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
                    Log.d(TAG, "TTS ready=$ttsReady (language result=$result)")
                    if (!ttsReady) {
                        addSystem("Русский голос в системе не найден — ответы будут только текстом.")
                    }
                } else {
                    Log.w(TAG, "TTS init failed: $status")
                    addSystem("Синтез речи недоступен — ответы будут только текстом.")
                }
            }
        }.also { engine ->
            engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    main.post { finishSpeaking() }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    main.post { finishSpeaking() }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    main.post { finishSpeaking() }
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    main.post { finishSpeaking() }
                }
            })
        }
    }

    private fun finishSpeaking() {
        if (!speaking.value) return
        speaking.value = false
        val callback = speakDoneCallback
        speakDoneCallback = null
        callback?.invoke()
    }
}
