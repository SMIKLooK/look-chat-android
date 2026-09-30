package com.look.chat.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Машина состояний диктовки без микрофона: события Vosk подаются напрямую
 * в onMicPartial/onMicFinal, а готовность менеджера проверяется по
 * колбэкам и флагам. Ровно эти пути работают в приложении, когда
 * MicListener читает аудио.
 */
@RunWith(RobolectricTestRunner::class)
class VoiceSessionManagerTest {

    private val wake = MutableStateFlow("старт")
    private val end = MutableStateFlow("стоп")

    private var dictatingNow = false
    private var heardNow = ""

    private val commands = mutableListOf<String>()
    private val statuses = mutableListOf<String>()
    private val systemMessages = mutableListOf<String>()

    private lateinit var session: VoiceSessionManager

    @Before
    fun setUp() {
        commands.clear()
        statuses.clear()
        systemMessages.clear()
        dictatingNow = false
        heardNow = ""
        wake.value = "старт"
        end.value = "стоп"

        session = VoiceSessionManager(
            context = ApplicationProvider.getApplicationContext(),
            wakeWord = { wake.value },
            endWord = { end.value },
            onDictatingChanged = { dictatingNow = it },
            onHeardChanged = { heardNow = it },
            onCommandFinalized = { commands.add(it) },
            onStatusUpdate = { statuses.add(it) },
            onSystemMessage = { systemMessages.add(it) },
        )
    }

    private fun partial(text: String) = session.onMicPartial(text)
    private fun final(text: String) = session.onMicFinal(text)

    // --- ожидание кодового слова ---

