package com.look.chat.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DtosTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `process request encodes to expected json`() {
        val encoded = json.encodeToString(ProcessRequest(text = "gemini привет"))
        assertEquals("""{"text":"gemini привет"}""", encoded)
    }

    @Test
    fun `ok envelope is parsed with all fields`() {
        val raw = """
            {"status":"ok","model":"gemini-3.6-flash","provider":"google",
             "answer":"Привет!","elapsed_ms":1234}
        """.trimIndent()
        val response = json.decodeFromString<ProcessResponse>(raw)
        assertEquals("ok", response.status)
        assertEquals("gemini-3.6-flash", response.model)
        assertEquals("google", response.provider)
        assertEquals("Привет!", response.answer)
        assertEquals(1234L, response.elapsedMs)
        assertNull(response.error)
    }

    @Test
    fun `error envelope is parsed into error field`() {
        val raw = """
            {"status":"error","error":{"code":"KEYWORD_NOT_FOUND","message":"Модель не найдена"}}
        """.trimIndent()
        val response = json.decodeFromString<ProcessResponse>(raw)
        assertEquals("error", response.status)
        assertEquals("KEYWORD_NOT_FOUND", response.error?.code)
        assertEquals("Модель не найдена", response.error?.message)
        assertNull(response.answer)
    }

    @Test
    fun `unknown fields and missing fields are tolerated`() {
        val raw = """{"status":"ok","answer":"ок","unexpected_field":42}"""
        val response = json.decodeFromString<ProcessResponse>(raw)
        assertEquals("ок", response.answer)
        assertNull(response.model)
        assertNull(response.provider)
        assertNull(response.elapsedMs)
        assertNull(response.error)
    }

    @Test
    fun `elapsed_ms field is mapped from snake_case`() {
        val response = json.decodeFromString<ProcessResponse>("""{"elapsed_ms":7}""")
        assertEquals(7L, response.elapsedMs)
    }

    @Test
    fun `models response is parsed with providers`() {
        val raw = """
            {"keywords":["старт","эхо"],
             "aliases":{"гемини":"google/gemini","фри":"openrouter/free"},
             "providers":[
               {"name":"google","models":["gemini-3.6-flash"]},
               {"name":"echo","models":["echo"]}
             ]}
        """.trimIndent()
        val response = json.decodeFromString<ModelsResponse>(raw)
        assertEquals(listOf("старт", "эхо"), response.keywords)
        assertEquals(mapOf("гемини" to "google/gemini", "фри" to "openrouter/free"), response.aliases)
        assertEquals(2, response.providers.size)
        assertEquals("google", response.providers[0].name)
        assertEquals(listOf("gemini-3.6-flash"), response.providers[0].models)
    }

    @Test
    fun `models response with missing fields falls back to defaults`() {
        val response = json.decodeFromString<ModelsResponse>("{}")
        assertEquals(emptyList<String>(), response.keywords)
        assertEquals(emptyMap<String, String>(), response.aliases)
        assertEquals(emptyList<ProviderInfo>(), response.providers)
    }
}
