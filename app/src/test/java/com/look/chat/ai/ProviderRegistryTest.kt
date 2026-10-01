package com.look.chat.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {

    private class StubProvider(
        override val name: String,
        override val models: List<String>,
    ) : AiProvider {
        override fun supports(model: String): Boolean = models.any { it.equals(model, ignoreCase = true) }
        override suspend fun complete(request: AiRequest): AiResponse =
            AiResponse(request.model, name, "ответ ${name}")
    }

    private class CatchAll(override val name: String) : AiProvider {
        override val models: List<String> = emptyList()
        override fun supports(model: String): Boolean = model.contains('/')
        override suspend fun complete(request: AiRequest): AiResponse =
            AiResponse(request.model, name, "ответ ${name}")
    }

    @Test
    fun `alias applies case-insensitively and returns canonical model`() {
        val registry = ProviderRegistry(listOf(StubProvider("gemini", listOf("gemini-3.6-flash"))))
        registry.setAliases(mapOf("Гемини" to "gemini-3.6-flash"))

        val (provider, model) = registry.resolve("геМини")

        assertEquals("gemini", provider.name)
        assertEquals("gemini-3.6-flash", model)
    }

    @Test
    fun `unknown model throws with code`() {
        val registry = ProviderRegistry(listOf(StubProvider("gemini", listOf("gemini-3.6-flash"))))

        val e = assertThrows(AiError::class.java) { registry.resolve("gpt-4o") }
        assertEquals(AiError.UNKNOWN_MODEL, e.code)
        assertTrue(e.message!!.contains("gpt-4o"))
    }

    @Test
    fun `unknown alias target reports user token like backend`() {
        val registry = ProviderRegistry(listOf(StubProvider("gemini", listOf("gemini-3.6-flash"))))
        registry.setAliases(mapOf("фри" to "openrouter/free"))

        val e = assertThrows(AiError::class.java) { registry.resolve("фри") }
        assertTrue(e.message!!.contains("фри"))
    }

    @Test
    fun `first registered provider wins, catch-all stays last`() {
        val registry = ProviderRegistry(
            listOf(
                StubProvider("gemini", listOf("gemini-3.6-flash")),
                CatchAll("openrouter"),
            ),
        )

        val (direct, directModel) = registry.resolve("gemini-3.6-flash")
        assertEquals("gemini", direct.name)

        val (catchAll, catchAllModel) = registry.resolve("vendor/model-x")
        assertEquals("openrouter", catchAll.name)
        assertEquals("vendor/model-x", catchAllModel)
    }

    @Test
    fun `re-registering same name replaces implementation`() {
        val registry = ProviderRegistry()
        registry.register(StubProvider("gemini", listOf("gemini-3.6-flash")))
        registry.register(StubProvider("gemini", listOf("gemini-3.8-flash")))

        assertEquals(1, registry.providers.size)
        assertEquals(listOf("gemini-3.8-flash"), registry.providers.single().models)
    }

    @Test
    fun `alias keys are lowercased on set`() {
        val registry = ProviderRegistry(listOf(StubProvider("echo", listOf("Echo-Model"))))
        registry.setAliases(mapOf("ЭхоМодель" to "Echo-Model"))

        val (_, model) = registry.resolve("эхомодель")
        assertEquals("Echo-Model", model)
    }
}
