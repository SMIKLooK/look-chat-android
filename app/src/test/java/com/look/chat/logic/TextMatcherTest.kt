package com.look.chat.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatcherTest {

    // --- normalize ---

    @Test
    fun `normalize lowercases, replaces yo and trims punctuation`() {
        assertEquals("елка", TextMatcher.normalize("ЁлКА, "))
        assertEquals("привет", TextMatcher.normalize("ПРИВЕТ!"))
        assertEquals("", TextMatcher.normalize(",,,"))
    }

    // --- splitTokens ---

    @Test
    fun `splitTokens splits by any whitespace`() {
        assertEquals(listOf("джа", "вис"), TextMatcher.splitTokens("  Джа \t вис "))
        assertEquals(emptyList<String>(), TextMatcher.splitTokens("   "))
    }

    // --- translitToRu ---

    @Test
    fun `translit maps latin letters to cyrillic by sound`() {
        assertEquals("тестворд", TextMatcher.translateToRu("testword"))
        assertEquals("кси", TextMatcher.translateToRu("xy")) // x→кс, y→и
        assertEquals("привет", TextMatcher.translateToRu("привет")) // кириллица не трогается
    }

    // --- matches: точное сравнение ---

    @Test
    fun `matches compares exactly`() {
        assertTrue(TextMatcher.matches("привет", "привет"))
        assertTrue(TextMatcher.matches("Привет,", "привет"))
    }

    @Test
    fun `matches ignores yo difference`() {
        // раньше ё→е применялась только к одной стороне — точное сравнение падало
        assertTrue(TextMatcher.matches("ёжик", "ежик"))
        assertTrue(TextMatcher.matches("все", "всё"))
    }

    // --- matches: транслит ---

    @Test
    fun `matches compares through transliteration`() {
        assertTrue(TextMatcher.matches("тестворд", "testword"))
        assertTrue(TextMatcher.matches("testword", "тестворд"))
    }

    // --- matches: опечатки ---

    @Test
    fun `matches tolerates typos only for words of five letters or more`() {
        assertTrue(TextMatcher.matches("джарвс", "джарвис"))
        assertFalse(TextMatcher.matches("код", "кот"))
    }

    @Test
    fun `matches rejects empty words`() {
        assertFalse(TextMatcher.matches("", "привет"))
        assertFalse(TextMatcher.matches("привет", ""))
    }

    @Test
    fun `matches rejects clearly different words`() {
        assertFalse(TextMatcher.matches("погода", "анекдот"))
    }
}
