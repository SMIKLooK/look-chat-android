package com.look.chat.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class RequestTextTest {

    private fun build(
        vararg tokens: String,
        customWords: Map<String, String> = emptyMap(),
        serverModels: List<String> = emptyList(),
        insertDefaultModel: Boolean = true,
    ): String = RequestText.build(
        tokens = tokens.toList(),
        customWords = customWords,
        serverModels = serverModels,
        insertDefaultModel = insertDefaultModel,
    )

    // --- псевдонимы моделей ---

    @Test
    fun `russian model names are translated`() {
        assertEquals("gemini расскажи анекдот", build("гемини", "расскажи", "анекдот"))
        assertEquals("gemini привет", build("джемини", "привет"))
        assertEquals("gemini привет", build("геминис", "привет"))
        assertEquals("deepseek привет", build("дипсик", "привет"))
        assertEquals("deepseek привет", build("депсик", "привет"))
    }

    @Test
    fun `punctuation and case in model name are ignored`() {
        assertEquals("gemini привет", build("Гемини,", "привет"))
    }

    @Test
    fun `passthrough words keep their model even before the server list is loaded`() {
        // serverModels пуст (запуск приложения, сервер недоступен):
        // «deepseek» не должен подмениться моделью по умолчанию,
        // а «фри» не должен задвоиться
        assertEquals("deepseek погода", build("deepseek", "погода"))
        assertEquals("free погода", build("free", "погода"))
        assertEquals("фри какая погода", build("фри", "какая", "погода"))
    }

    @Test
    fun `two-word phrase aliases map to deepseek`() {
        assertEquals("deepseek анекдот", build("дип", "сик", "анекдот"))
        assertEquals("deepseek анекдот", build("ди", "псих", "анекдот"))
        assertEquals("deepseek анекдот", build("депп", "сик", "анекдот"))
        assertEquals("deepseek привет", build("дип", "сок", "привет"))
    }

    @Test
    fun `phrase aliases cover hyphenated backend models`() {
        // голос слышит «немотрон ультра», бекенд ждёт «немотрон-ультра»
        assertEquals("немотрон-ультра расскажи сказку", build("немотрон", "ультра", "расскажи", "сказку"))
        assertEquals("немотрон-супер привет", build("немотрон", "супер", "привет"))
        assertEquals("немотрон-лайт привет", build("немотрон", "лайт", "привет"))
        assertEquals("немотрон-омни привет", build("немотрон", "омни", "привет"))
        assertEquals("гемма-мини привет", build("гемма", "мини", "привет"))
        assertEquals("лагуна-мини привет", build("лагуна", "мини", "привет"))
        assertEquals("инклинг-мини привет", build("инклинг", "мини", "привет"))
        assertEquals("некс-мини привет", build("некс", "мини", "привет"))
        assertEquals("норд-код привет", build("норд", "код", "привет"))
        assertEquals("линг-мед привет", build("линг", "мед", "привет"))
        assertEquals("линг-фин привет", build("линг", "фин", "привет"))
        assertEquals("гигачат привет", build("гига", "чат", "привет"))
    }

    @Test
    fun `gigachat passes through when server knows it`() {
        assertEquals(
            "гигачат привет",
            build("гигачат", "привет", serverModels = listOf("гигачат", "gigachat", "сбер", "sber")),
        )
    }

    @Test
    fun `known server model passes as is`() {
        assertEquals(
            "echo тест",
            build("echo", "тест", serverModels = listOf("echo", "gemini-3.6-flash")),
        )
    }

    @Test
    fun `unknown model word falls back to default model`() {
        assertEquals("фри привет", build("привет"))
    }

    @Test
    fun `default model insertion can be disabled`() {
        assertEquals("привет", build("привет", insertDefaultModel = false))
    }

    @Test
    fun `empty tokens give empty request`() {
        assertEquals("", build())
    }

    // --- свои слова для моделей ---

    @Test
    fun `custom word found mid-sentence moves model first and is cut out`() {
        assertEquals(
            "claude скажи анекдот",
            build("скажи", "тестворд", "анекдот", customWords = mapOf("тестворд" to "claude")),
        )
    }

    @Test
    fun `custom word of two tokens matches`() {
        assertEquals(
            "claude привет",
            build("джа", "вис", "привет", customWords = mapOf("джа вис" to "claude")),
        )
    }

    @Test
    fun `custom word matches by transliteration`() {
        // голосом «testword» распознаётся как «тестворд»
        assertEquals("claude", build("тестворд", customWords = mapOf("testword" to "claude")))
    }

    @Test
    fun `custom word matches with small typos for long words`() {
        assertEquals("claude", build("джарвс", customWords = mapOf("джарвис" to "claude")))
    }

    @Test
    fun `short custom words do not match by typos`() {
        assertEquals("фри код", build("код", customWords = mapOf("кот" to "claude")))
    }

    @Test
    fun `custom word takes precedence over built-in aliases`() {
        assertEquals(
            "claude привет",
            build("гемини", "привет", customWords = mapOf("гемини" to "claude")),
        )
    }

    // --- ключевые слова бекенда ---

    @Test
    fun `backend keywords fall back to defaults while server is silent`() {
        assertEquals(setOf("старт", "start"), RequestText.backendKeywords(emptyList()))
    }

    @Test
    fun `backend keywords come from server lowercased`() {
        assertEquals(setOf("старт", "эхо"), RequestText.backendKeywords(listOf("Старт", "ЭХО")))
    }
}
