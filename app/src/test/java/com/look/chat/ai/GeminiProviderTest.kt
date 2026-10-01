package com.look.chat.ai

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class GeminiProviderTest {

    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/v1beta").toString()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun provider() = GeminiProvider(apiKey = "g-key", baseUrl = baseUrl)

    @Test
    fun `complete posts generateContent with api key header`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"candidates":[{"content":{"parts":[{"text":" Привет! "}],"role":"model"}}]}""",
            ),
        )

        val response = provider().complete(
            AiRequest("gemini-2.5-pro", listOf(AiMessage("user", "привет"))),
        )

        assertEquals("gemini-2.5-pro", response.model)
        assertEquals("gemini", response.provider)
        assertEquals("Привет!", response.content)

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/v1beta/models/gemini-2.5-pro:generateContent", recorded.path)
        assertEquals("g-key", recorded.getHeader("x-goog-api-key"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains(""""role":"user""""))
        assertTrue(body.contains(""""text":"привет""""))
        Unit
    }

    @Test
    fun `assistant role is renamed to model for gemini`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"ок"}]}}]}"""),
        )

        provider().complete(
            AiRequest(
                "gemini-2.5-pro",
                listOf(AiMessage("user", "в"), AiMessage("assistant", "а"), AiMessage("user", "б")),
            ),
        )

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        val body = recorded.body.readUtf8()
        assertTrue(body.contains(""""role":"model""""))
        assertFalse(body.contains("assistant"))
        Unit
    }

    @Test
    fun `parts of one candidate are concatenated`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"candidates":[{"content":{"parts":[{"text":"Первая "},{"text":"часть"}]}}]}""",
            ),
        )

        val response = provider().complete(
            AiRequest("gemini-2.5-pro", listOf(AiMessage("user", "привет"))),
        )
        assertEquals("Первая часть", response.content)
        Unit
    }

    @Test
    fun `generation config is sent only with positive max tokens`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"ок"}]}}]}"""),
        )

        GeminiProvider(apiKey = "g-key", baseUrl = baseUrl, maxTokens = 33)
            .complete(AiRequest("gemini-2.5-pro", listOf(AiMessage("user", "х"))))

        val body = server.takeRequest(2, TimeUnit.SECONDS)!!.body.readUtf8()
        assertTrue(body.contains(""""maxOutputTokens":33"""))
        Unit
    }

    @Test
    fun `block reason is reported as provider error`() = runBlocking {
        server.enqueue(
            MockResponse().setBody("""{"promptFeedback":{"blockReason":"SAFETY"}}"""),
        )
        val e = assertThrows(AiError::class.java) {
            runBlocking {
                provider().complete(AiRequest("gemini-2.5-pro", listOf(AiMessage("user", "х"))))
            }
        }
        assertTrue(e.message!!.contains("blockReason: SAFETY"))
        Unit
    }

    @Test
    fun `error message is extracted on http error`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"error":{"code":400,"message":"API key not valid"}}"""),
        )
        val e = assertThrows(AiError::class.java) {
            runBlocking {
                provider().complete(AiRequest("gemini-2.5-pro", listOf(AiMessage("user", "х"))))
            }
        }
        assertEquals(AiError.PROVIDER_ERROR, e.code)
        assertTrue(e.message!!.contains("HTTP 400"))
        assertTrue(e.message!!.contains("API key not valid"))
        Unit
    }

    @Test
    fun `empty candidates is an error`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"candidates":[]}"""))
        val e = assertThrows(AiError::class.java) {
            runBlocking {
                provider().complete(AiRequest("gemini-2.5-pro", listOf(AiMessage("user", "х"))))
            }
        }
        assertTrue(e.message!!.contains("не вернул текст"))
        Unit
    }

    @Test
    fun `supports known models and gemini prefix`() {
        val provider = provider()
        assertTrue(provider.supports("gemini-3.8-flash"))
        assertTrue(provider.supports("GEMINI-2.0-flash"))
        assertFalse(provider.supports("gpt-4o"))
    }
}
