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

class OpenAiProvidersTest {

    private lateinit var server: MockWebServer
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/v1").toString()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun okBody(content: String = " Привет! ") =
        """{"choices":[{"message":{"role":"assistant","content":"$content"}}]}"""

    @Test
    fun `openrouter resolves short name to full slug and posts chat request`() = runBlocking {
        server.enqueue(MockResponse().setBody(okBody()))

        val provider = OpenRouterProvider(
            apiKey = "test-key",
            baseUrl = baseUrl,
            models = listOf("anthropic/claude-sonnet-5", "openai/gpt-4o"),
        )
        val response = provider.complete(
            AiRequest("claude-sonnet-5", listOf(AiMessage("user", "привет"))),
        )

        assertEquals("anthropic/claude-sonnet-5", response.model)
        assertEquals("openrouter", response.provider)
        assertEquals("Привет!", response.content)

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
        assertEquals(OpenRouterProvider.APP_TITLE, recorded.getHeader("X-Title"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains(""""model":"anthropic/claude-sonnet-5""""))
        assertTrue(body.contains(""""role":"user""""))
        assertTrue(body.contains(""""content":"привет""""))
        assertFalse(body.contains("max_tokens"))
        Unit
    }

    @Test
    fun `openrouter keeps exact model and accepts any vendor slug`() = runBlocking {
        server.enqueue(MockResponse().setBody(okBody("ок")))

        val provider = OpenRouterProvider(apiKey = "k", baseUrl = baseUrl)
        val response = provider.complete(
            AiRequest("Vendor/Model-X", listOf(AiMessage("user", "текст"))),
        )

        assertEquals("vendor/model-x", response.model)
        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertTrue(recorded.body.readUtf8().contains(""""model":"vendor/model-x""""))
        Unit
    }

    @Test
    fun `openrouter supports rules`() {
        val provider = OpenRouterProvider(
            apiKey = "k",
            models = listOf("openai/gpt-4o", "openrouter/free"),
        )
        assertTrue(provider.supports("any/vendor-slug"))
        assertTrue(provider.supports("gpt-4o"))
        assertFalse(provider.supports("gpt-4o-mini"))
    }

    @Test
    fun `openrouter forwards max tokens to the request body`() = runBlocking {
        server.enqueue(MockResponse().setBody(okBody()))

        val provider = OpenRouterProvider(apiKey = "k", baseUrl = baseUrl, maxTokens = 77)
        provider.complete(AiRequest("openai/gpt-4o", listOf(AiMessage("user", "текст"))))

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertTrue(recorded.body.readUtf8().contains(""""max_tokens":77"""))
        Unit
    }

    @Test
    fun `openrouter error body becomes provider error with message`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(429)
                .setBody("""{"error":{"message":"Rate limit exceeded"}}"""),
        )
        val provider = OpenRouterProvider(apiKey = "k", baseUrl = baseUrl)

        val e = assertThrows(AiError::class.java) {
            runBlocking {
                provider.complete(AiRequest("openai/gpt-4o", listOf(AiMessage("user", "текст"))))
            }
        }
        assertEquals(AiError.PROVIDER_ERROR, e.code)
        assertTrue(e.message!!.contains("HTTP 429"))
        assertTrue(e.message!!.contains("Rate limit exceeded"))
        Unit
    }

    @Test
    fun `openrouter waf 403 gets dedicated hint`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"error":{"message":"${OpenRouterProvider.WAF_BLOCKED}"}}"""),
        )
        val provider = OpenRouterProvider(apiKey = "k", baseUrl = baseUrl)

        val e = assertThrows(AiError::class.java) {
            runBlocking {
                provider.complete(AiRequest("openai/gpt-4o", listOf(AiMessage("user", "текст"))))
            }
        }
        assertTrue(e.message!!.contains("WAF"))
        Unit
    }

    @Test
    fun `openrouter non-json success body reports invalid reply`() = runBlocking {
        server.enqueue(MockResponse().setBody("<html>boom</html>"))
        val provider = OpenRouterProvider(apiKey = "k", baseUrl = baseUrl)

        val e = assertThrows(AiError::class.java) {
            runBlocking {
                provider.complete(AiRequest("openai/gpt-4o", listOf(AiMessage("user", "текст"))))
            }
        }
        assertTrue(e.message!!.contains("некорректный ответ"))
        Unit
    }

    @Test
    fun `openrouter empty choices is an error`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[]}"""))
        val provider = OpenRouterProvider(apiKey = "k", baseUrl = baseUrl)

        val e = assertThrows(AiError::class.java) {
            runBlocking {
                provider.complete(AiRequest("openai/gpt-4o", listOf(AiMessage("user", "текст"))))
            }
        }
        assertTrue(e.message!!.contains("пустой список choices"))
        Unit
    }

    @Test
    fun `gptunnel resolves model case-insensitively and sends bearer`() = runBlocking {
        server.enqueue(MockResponse().setBody(okBody("Готово")))

        val provider = GptunnelProvider(apiKey = "t-key", baseUrl = baseUrl)
        val response = provider.complete(
            AiRequest("GPT-4O", listOf(AiMessage("user", "вопрос"))),
        )

        assertEquals("gpt-4o", response.model)
        assertEquals("gptunnel", response.provider)
        assertEquals("Готово", response.content)

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer t-key", recorded.getHeader("Authorization"))
        assertTrue(recorded.body.readUtf8().contains(""""model":"gpt-4o""""))
        Unit
    }

    @Test
    fun `gptunnel error body becomes provider error`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(500)
                .setBody("""{"error":{"message":"internal"}}"""),
        )
        val provider = GptunnelProvider(apiKey = "t-key", baseUrl = baseUrl)

        val e = assertThrows(AiError::class.java) {
            runBlocking {
                provider.complete(AiRequest("gpt-4o", listOf(AiMessage("user", "вопрос"))))
            }
        }
        assertEquals(AiError.PROVIDER_ERROR, e.code)
        assertTrue(e.message!!.contains("HTTP 500"))
        Unit
    }

    @Test
    fun `gptunnel supports only known models`() {
        val provider = GptunnelProvider(apiKey = "t-key", baseUrl = baseUrl)
        assertTrue(provider.supports("gpt-4o"))
        assertTrue(provider.supports("GPT-4O"))
        assertFalse(provider.supports("gemini-3.8-flash"))
    }
}
