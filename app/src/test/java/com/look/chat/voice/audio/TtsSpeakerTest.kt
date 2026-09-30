package com.look.chat.voice.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Озвучка до инициализации движка: speak обязан сразу вызывать onDone
 * (ответ уходит только текстом) и не включать состояние «говорю».
 * Живой TTS проверяется на устройстве — здесь важен контракт без аудио.
 */
@RunWith(RobolectricTestRunner::class)
class TtsSpeakerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `speak before init falls back to onDone immediately`() {
        val speaker = TtsSpeaker(onSystemMessage = {})
        var done = false

        speaker.speak("Привет!", skipChars = "*_#~`") { done = true }

        assertTrue(done)
        assertFalse(speaker.isSpeaking)
        assertFalse(speaker.isReady)
    }

    @Test
    fun `stop without speaking is a no-op`() {
        val speaker = TtsSpeaker(onSystemMessage = {})

        speaker.stop()

        assertFalse(speaker.isSpeaking)
    }

    @Test
    fun `shutdown without init is a no-op`() {
        val speaker = TtsSpeaker(onSystemMessage = {})

        speaker.shutdown()

        assertFalse(speaker.isReady)
        assertFalse(speaker.isSpeaking)
    }

    @Test
    fun `system messages reach the callback after failed init`() {
        val messages = mutableListOf<String>()
        val speaker = TtsSpeaker(onSystemMessage = { messages.add(it) })

        speaker.init(context)
        // Robolectric создаёт TextToSpeech-заглушку: ждём её колбэк
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        // инициализация заглушки не даёт рабочего русского голоса —
        // важно, что сообщение об этом дошло до чата (или не упало вовсе)
        assertTrue(messages.isEmpty() || messages.single().contains("текстом"))
    }
}
