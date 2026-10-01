package com.look.chat.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpTest {

    @Test
    fun `chat request json builds model and messages`() {
        val json = chatRequestJson(
            "gpt-4o",
            listOf(AiMessage("user", "привет"), AiMessage("assistant", "ок")),
            maxTokens = 0,
        )
        assertEquals(
            """{"model":"gpt-4o","messages":[{"role":"user","content":"привет"},""" +
                """{"role":"assistant","content":"ок"}]}""",
            json,
        )
    }

    @Test
    fun `chat request json includes max tokens only when positive`() {
        val without = chatRequestJson("gpt-4o", listOf(AiMessage("user", "в")), maxTokens = 0)
        val with = chatRequestJson("gpt-4o", listOf(AiMessage("user", "в")), maxTokens = 50)

        assertTrue(!without.contains("max_tokens"))
        assertTrue(with.endsWith(""","max_tokens":50}"""))
    }

    @Test
    fun `chat reply trims content`() {
        val result = HttpResult(200, """{"choices":[{"message":{"content":"  ответ  "}}]}""")
        assertEquals("ответ", chatReply("echo", result))
    }

    @Test
    fun `chat reply success without content gives empty string`() {
        val result = HttpResult(200, """{"choices":[{"message":{}}]}""")
        assertEquals("", chatReply("echo", result))
    }

    @Test
    fun `chat reply empty choices is error`() {
        val result = HttpResult(200, """{"choices":[]}""")
        val e = assertThrows(AiError::class.java) { chatReply("echo", result) }
        assertTrue(e.message!!.contains("пустой список choices"))
    }

    @Test
    fun `chat reply non-json body is error with truncated body`() {
        val result = HttpResult(500, "x".repeat(600))
        val e = assertThrows(AiError::class.java) { chatReply("echo", result) }
        assertTrue(e.message!!.contains("некорректный ответ"))
        assertTrue(e.message!!.endsWith("..."))
    }

    @Test
    fun `chat reply http error extracts message from error object`() {
        val result = HttpResult(429, """{"error":{"message":"лимит"}}""")
        val e = assertThrows(AiError::class.java) { chatReply("echo", result) }
        assertEquals("ошибка echo: HTTP 429: лимит", e.message)
    }

    @Test
    fun `chat reply http error falls back to description then plain string`() {
        val byDescription = HttpResult(500, """{"error":{"description":"сбой"}}""")
        val e1 = assertThrows(AiError::class.java) { chatReply("echo", byDescription) }
        assertEquals("ошибка echo: HTTP 500: сбой", e1.message)

        val plain = HttpResult(500, """{"error":"сбой"}""")
        val e2 = assertThrows(AiError::class.java) { chatReply("echo", plain) }
        assertEquals("ошибка echo: HTTP 500: сбой", e2.message)
    }

    @Test
    fun `chat reply http error without details shows only code`() {
        val result = HttpResult(500, """{}""")
        val e = assertThrows(AiError::class.java) { chatReply("echo", result) }
        assertEquals("ошибка echo: HTTP 500", e.message)
    }

    @Test
    fun `chat reply error hook receives code and text and can override`() {
        val result = HttpResult(403, """{"error":{"message":"запрещено"}}""")
        var hookCode = 0
        var hookText = ""
        val e = assertThrows(AiError::class.java) {
            chatReply("echo", result) { code, errText, _ ->
                hookCode = code
                hookText = errText.orEmpty()
                throw AiError(AiError.PROVIDER_ERROR, "своя ошибка")
            }
        }
        assertEquals(403, hookCode)
        assertEquals("запрещено", hookText)
        assertEquals("своя ошибка", e.message)
    }

    @Test
    fun `chat reply hook without exception still throws generic error`() {
        val result = HttpResult(403, """{"error":{"message":"запрещено"}}""")
        val e = assertThrows(AiError::class.java) {
            chatReply("echo", result) { _, _, _ -> }
        }
        assertEquals("ошибка echo: HTTP 403: запрещено", e.message)
    }

    @Test
    fun `error text extraction covers all shapes`() {
        assertNull(extractErrorText(null))
        assertNull(extractErrorText(Http.json.parseToJsonElement("{}")))
        assertEquals(
            "из сообщения",
            extractErrorText(Http.json.parseToJsonElement("""{"message":"из сообщения"}""")),
        )
        assertEquals(
            "из описания",
            extractErrorText(Http.json.parseToJsonElement("""{"description":"из описания"}""")),
        )
        assertEquals(
            "строка",
            extractErrorText(Http.json.parseToJsonElement(""""строка"""")),
        )
    }

    @Test
    fun `truncate keeps short body and cuts long one`() {
        assertEquals("короткий", Http.truncate("короткий"))
        val cut = Http.truncate("a".repeat(600))
        assertEquals(515, cut.length)
        assertTrue(cut.endsWith("..."))
    }
}
