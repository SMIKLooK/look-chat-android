package com.look.chat.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBackendTest {

    private class EchoProvider(
        override val name: String = "echo",
        override val models: List<String> = listOf("echo"),
    ) : AiProvider {
        override fun supports(model: String): Boolean = models.any { it.equals(model, true) }
        override suspend fun complete(request: AiRequest): AiResponse =
            AiResponse(request.model, name, "эхо: " + request.messages.single().content)
    }

    private fun backend(
        providers: List<AiProvider> = listOf(EchoProvider()),
        aliases: Map<String, String> = mapOf("гемини" to "echo", "фри" to "echo"),
    ): LocalBackend {
        val registry = ProviderRegistry(providers)
        registry.setAliases(aliases)
        return LocalBackend(registry)
    }

    @Test
    fun `process parses text, resolves alias and returns answer`() = runBlocking {
        val result = backend().process("гемини расскажи анекдот")

        assertEquals("echo", result.model)
        assertEquals("echo", result.provider)
        assertEquals("эхо: расскажи анекдот", result.answer)
        assertTrue(result.elapsedMs >= 0)
        Unit
    }

    @Test
    fun `process sends user message with prompt`() = runBlocking {
        var captured: AiRequest? = null
        val provider = object : AiProvider {
            override val name = "echo"
            override val models = listOf("echo")
            override fun supports(model: String) = model == "echo"
            override suspend fun complete(request: AiRequest): AiResponse {
                captured = request
                return AiResponse(request.model, name, "ок")
            }
        }

        backend(providers = listOf(provider)).process("echo вопрос из двух слов")

        val request = captured!!
        assertEquals("echo", request.model)
        assertEquals(1, request.messages.size)
        assertEquals("user", request.messages.single().role)
        assertEquals("вопрос из двух слов", request.messages.single().content)
        Unit
    }

    @Test
    fun `empty text is rejected before touching providers`() {
        val e = assertThrows(AiError::class.java) {
            runBlocking { backend().process("   ") }
        }
        assertEquals(AiError.EMPTY_TEXT, e.code)
    }

    @Test
    fun `unknown model is rejected with code`() {
        val e = assertThrows(AiError::class.java) {
            runBlocking { backend().process("нет-такой привет") }
        }
        assertEquals(AiError.UNKNOWN_MODEL, e.code)
    }

    @Test
    fun `registry without providers explains where to add keys`() {
        val e = assertThrows(AiError::class.java) {
            runBlocking { backend(providers = emptyList()).process("фри привет") }
        }
        assertEquals(AiError.PROVIDER_ERROR, e.code)
        assertTrue(e.message!!.contains("настройки"))
    }

    @Test
    fun `defaultRegistry user key overrides builtin and empty builtin is filled`() {
        val saved = listOf(Keys.Gemini, Keys.Gptunnel, Keys.OpenRouter)
        Keys.Gemini = ""
        Keys.Gptunnel = "builtin-gptunnel"
        Keys.OpenRouter = ""
        try {
            val registry = LocalBackend.defaultRegistry(
                mapOf("openrouter" to "user-key", "gptunnel" to "  ", "gemini" to "user-gemini"),
            )
            assertEquals(listOf("gemini", "gptunnel", "openrouter"), registry.providers.map { it.name })
        } finally {
            Keys.Gemini = saved[0]
            Keys.Gptunnel = saved[1]
            Keys.OpenRouter = saved[2]
        }
    }

    @Test
    fun `defaultRegistry registers only providers with keys, openrouter last`() {
        val saved = listOf(Keys.Gemini, Keys.Gptunnel, Keys.OpenRouter)
        Keys.Gemini = "g"
        Keys.Gptunnel = ""
        Keys.OpenRouter = "o"
        try {
            val registry = LocalBackend.defaultRegistry()
            assertEquals(listOf("gemini", "openrouter"), registry.providers.map { it.name })

            val (provider, model) = registry.resolve("фри")
            assertEquals("openrouter", provider.name)
            assertEquals("openrouter/free", model)

            val (gemini, geminiModel) = registry.resolve("гемини")
            assertEquals("gemini", gemini.name)
            assertEquals("gemini-3.6-flash", geminiModel)
        } finally {
            Keys.Gemini = saved[0]
            Keys.Gptunnel = saved[1]
            Keys.OpenRouter = saved[2]
        }
    }

    @Test
    fun `defaultRegistry without keys has no providers`() {
        val saved = listOf(Keys.Gemini, Keys.Gptunnel, Keys.OpenRouter)
        Keys.Gemini = ""
        Keys.Gptunnel = ""
        Keys.OpenRouter = ""
        try {
            val registry = LocalBackend.defaultRegistry()
            assertEquals(emptyList<String>(), registry.providers.map { it.name })
        } finally {
            Keys.Gemini = saved[0]
            Keys.Gptunnel = saved[1]
            Keys.OpenRouter = saved[2]
        }
    }
}