    @Test
    fun `partial without wake word is ignored`() {
        partial("привет как дела")

        assertFalse(session.voiceArmed)
        assertFalse(session.confirmingWake)
        assertFalse(dictatingNow)
        assertEquals("", heardNow)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `final without wake word is ignored`() {
        final("привет как дела")

        assertFalse(session.voiceArmed)
        assertFalse(session.confirmingWake)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `custom wake word from settings is respected`() {
        wake.value = "джарвис"

        partial("джарвис")

        assertTrue(session.confirmingWake)
    }

    // --- подтверждение кандидата и начало диктовки ---

    @Test
    fun `wake candidate waits for confirmation then arms dictation`() {
        partial("старт")
        assertTrue(session.confirmingWake)
        assertFalse(session.voiceArmed)
        assertFalse(dictatingNow)

        partial("старт привет")

        assertTrue(session.voiceArmed)
        assertTrue(dictatingNow)
        assertEquals("старт привет", heardNow)
        assertEquals("старт привет", session.pendingSegmentPartial)
        assertTrue("Записываю… закончите словом «стоп»" in statuses)
    }

    @Test
    fun `final result replaces partial of the same phrase without doubling`() {
        partial("старт")
        partial("старт привет")
        final("старт привет мир")

        assertEquals("старт привет мир", heardNow)
        assertEquals(null, session.pendingSegmentPartial)
        assertTrue(dictatingNow)
    }

    @Test
    fun `unconfirmed wake candidate is rejected quietly`() {
        partial("старт")
        session.confirmStartedAt = System.currentTimeMillis() - 11_000

        session.rejectWakeCandidate()

        assertFalse(session.confirmingWake)
        assertFalse(session.voiceArmed)
        assertFalse(dictatingNow)
        assertEquals("", heardNow)
        assertTrue(commands.isEmpty())
        assertTrue(systemMessages.isEmpty())
    }

    // --- слово окончания ---

    @Test
    fun `end word in partial finalizes and emits command`() {
        partial("старт")
        partial("старт привет")
        partial("старт привет стоп")

        assertEquals(listOf("привет"), commands)
        assertFalse(dictatingNow)
        assertEquals("", heardNow)
        assertTrue(session.voiceBusy)
    }

    @Test
    fun `end word in final result finalizes`() {
        partial("старт") // кандидат
        final("старт скажи привет стоп") // подтверждение сразу со стопом

        assertEquals(listOf("скажи привет"), commands)
        assertFalse(dictatingNow)
    }

    @Test
    fun `only text before the first end word is sent`() {
        partial("старт")
        partial("старт скажи")
        partial("старт скажи стоп анекдот")

        assertEquals(listOf("скажи"), commands)
    }

    @Test
    fun `with empty end word stop is treated as text and pause sends everything`() {
        end.value = ""

        partial("старт")
        partial("старт привет стоп")

        assertTrue(commands.isEmpty()) // «стоп» — просто часть запроса
        assertEquals("старт привет стоп", heardNow)

        session.lastVoiceActivityAt = System.currentTimeMillis() - 4000
        session.checkSilenceSend()

        assertEquals(listOf("привет стоп"), commands)
    }

    // --- тишина ---

    @Test
    fun `silence after dictation sends accumulated text`() {
        partial("старт")
        final("старт скажи анекдот") // без стопа — ждём тишину
        assertTrue(dictatingNow)
        assertTrue(commands.isEmpty())

        session.lastVoiceActivityAt = System.currentTimeMillis() - 4000
        session.checkSilenceSend()

        assertEquals(listOf("скажи анекдот"), commands)
        assertFalse(dictatingNow)
    }

    @Test
    fun `silence after wake word only resets dictation`() {
        partial("старт")
        final("старт") // сказали только кодовое слово
        session.lastVoiceActivityAt = System.currentTimeMillis() - 4000
        session.checkSilenceSend()

        assertFalse(dictatingNow)
        assertFalse(session.voiceArmed)
        assertEquals("", heardNow)
        assertTrue(commands.isEmpty())
        assertTrue("После «старт» не было запроса — слушаю дальше." in systemMessages)
        assertTrue("Слушаю кодовое слово «старт»" in statuses)
    }

    @Test
    fun `silence check does nothing while not dictating`() {
        session.checkSilenceSend()

        assertTrue(commands.isEmpty())
        assertTrue(systemMessages.isEmpty())
    }

    @Test
    fun `silence check skips while request is in flight`() {
        partial("старт")
        partial("старт привет")
        partial("старт привет стоп") // улетело, voiceBusy = true
        commands.clear()
        session.lastVoiceActivityAt = System.currentTimeMillis() - 4000

        session.checkSilenceSend()

        assertTrue(commands.isEmpty())
    }

    // --- стык с движком: приостановка и возобновление ---

    @Test
    fun `suspendForRequest resets dictation`() {
        partial("старт")
        partial("старт привет мир")
        assertTrue(dictatingNow)

        session.suspendForRequest()

        assertFalse(session.voiceArmed)
        assertFalse(dictatingNow)
        assertEquals("", heardNow)
    }

    @Test
    fun `clearBusy lets microphone events through again`() {
        partial("старт")
        partial("старт привет")
        partial("старт привет стоп") // voiceBusy = true
        assertTrue(session.voiceBusy)

        partial("старт снова") // игнорируется
        assertTrue(commands.size == 1)

        session.clearBusy()
        assertFalse(session.voiceBusy)
    }

    @Test
    fun `stop resets everything`() {
        partial("старт")
        partial("старт привет мир")
        assertTrue(dictatingNow)

        session.stop()

        assertFalse(dictatingNow)
        assertEquals("", heardNow)
        assertFalse(session.voiceBusy)
    }

    // --- статус диктовки ---

    @Test
    fun `dictation status mentions end word`() {
        partial("старт")
        partial("старт привет")

        assertTrue("Записываю… закончите словом «стоп»" in statuses)
    }

    @Test
    fun `dictation status without end word mentions pause`() {
        end.value = ""

        partial("старт")
        partial("старт привет")

        assertTrue("Записываю… пауза отправит запрос" in statuses)
    }
}
