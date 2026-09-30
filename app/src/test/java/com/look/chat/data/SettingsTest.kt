package com.look.chat.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
class SettingsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPrefs() {
        prefs.edit().clear().commit()
    }

    private val prefs get() = context.getSharedPreferences("look_chat", Context.MODE_PRIVATE)

    private fun fresh() = Settings(context)

    @Test
    fun `defaults on a device build`() {
        val s = fresh()
        // Robolectric не эмулятор — должен выбираться адрес компьютера в LAN
        assertEquals(Settings.PC_LAN_URL, s.serverUrl)
        assertEquals(Settings.DEFAULT_WAKE_WORD, s.wakeWord)
        assertEquals(Settings.DEFAULT_END_WORD, s.endWord)
        assertEquals(Settings.DEFAULT_TTS_SKIP_CHARS, s.ttsSkipChars)
        assertEquals(Settings.DEFAULT_BEEP_INTERVAL_SEC, s.beepIntervalSec)
        assertEquals(emptyMap<String, String>(), s.customModelWords)
    }

    @Test
    fun `emulator fingerprint switches to emulator url`() {
        ShadowBuild.setFingerprint("generic/google/generic_x86")
        assertEquals(Settings.EMULATOR_URL, Settings.defaultUrl())
    }

    @Test
    fun `server url is trimmed and stored`() {
        fresh().serverUrl = "http://host:8080///"
        assertEquals("http://host:8080", fresh().serverUrl)
    }

    @Test
    fun `blank stored server url falls back to default`() {
        fresh().serverUrl = "   "
        assertEquals(Settings.PC_LAN_URL, fresh().serverUrl)
    }

    @Test
    fun `wake word is lowercased and trimmed`() {
        fresh().wakeWord = "  Джарвис  "
        assertEquals("джарвис", fresh().wakeWord)
    }

    @Test
    fun `end word is lowercased and can be emptied`() {
        val s = fresh()
        s.endWord = " СтоП "
        assertEquals("стоп", fresh().endWord)
        s.endWord = ""
        assertEquals("", fresh().endWord)
    }

    @Test
    fun `tts skip chars are stored as is`() {
        fresh().ttsSkipChars = "*#🙂"
        assertEquals("*#🙂", fresh().ttsSkipChars)
    }

    @Test
    fun `beep interval is coerced to non-negative`() {
        val s = fresh()
        s.beepIntervalSec = -5
        assertEquals(0, fresh().beepIntervalSec)
        s.beepIntervalSec = 15
        assertEquals(15, fresh().beepIntervalSec)
    }

    @Test
    fun `garbage beep interval falls back to default`() {
        prefs.edit().putString("beep_interval_sec", "abc").commit()
        assertEquals(Settings.DEFAULT_BEEP_INTERVAL_SEC, fresh().beepIntervalSec)
    }

    @Test
    fun `custom model words survive roundtrip`() {
        fresh().customModelWords = mapOf("джа вис" to "claude", "фри2" to "gemini")
        assertEquals(mapOf("джа вис" to "claude", "фри2" to "gemini"), fresh().customModelWords)
    }

    @Test
    fun `custom model words keep equals sign inside model`() {
        fresh().customModelWords = mapOf("слово" to "a=b")
        assertEquals(mapOf("слово" to "a=b"), fresh().customModelWords)
    }

    @Test
    fun `malformed custom word entries are skipped`() {
        prefs.edit().putString("custom_model_words", "безравно,=модель,слово=,норм=gemini").commit()
        assertEquals(mapOf("норм" to "gemini"), fresh().customModelWords)
    }

    @Test
    fun `settings are stored under one shared prefs file`() {
        // разные свойства должны жить в одном файле — иначе часть настроек терялась бы
        val s = fresh()
        s.serverUrl = "http://host:1"
        s.wakeWord = "джарвис"
        val reread = fresh()
        assertEquals("http://host:1", reread.serverUrl)
        assertEquals("джарвис", reread.wakeWord)
        assertTrue(prefs.contains("server_url"))
        assertTrue(prefs.contains("wake_word"))
    }
}
