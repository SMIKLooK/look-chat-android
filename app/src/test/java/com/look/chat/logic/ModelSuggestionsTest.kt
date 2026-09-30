package com.look.chat.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSuggestionsTest {

    @Test
    fun `pinned models go first`() {
        val aliases = mapOf(
            "gemini" to "google/gemini",
            "фри" to "openrouter/free",
            "deepseek" to "deepseek/chat:free",
        )
        assertEquals(listOf("deepseek", "gemini", "фри"), ModelSuggestions.visible(aliases))
    }

    @Test
    fun `gigachat is pinned when backend exposes its alias`() {
        val aliases = mapOf(
            "deepseek" to "deepseek/deepseek-v4-flash",
            "gemini" to "gemini-3.6-flash",
            "фри" to "openrouter/free",
            "гигачат" to "GigaChat",
            "gigachat" to "GigaChat",
            "сбер" to "GigaChat",
        )
        assertEquals(
            listOf("deepseek", "gemini", "фри", "гигачат"),
            ModelSuggestions.visible(aliases),
        )
    }

    @Test
    fun `pinned matching is case-insensitive`() {
        val aliases = mapOf("DeepSeek" to "deepseek/chat:free")
        assertEquals(listOf("deepseek"), ModelSuggestions.visible(aliases))
    }

    @Test
    fun `free aliases deduplicate by target preferring latin name`() {
        val aliases = mapOf(
            "deepseek" to "deepseek/chat:free", // закреплённая со своей целью
            "бесплатно" to "openrouter/free",   // не закреплённая — попадёт в список
            "free" to "openrouter/free",        // латинское имя вытесняет русское
            "миник" to "qwen/qwen3:free",       // отдельная :free-модель
        )
        val suggestions = ModelSuggestions.visible(aliases)
        assertEquals(listOf("deepseek", "free", "миник"), suggestions)
    }

    @Test
    fun `aliases on the pinned model target are hidden`() {
        // «фри» закреплена и уже ведёт на openrouter/free — её дубликаты не показываем
        val aliases = mapOf(
            "фри" to "openrouter/free",
            "бесплатно" to "openrouter/free",
            "free" to "openrouter/free",
            "миник" to "qwen/qwen3:free",
        )
        assertEquals(listOf("фри", "миник"), ModelSuggestions.visible(aliases))
    }

    @Test
    fun `non-free aliases without pin are not shown`() {
        val aliases = mapOf("гемини" to "google/gemini-pro-paid")
        assertEquals(emptyList<String>(), ModelSuggestions.visible(aliases))
    }

    @Test
    fun `custom pinned list can be passed explicitly`() {
        val aliases = mapOf("эхо" to "echo-model")
        assertEquals(listOf("эхо"), ModelSuggestions.visible(aliases, pinned = listOf("эхо")))
    }
}
