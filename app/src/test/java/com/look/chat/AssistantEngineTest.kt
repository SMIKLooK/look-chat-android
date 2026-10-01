package com.look.chat

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.look.chat.ai.AiError
import com.look.chat.ai.AiProvider
import com.look.chat.ai.AiRequest
import com.look.chat.ai.AiResponse
import com.look.chat.ai.LocalBackend
import com.look.chat.ai.ProviderRegistry
import com.look.chat.data.Settings
import com.look.chat.data.model.ModelsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AssistantEngineTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.getSharedPreferences("look_chat", Context.MODE_PRIVATE)
            .edit().clear().commit()

        AssistantEngine.onAssistantStopped()
        AssistantEngine.backend = defaultStubBackend()
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
        AssistantEngine._input.value = TextFieldValue("")
    }


    private fun prefs() = context.getSharedPreferences("look_chat", Context.MODE_PRIVATE)

    private fun dispatchMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

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

    private class FakeProvider(
        override val name: String = "echo",
        override val models: List<String> = listOf("echo"),
        private val reply: suspend (AiRequest) -> String = { request ->
            "Эхо(${request.model}): ${request.messages.single().content}"
        },
    ) : AiProvider {
        var calls: Int = 0
            private set

        override fun supports(model: String): Boolean = model in models

        override suspend fun complete(request: AiRequest): AiResponse {
            calls++
            return AiResponse(model = request.model, provider = name, content = reply(request))
        }
    }

    private fun defaultStubBackend(
        providers: List<AiProvider>? = null,
        aliases: Map<String, String> = mapOf(
            "gemini" to "echo",
            "фри" to "echo",
            "миник" to "нет-такой",
        ),
    ): LocalBackend {
        val list = providers ?: listOf(
            FakeProvider(
                models = listOf(
                    "echo", "claude", "google/gemini",
                    "openrouter/free", "qwen/qwen3:free",
                ),
            ),
        )
        val registry = ProviderRegistry(list)
        registry.setAliases(aliases)
        return LocalBackend(registry)
    }

    private fun lastMessage() = AssistantEngine.messages.value.last()


    @Test
    fun `ensureInit loads persisted settings into flows`() {
        prefs().edit()
            .putString("wake_word", "джарвис")
            .putString("end_word", "")
            .putString("tts_skip_chars", "")
            .putString("beep_interval_sec", "15")
            .putString("custom_model_words", "джарвис=claude")
            .commit()

        AssistantEngine.ensureInit(context)

        assertEquals("джарвис", AssistantEngine.wakeWord.value)
        assertEquals("", AssistantEngine.endWord.value)
        assertEquals("", AssistantEngine.ttsSkipChars.value)
        assertEquals(15, AssistantEngine.beepIntervalSec.value)
        assertEquals(mapOf("джарвис" to "claude"), AssistantEngine.customModelWords.value)
    }

    @Test
    fun `saveSettings updates flows, reports and persists`() {
        AssistantEngine.ensureInit(context)
        val base = AssistantEngine.messages.value.size

        AssistantEngine.saveSettings(
            wake = " НОВОЕ ",
            end = "",
            skipChars = "#",
            beepSec = 0,
        )

        assertEquals("новое", AssistantEngine.wakeWord.value)
        assertEquals("", AssistantEngine.endWord.value)
        assertEquals("#", AssistantEngine.ttsSkipChars.value)
        assertEquals(0, AssistantEngine.beepIntervalSec.value)

        val systemTexts = AssistantEngine.messages.value.drop(base).map { it.text }
        assertEquals(
            listOf(
                "Кодовое слово ассистента: «новое»",
                "Голосовой ввод отправляется после паузы.",
                "Перед озвучкой вырезаются символы: #",
                "Сигнал «я работаю» выключен.",
            ),
            systemTexts,
        )

        val stored = Settings(context)
        assertEquals("новое", stored.wakeWord)
        assertEquals("", stored.endWord)
        assertEquals("#", stored.ttsSkipChars)
        assertEquals(0, stored.beepIntervalSec)
    }

    @Test
    fun `saveSettings without changes adds no messages`() {
        AssistantEngine.ensureInit(context)
        val base = AssistantEngine.messages.value.size

        AssistantEngine.saveSettings("старт", "стоп", "*_#~`", 300)

        assertEquals(base, AssistantEngine.messages.value.size)
    }

    @Test
    fun `wake word change pushes notification status while assistant active`() {
        AssistantEngine.ensureInit(context)
        AssistantEngine._assistantActive.value = true

        AssistantEngine.saveSettings("джарвис", "стоп", "*_#~`", 300)

        assertEquals("джарвис", AssistantEngine.wakeWord.value)
        assertEquals("Слушаю кодовое слово «джарвис»", AssistantEngine.assistantStatus.value)
    }

    @Test
    fun `saveCustomWord persists updates flow and reports`() {
        AssistantEngine.ensureInit(context)
        val base = AssistantEngine.messages.value.size

        AssistantEngine.saveCustomWord(" Джа Вис ", "claude")

        assertEquals(mapOf("джа вис" to "claude"), AssistantEngine.customModelWords.value)
        assertEquals(mapOf("джа вис" to "claude"), Settings(context).customModelWords)
        assertEquals(
            listOf("Слово «джа вис» теперь означает модель claude"),
            AssistantEngine.messages.value.drop(base).map { it.text },
        )
    }

    @Test
    fun `saveCustomWord with blank input does nothing`() {
        AssistantEngine.ensureInit(context)
        val base = AssistantEngine.messages.value.size

        AssistantEngine.saveCustomWord("   ", "claude")
        AssistantEngine.saveCustomWord("слово", "   ")

        assertEquals(emptyMap<String, String>(), AssistantEngine.customModelWords.value)
        assertEquals(base, AssistantEngine.messages.value.size)
    }

    @Test
    fun `removeCustomWord removes word and reports`() {
        AssistantEngine.ensureInit(context)
        AssistantEngine.saveCustomWord("джарвис", "claude")
        val afterAdd = AssistantEngine.messages.value.size

        AssistantEngine.removeCustomWord("джарвис")

        assertEquals(emptyMap<String, String>(), AssistantEngine.customModelWords.value)
        assertEquals(emptyMap<String, String>(), Settings(context).customModelWords)
        assertEquals("Слово «джарвис» удалено", AssistantEngine.messages.value[afterAdd].text)
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


    @Test
    fun `manual send with keyword resolves alias and shows reply`() {
        val echo = FakeProvider()
        AssistantEngine.backend = defaultStubBackend(listOf(echo))
        val base = AssistantEngine.messages.value.size

        AssistantEngine._input.value = TextFieldValue("старт гемини расскажи анекдот")
        AssistantEngine.send()
        dispatchMain()

        assertTrue(awaitTrue { !AssistantEngine.loading.value && AssistantEngine.messages.value.size == base + 2 })
        assertEquals(1, echo.calls)

        val user = AssistantEngine.messages.value[base]
        assertTrue(user.fromUser)
        assertEquals("старт гемини расскажи анекдот", user.text)

        val reply = AssistantEngine.messages.value[base + 1]
        assertFalse(reply.fromUser)
        assertFalse(reply.isError)
        assertEquals("Эхо(echo): расскажи анекдот", reply.text)
        assertEquals("echo", reply.model)
        assertEquals("echo", reply.provider)
        assertTrue((reply.elapsedMs ?: -1L) >= 0L)
    }

    @Test
    fun `manual send without model goes through default model`() {
        val echo = FakeProvider()
        AssistantEngine.backend = defaultStubBackend(listOf(echo))

        AssistantEngine._input.value = TextFieldValue("какая погода")
        AssistantEngine.send()
        dispatchMain()

        assertTrue(awaitTrue { !AssistantEngine.loading.value })
        assertEquals("Эхо(echo): какая погода", lastMessage().text)
    }

    @Test
    fun `manual send honors custom model words`() {
        AssistantEngine.backend = defaultStubBackend()
        AssistantEngine._customModelWords.value = mapOf("тестворд" to "claude")

        AssistantEngine._input.value = TextFieldValue("скажи тестворд анекдот")
        AssistantEngine.send()
        dispatchMain()

        assertTrue(awaitTrue { !AssistantEngine.loading.value })
        assertEquals("Эхо(claude): скажи анекдот", lastMessage().text)
    }

    @Test
    fun `empty input is not sent`() {
        val echo = FakeProvider()
        AssistantEngine.backend = defaultStubBackend(listOf(echo))
        val base = AssistantEngine.messages.value.size

        AssistantEngine._input.value = TextFieldValue("   ")
        AssistantEngine.send()

        assertEquals(base, AssistantEngine.messages.value.size)
        assertFalse(AssistantEngine.loading.value)
        assertEquals(0, echo.calls)
    }

    @Test
    fun `second send while loading is ignored`() {
        val slow = FakeProvider(reply = {
            delay(300)
            "Готово!"
        })
        AssistantEngine.backend = defaultStubBackend(listOf(slow))
        val base = AssistantEngine.messages.value.size

        AssistantEngine._input.value = TextFieldValue("первый")
        AssistantEngine.send()
        dispatchMain()
        assertTrue(awaitTrue { slow.calls == 1 })

        AssistantEngine._input.value = TextFieldValue("второй")
        AssistantEngine.send()

        assertEquals("второй", AssistantEngine.input.value.text)
        assertTrue(awaitTrue { !AssistantEngine.loading.value && AssistantEngine.messages.value.size == base + 2 })
        assertEquals(1, slow.calls)
    }

    @Test
    fun `cancelRequest stops loading and reports instead of reply`() {
        val slow = FakeProvider(reply = {
            delay(5000)
            "Не успею"
        })
        AssistantEngine.backend = defaultStubBackend(listOf(slow))
        val base = AssistantEngine.messages.value.size

        AssistantEngine._input.value = TextFieldValue("долгий вопрос")
        AssistantEngine.send()
        dispatchMain()
        assertTrue(awaitTrue { slow.calls == 1 })
        assertTrue(AssistantEngine.loading.value)

        AssistantEngine.cancelRequest()

        assertFalse(AssistantEngine.loading.value)
        assertTrue(awaitTrue { AssistantEngine.messages.value.size == base + 2 })
        assertEquals("Запрос отменён", lastMessage().text)

        val deadline = System.currentTimeMillis() + 1000
        while (System.currentTimeMillis() < deadline) {
            dispatchMain()
            assertEquals(base + 2, AssistantEngine.messages.value.size)
            Thread.sleep(50)
        }
    }

    @Test
    fun `cancelRequest without in-flight request is a no-op`() {
        val base = AssistantEngine.messages.value.size

        AssistantEngine.cancelRequest()

        assertEquals(base, AssistantEngine.messages.value.size)
        assertFalse(AssistantEngine.loading.value)
    }

    @Test
    fun `unknown model becomes red message with engine code`() {
        AssistantEngine.refreshModels()
        val base = AssistantEngine.messages.value.size

        AssistantEngine._input.value = TextFieldValue("старт миник привет")
        AssistantEngine.send()

        assertTrue(awaitTrue { !AssistantEngine.loading.value && AssistantEngine.messages.value.size == base + 2 })
        val reply = AssistantEngine.messages.value[base + 1]
        assertTrue(reply.isError)
        assertEquals("Ошибка UNKNOWN_MODEL\nнеизвестная модель: миник", reply.text)
    }

    @Test
    fun `provider error becomes red message with code`() {
        val failing = FakeProvider(
            name = "boom",
            models = listOf("boom"),
            reply = { throw AiError(AiError.PROVIDER_ERROR, "ошибка провайдера") },
        )
        AssistantEngine.backend = defaultStubBackend(
            listOf(failing),
            aliases = mapOf("фри" to "boom"),
        )
        val base = AssistantEngine.messages.value.size

        AssistantEngine._input.value = TextFieldValue("привет")
        AssistantEngine.send()

        assertTrue(awaitTrue { !AssistantEngine.loading.value && AssistantEngine.messages.value.size == base + 2 })
        val reply = AssistantEngine.messages.value[base + 1]
        assertTrue(reply.isError)
        assertEquals("Ошибка PROVIDER_ERROR\nошибка провайдера", reply.text)
    }

    @Test
    fun `network failure becomes helpful error message`() {
        val failing = FakeProvider(models = listOf("echo"), reply = {
            throw IOException("Connection reset")
        })
        AssistantEngine.backend = defaultStubBackend(listOf(failing))
        val base = AssistantEngine.messages.value.size

        AssistantEngine._input.value = TextFieldValue("привет")
        AssistantEngine.send()

        assertTrue(awaitTrue { AssistantEngine.messages.value.size == base + 2 })
        val reply = AssistantEngine.messages.value[base + 1]
        assertTrue(reply.isError)
        assertTrue(reply.text.startsWith("Не удалось обратиться к модели"))
        assertTrue(reply.text.contains("Connection reset"))
    }

    @Test
    fun `manual send with assistant active pushes statuses and no voice`() {
        AssistantEngine._assistantActive.value = true
        val slow = FakeProvider(reply = {
            delay(250)
            "Готово!"
        })
        AssistantEngine.backend = defaultStubBackend(listOf(slow))
        val base = AssistantEngine.messages.value.size

        AssistantEngine._input.value = TextFieldValue("привет")
        AssistantEngine.send()

        assertEquals("Отправляю запрос…", AssistantEngine.assistantStatus.value)

        assertTrue(awaitTrue { !AssistantEngine.loading.value && AssistantEngine.messages.value.size == base + 2 })
        assertEquals("Слушаю кодовое слово «старт»", AssistantEngine.assistantStatus.value)
        assertFalse(AssistantEngine.speaking.value)
    }


    @Test
    fun `voice command is sent with default model and marked in chat`() {
        val echo = FakeProvider()
        AssistantEngine.backend = defaultStubBackend(listOf(echo))
        val base = AssistantEngine.messages.value.size

        AssistantEngine.submitVoiceCommand("привет")
        dispatchMain()

        assertTrue(awaitTrue { !AssistantEngine.loading.value && AssistantEngine.messages.value.size == base + 2 })
        assertEquals(1, echo.calls)

        val user = AssistantEngine.messages.value[base]
        assertTrue(user.fromUser)
        assertTrue(user.voice)
        assertEquals("привет", user.text)

        val reply = AssistantEngine.messages.value[base + 1]
        assertEquals("Эхо(echo): привет", reply.text)
        assertFalse(AssistantEngine.speaking.value)
    }


    @Test
    fun `refreshModels fills suggestions and provider names from engine`() {
        AssistantEngine.backend = defaultStubBackend(
            aliases = mapOf(
                "gemini" to "google/gemini",
                "фри" to "openrouter/free",
                "бесплатно" to "openrouter/free",
                "миник" to "qwen/qwen3:free",
            ),
        )
        AssistantEngine.refreshModels()

        val models = AssistantEngine.models.value
        assertEquals(listOf("gemini", "фри", "миник"), models.suggestions)
        assertEquals(emptyList<String>(), models.keywords)
        assertTrue("миник" in models.serverModels)
        assertTrue("qwen/qwen3:free" in models.serverModels)
        assertEquals(listOf("echo"), models.providers)
    }

    @Test
    fun `refreshModels without providers keeps suggestions empty`() {
        AssistantEngine.backend = defaultStubBackend(emptyList())
        AssistantEngine.refreshModels()
        dispatchMain()

        assertEquals(emptyList<String>(), AssistantEngine.models.value.suggestions)
        assertEquals(emptyList<String>(), AssistantEngine.models.value.providers)
    }


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
