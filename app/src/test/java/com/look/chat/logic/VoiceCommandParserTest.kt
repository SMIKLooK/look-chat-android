package com.look.chat.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {

    private val wake = "старт"
    private val end = "стоп"

    // --- кодовое слово ---

    @Test
    fun `exact wake word matches`() {
        assertTrue(VoiceCommandParser.isWakeToken("старт", wake))
        assertTrue(VoiceCommandParser.isWakeToken("Старт", wake))
    }

    @Test
    fun `wake word tolerates small endings`() {
        assertTrue(VoiceCommandParser.isWakeToken("старта", wake))
        assertTrue(VoiceCommandParser.isWakeToken("старту", wake))
    }

    @Test
    fun `wake word rejects too different tokens`() {
        assertFalse(VoiceCommandParser.isWakeToken("стартовый", wake)) // +3 буквы
        assertFalse(VoiceCommandParser.isWakeToken("стартап", wake))
        assertFalse(VoiceCommandParser.isWakeToken("ст", wake))
        assertFalse(VoiceCommandParser.isWakeToken("привет", wake))
    }

    @Test
    fun `short custom wake word requires exact match`() {
        // двухбуквенное кодовое слово не должно ловить любую речь на «ок»
        assertFalse(VoiceCommandParser.isWakeToken("окно", "ок"))
        assertTrue(VoiceCommandParser.isWakeToken("ок", "ок"))
    }

    @Test
    fun `wakeTokenIndex finds first wake occurrence`() {
        assertEquals(2, VoiceCommandParser.wakeTokenIndex(listOf("раз", "раз", "старт", "привет"), wake))
        assertEquals(-1, VoiceCommandParser.wakeTokenIndex(listOf("привет", "как", "дела"), wake))
    }

    // --- слово окончания ---

    @Test
    fun `exact end word matches`() {
        assertTrue(VoiceCommandParser.isEndToken("стоп", end))
        assertTrue(VoiceCommandParser.isEndToken("Стоп.", end))
    }

    @Test
    fun `end word tolerates one extra letter`() {
        assertTrue(VoiceCommandParser.isEndToken("стопа", end))
        assertTrue(VoiceCommandParser.isEndToken("стопы", end))
    }

    @Test
    fun `end word rejects two or more extra letters`() {
        assertFalse(VoiceCommandParser.isEndToken("стопами", end))
        assertFalse(VoiceCommandParser.isEndToken("сторож", end))
    }

    @Test
    fun `heardContainsEnd ignores punctuation and yo`() {
        assertTrue(VoiceCommandParser.heardContainsEnd("скажи анекдот стоп", end))
        assertTrue(VoiceCommandParser.heardContainsEnd("скажи анекдот, стоп.", end))
        assertTrue(VoiceCommandParser.heardContainsEnd("СТОП", end))
        assertFalse(VoiceCommandParser.heardContainsEnd("скажи анекдот", end))
    }

    @Test
    fun `heardContainsEnd with custom end word`() {
        assertTrue(VoiceCommandParser.heardContainsEnd("ну всё давай хватит", "хватит"))
        assertFalse(VoiceCommandParser.heardContainsEnd("хват", "хватит"))
    }

    // --- разбор сказанного ---

    @Test
    fun `textBeforeEnd cuts at first end word`() {
        assertEquals("скажи анекдот", VoiceCommandParser.textBeforeEnd("скажи анекдот стоп", end))
        assertEquals("скажи", VoiceCommandParser.textBeforeEnd("скажи стоп анекдот", end))
        assertEquals("", VoiceCommandParser.textBeforeEnd("стоп", end))
        assertEquals("скажи анекдот", VoiceCommandParser.textBeforeEnd("скажи анекдот", end))
    }

    @Test
    fun `afterWake drops everything up to and including wake word`() {
        assertEquals("привет", VoiceCommandParser.afterWake("раз раз старт привет", wake))
        assertEquals("", VoiceCommandParser.afterWake("старт", wake))
        assertEquals("привет", VoiceCommandParser.afterWake("привет", wake))
    }

    @Test
    fun `fromWake keeps wake word itself for status line`() {
        assertEquals("старт привет", VoiceCommandParser.fromWake("раз раз старт привет", wake))
        assertEquals("старт", VoiceCommandParser.fromWake("старт", wake))
        assertEquals("привет", VoiceCommandParser.fromWake("привет", wake))
    }
}
