package com.look.chat

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.look.chat.data.Settings
import com.look.chat.data.model.ModelsState
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/**
 * Тесты фасада AssistantEngine: сохранение настроек, сборка и отправка
 * запросов на фейковый бекенд (MockWebServer), подсказки моделей,
 * жизненный цикл ассистента. Машина состояний диктовки тестируется
 * отдельно — в VoiceSessionManagerTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AssistantEngineTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        context.getSharedPreferences("look_chat", Context.MODE_PRIVATE)
            .edit().clear().commit()

        server = MockWebServer()
        server.start()

        AssistantEngine.onAssistantStopped()
        AssistantEngine._wakeWord.value = Settings.DEFAULT_WAKE_WORD
        AssistantEngine._endWord.value = Settings.DEFAULT_END_WORD
        AssistantEngine._beepIntervalSec.value = 0
        AssistantEngine._ttsSkipChars.value = Settings.DEFAULT_TTS_SKIP_CHARS
        AssistantEngine._customModelWords.value = emptyMap()
        AssistantEngine._models.value = ModelsState()
        AssistantEngine._loading.value = false
        AssistantEngine._assistantActive.value = false
        AssistantEngine._speaking.value = false
        AssistantEngine._dictating.value = false
        AssistantEngine._heard.value = ""
        AssistantEngine._serverUrl.value = server.url("/").toString()
        AssistantEngine._input.value = TextFieldValue("")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // --- помощники ---

    private fun prefs() = context.getSharedPreferences("look_chat", Context.MODE_PRIVATE)

    /** Двигает очередь главного лупера: корутины движка живут на Main. */
    private fun dispatchMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Ждёт условие, попутно прокручивая главный лупер. */
    private fun awaitTrue(attempts: Int = 200, condition: () -> Boolean): Boolean {
        val looper = shadowOf(Looper.getMainLooper())
        repeat(attempts) {
            looper.idle()
            if (condition()) return true
            Thread.sleep(25)
        }
        looper.idle()
        return condition()
    }

    private fun okProcessResponse(answer: String = "Готово!") = MockResponse().setBody(
        """{"status":"ok","model":"free-x","provider":"openrouter","answer":"$answer","elapsed_ms":42}""",
    )

    private fun modelsResponse() = MockResponse().setBody(
        """{"keywords":["старт"],
            "aliases":{"gemini":"google/gemini","фри":"openrouter/free",
                       "гигачат":"GigaChat","бесплатно":"openrouter/free",
                       "миник":"qwen/qwen3:free"},
            "providers":[{"name":"echo","models":["echo"]}]}""",
    )

    /** Адрес фейкового сервера в настройках — до ensureInit. */
    private fun seedPrefs(vararg pairs: Pair<String, String>) {
        val editor = prefs().edit()
        editor.putString("server_url", server.url("/").toString())
        pairs.forEach { (k, v) -> editor.putString(k, v) }
        editor.commit()
    }

    private fun lastMessage() = AssistantEngine.messages.value.last()

    // --- инициализация и настройки ---

    @Test
    fun `ensureInit loads persisted settings into flows`() {
        seedPrefs(
            "wake_word" to "джарвис",
            "end_word" to "",
            "tts_skip_chars" to "",
            "beep_interval_sec" to "15",
            "custom_model_words" to "джарвис=claude",
        )
        server.enqueue(modelsResponse())

        AssistantEngine.ensureInit(context)

        assertEquals(server.url("/").toString(), AssistantEngine.serverUrl.value)
        assertEquals("джарвис", AssistantEngine.wakeWord.value)
        assertEquals("", AssistantEngine.endWord.value)
        assertEquals("", AssistantEngine.ttsSkipChars.value)
        assertEquals(15, AssistantEngine.beepIntervalSec.value)
        assertEquals(mapOf("джарвис" to "claude"), AssistantEngine.customModelWords.value)
    }

    @Test
    fun `saveSettings updates flows, reports and persists`() {
        seedPrefs()
        server.enqueue(modelsResponse()) // ответ на refreshModels из ensureInit
        AssistantEngine.ensureInit(context)
        val base = AssistantEngine.messages.value.size

        AssistantEngine.saveSettings(
            url = " http://srv:1/ ",
            wake = " НОВОЕ ",
            end = "",
            skipChars = "#",
            beepSec = 0,
        )

        assertEquals("http://srv:1", AssistantEngine.serverUrl.value)
        assertEquals("новое", AssistantEngine.wakeWord.value)
        assertEquals("", AssistantEngine.endWord.value)
        assertEquals("#", AssistantEngine.ttsSkipChars.value)
        assertEquals(0, AssistantEngine.beepIntervalSec.value)

        val systemTexts = AssistantEngine.messages.value.drop(base).map { it.text }
        assertEquals(
            listOf(
                "Адрес сервера сохранён: http://srv:1",
                "Кодовое слово ассистента: «новое»",
                "Голосовой ввод отправляется после паузы.",
                "Перед озвучкой вырезаются символы: #",
                "Сигнал «я работаю» выключен.",
            ),
            systemTexts,
        )

        // всё сохранено в prefs — новый экземпляр настроек читает то же
        val stored = Settings(context)
        assertEquals("http://srv:1", stored.serverUrl)
        assertEquals("новое", stored.wakeWord)
        assertEquals("", stored.endWord)
        assertEquals("#", stored.ttsSkipChars)
        assertEquals(0, stored.beepIntervalSec)
    }

    @Test
    fun `saveSettings without changes adds no messages`() {
        seedPrefs()
        server.enqueue(modelsResponse())
        AssistantEngine.ensureInit(context)
        val base = AssistantEngine.messages.value.size

        AssistantEngine.saveSettings("   ", "старт", "стоп", "*_#~`", 300)

        assertEquals(base, AssistantEngine.messages.value.size)
    }

    @Test
    fun `wake word change pushes notification status while assistant active`() {
        seedPrefs()
        server.enqueue(modelsResponse())
        AssistantEngine.ensureInit(context)
        AssistantEngine._assistantActive.value = true

        AssistantEngine.saveSettings("http://srv:1", "джарвис", "стоп", "*_#~`", 300)

        assertEquals("джарвис", AssistantEngine.wakeWord.value)
        assertEquals("Слушаю кодовое слово «джарвис»", AssistantEngine.assistantStatus.value)
    }

    @Test
    fun `onModelPicked appends model and moves cursor to end`() {
        AssistantEngine.onModelPicked("gemini")
        assertEquals("gemini ", AssistantEngine.input.value.text)
        assertEquals(AssistantEngine.input.value.text.length, AssistantEngine.input.value.selection.end)

        AssistantEngine.onModelPicked("echo")
        assertEquals("gemini echo ", AssistantEngine.input.value.text)
        assertEquals(AssistantEngine.input.value.text.length, AssistantEngine.input.value.selection.end)
    }

    // --- чат: ручная отправка ---

    @Test
    fun `manual send with keyword sends alias request and shows reply`() {
        server.enqueue(okProcessResponse("Отличный анекдот!"))
        val base = AssistantEngine.messages.value.size

        AssistantEngine._input.value = TextFieldValue("старт гемини расскажи анекдот")
        AssistantEngine.send()
        dispatchMain()

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/process", recorded.path)
        assertEquals("""{"text":"gemini расскажи анекдот"}""", recorded.body.readUtf8())

        assertTrue(awaitTrue { !AssistantEngine.loading.value && AssistantEngine.messages.value.size == base + 2 })
        val user = AssistantEngine.messages.value[base]
        assertTrue(user.fromUser)
        assertEquals("старт гемини расскажи анекдот", user.text) // в чате — как написали

        val reply = AssistantEngine.messages.value[base + 1]
        assertFalse(reply.fromUser)
        assertFalse(reply.isError)
        assertEquals("Отличный анекдот!", reply.text)
        assertEquals("free-x", reply.model)
        assertEquals("openrouter", reply.provider)
        assertEquals(42L, reply.elapsedMs)
    }

    @Test
    fun `manual send without model inserts default model`() {
        server.enqueue(okProcessResponse())
        AssistantEngine._input.value = TextFieldValue("какая погода")
        AssistantEngine.send()
        dispatchMain()

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("""{"text":"фри какая погода"}""", recorded.body.readUtf8())
    }

    @Test
    fun `manual send honors custom model words`() {
        server.enqueue(okProcessResponse())
        AssistantEngine._customModelWords.value = mapOf("тестворд" to "claude")

        AssistantEngine._input.value = TextFieldValue("скажи тестворд анекдот")
        AssistantEngine.send()
        dispatchMain()

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("""{"text":"claude скажи анекдот"}""", recorded.body.readUtf8())
    }

    @Test
    fun `empty input is not sent`() {
        val base = AssistantEngine.messages.value.size
        AssistantEngine._input.value = TextFieldValue("   ")
        AssistantEngine.send()

        assertEquals(base, AssistantEngine.messages.value.size)
        assertFalse(AssistantEngine.loading.value)
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `second send while loading is ignored`() {
        server.enqueue(okProcessResponse().setBodyDelay(300, TimeUnit.MILLISECONDS))
        val base = AssistantEngine.messages.value.size

        AssistantEngine._input.value = TextFieldValue("первый")
        AssistantEngine.send()
        dispatchMain()
        assertTrue(awaitTrue { server.requestCount == 1 })

        AssistantEngine._input.value = TextFieldValue("второй")
        AssistantEngine.send()

        assertEquals("второй", AssistantEngine.input.value.text) // не очищен
        assertTrue(awaitTrue { !AssistantEngine.loading.value && AssistantEngine.messages.value.size == base + 2 })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `backend error envelope becomes red message`() {
        server.enqueue(
            MockResponse().setBody(
                """{"status":"error","error":{"code":"KEYWORD_NOT_FOUND","message":"Модель не найдена"}}""",
            ),
        )
        val base = AssistantEngine.messages.value.size

        AssistantEngine._input.value = TextFieldValue("старт привет")
        AssistantEngine.send()

        assertTrue(awaitTrue { !AssistantEngine.loading.value && AssistantEngine.messages.value.size == base + 2 })
        val reply = AssistantEngine.messages.value[base + 1]
        assertTrue(reply.isError)
        assertEquals("Ошибка KEYWORD_NOT_FOUND\nМодель не найдена", reply.text)
    }

    @Test
    fun `unreachable server becomes helpful error message`() {
        server.shutdown()
        val base = AssistantEngine.messages.value.size

        AssistantEngine._input.value = TextFieldValue("привет")
        AssistantEngine.send()

        assertTrue(awaitTrue { AssistantEngine.messages.value.size == base + 2 })
        val reply = AssistantEngine.messages.value[base + 1]
        assertTrue(reply.isError)
        assertTrue(reply.text.startsWith("Не удалось связаться с сервером"))
    }

    @Test
    fun `non-json success reply becomes error message`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("не json"))
        val base = AssistantEngine.messages.value.size

        AssistantEngine._input.value = TextFieldValue("привет")
        AssistantEngine.send()

        assertTrue(awaitTrue { AssistantEngine.messages.value.size == base + 2 })
        val reply = AssistantEngine.messages.value[base + 1]
        assertTrue(reply.isError)
        assertTrue(reply.text.startsWith("Не удалось связаться с сервером"))
    }

    @Test
    fun `manual send with assistant active pushes statuses and no voice`() {
        AssistantEngine._assistantActive.value = true
        server.enqueue(okProcessResponse())
        val base = AssistantEngine.messages.value.size

        AssistantEngine._input.value = TextFieldValue("привет")
        AssistantEngine.send()

        // статус «Отправляю запрос…» ставится синхронно, до ухода в сеть
        assertEquals("Отправляю запрос…", AssistantEngine.assistantStatus.value)

        assertTrue(awaitTrue { !AssistantEngine.loading.value && AssistantEngine.messages.value.size == base + 2 })
        assertEquals("Слушаю кодовое слово «старт»", AssistantEngine.assistantStatus.value)
        assertFalse(AssistantEngine.speaking.value) // TTS не инициализирован — только текст
    }

    // --- голосовые команды (стык с VoiceSessionManager) ---

    @Test
    fun `voice command is sent with default model and marked in chat`() {
        server.enqueue(okProcessResponse("Ответ на голос!"))
        val base = AssistantEngine.messages.value.size

        AssistantEngine.submitVoiceCommand("привет")
        dispatchMain()

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("""{"text":"фри привет"}""", recorded.body.readUtf8())

        assertTrue(awaitTrue { !AssistantEngine.loading.value && AssistantEngine.messages.value.size == base + 2 })
        val user = AssistantEngine.messages.value[base]
        assertTrue(user.fromUser)
        assertTrue(user.voice)
        assertEquals("привет", user.text)

        val reply = AssistantEngine.messages.value[base + 1]
        assertEquals("Ответ на голос!", reply.text)
        assertFalse(AssistantEngine.speaking.value) // TTS нет — только текст
    }

    // --- модели для подсказок ---

    @Test
    fun `refreshModels fills suggestions, keywords and server models`() {
        server.enqueue(modelsResponse())

        AssistantEngine.refreshModels()

        assertTrue(awaitTrue { AssistantEngine.models.value.suggestions.isNotEmpty() })
        val models = AssistantEngine.models.value
        assertEquals(listOf("gemini", "фри", "гигачат", "миник"), models.suggestions)
        assertEquals(listOf("старт"), models.keywords)
        assertTrue("echo" in models.serverModels)
        assertTrue("гигачат" in models.serverModels)
    }

    @Test
    fun `refreshModels with unreachable server keeps empty state`() {
        server.shutdown()
        AssistantEngine.refreshModels()
        // просто не падает: состояние остаётся пустым
        dispatchMain()
        assertTrue(awaitTrue { AssistantEngine.models.value.keywords.isEmpty() })
        assertEquals(emptyList<String>(), AssistantEngine.models.value.suggestions)
    }

    // --- остановка и озвучка ---

    @Test
    fun `assistant stopped resets state and reports`() {
        AssistantEngine._assistantActive.value = true
        AssistantEngine._dictating.value = true
        AssistantEngine._speaking.value = true
        AssistantEngine._heard.value = "старт х"
        val base = AssistantEngine.messages.value.size

        AssistantEngine.onAssistantStopped()

        assertFalse(AssistantEngine.assistantActive.value)
        assertFalse(AssistantEngine.dictating.value)
        assertFalse(AssistantEngine.speaking.value)
        assertEquals("", AssistantEngine.heard.value)
        assertEquals("Ассистент выключен", AssistantEngine.messages.value[base].text)
    }

    @Test
    fun `stopSpeaking is a no-op when nothing is spoken`() {
        AssistantEngine.stopSpeaking()
        assertFalse(AssistantEngine.speaking.value)
    }

    @Test
    fun `onMicPermissionDenied adds hint message`() {
        val base = AssistantEngine.messages.value.size
        AssistantEngine.onMicPermissionDenied()
        assertTrue(AssistantEngine.messages.value[base].text.contains("микрофон"))
    }
}
