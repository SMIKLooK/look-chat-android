package com.look.chat.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RequestParserTest {

    private fun parse(text: String) = RequestParser.parse(text)

    @Test
    fun `first word is model, rest is prompt`() {
        val parsed = parse("gpt привет как дела")
        assertEquals("gpt", parsed.model)
        assertEquals("привет как дела", parsed.prompt)
    }

    @Test
    fun `leading separators are skipped`() {
        val parsed = parse("  :: ,модель запрос")
        assertEquals("модель", parsed.model)
        assertEquals("запрос", parsed.prompt)
    }

    @Test
    fun `colon form splits model and prompt`() {
        val parsed = parse("модель:запрос хвост")
        assertEquals("модель", parsed.model)
        assertEquals("запрос хвост", parsed.prompt)
    }

    @Test
    fun `colon with space still splits`() {
        val parsed = parse("модель : запрос")
        assertEquals("модель", parsed.model)
        assertEquals("запрос", parsed.prompt)
    }

    @Test
    fun `trailing punctuation is trimmed from model`() {
        val parsed = parse("гпт, расскажи анекдот")
        assertEquals("гпт", parsed.model)
        assertEquals("расскажи анекдот", parsed.prompt)
    }

    @Test
    fun `blank text is rejected`() {
        val e = assertThrows(AiError::class.java) { parse("   ") }
        assertEquals(AiError.EMPTY_TEXT, e.code)
    }

    @Test
    fun `model without prompt is rejected`() {
        val e = assertThrows(AiError::class.java) { parse("гпт") }
        assertEquals(AiError.PROMPT_NOT_SPECIFIED, e.code)
    }

    @Test
    fun `model with only colon is rejected`() {
        val e = assertThrows(AiError::class.java) { parse("гпт:") }
        assertEquals(AiError.PROMPT_NOT_SPECIFIED, e.code)
    }

    @Test
    fun `text of separators only is rejected as missing model`() {
        val e = assertThrows(AiError::class.java) { parse(" : , . ") }
        assertEquals(AiError.MODEL_NOT_SPECIFIED, e.code)
    }
}
