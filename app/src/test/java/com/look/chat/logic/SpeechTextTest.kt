package com.look.chat.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTextTest {

    private val defaultSkip = "*_#~`"

    // --- prepare ---

    @Test
    fun `prepare strips markdown symbols and collapses whitespace`() {
        val prepared = SpeechText.prepare("**Привет,  _мир_!**\n\nВторая  строка", defaultSkip)
        assertEquals("Привет, мир! Вторая строка", prepared)
    }

    @Test
    fun `prepare with empty skip chars keeps everything`() {
        val prepared = SpeechText.prepare("**Привет**\nмир", "")
        assertEquals("**Привет** мир", prepared)
    }

    @Test
    fun `prepare with custom skip chars removes them`() {
        val prepared = SpeechText.prepare("Привет 🙂 мир", "🙂")
        assertEquals("Привет мир", prepared)
    }

    @Test
    fun `prepare of symbols only gives empty string`() {
        assertEquals("", SpeechText.prepare("*_#~", defaultSkip))
        assertEquals("", SpeechText.prepare("   ", defaultSkip))
    }

    // --- split ---

    @Test
    fun `short text is returned as single chunk`() {
        assertEquals(listOf("Привет!"), SpeechText.split("Привет!", max = 4000))
    }

    @Test
    fun `text at exact limit is not split`() {
        val text = "а".repeat(100)
        assertEquals(listOf(text), SpeechText.split(text, max = 100))
    }

    @Test
    fun `long text splits by sentences without losing words`() {
        val chunks = SpeechText.split("Раз. Два. Три.", max = 10)
        assertEquals(listOf("Раз. Два.", "Три."), chunks)
    }

    @Test
    fun `oversized sentence is split by words`() {
        val chunks = SpeechText.split("Слово1 слово2 слово3", max = 12)
        assertEquals(listOf("Слово1", "слово2", "слово3"), chunks)
    }

    @Test
    fun `every chunk fits the limit and content is preserved`() {
        val text = buildString {
            repeat(80) { i ->
                append("Предложение номер $i рассказывает историю про тесты и ассистента. ")
            }
        }
        val chunks = SpeechText.split(text, max = 4000)

        assertTrue(chunks.size > 1)
        chunks.forEach { chunk ->
            assertTrue("кусок длиннее лимита: ${chunk.length}", chunk.length <= 4000)
        }
        val rejoined = chunks.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        assertEquals(text.replace(Regex("\\s+"), " ").trim(), rejoined)
    }
}
