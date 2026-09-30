package com.look.chat.data

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class LookApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: LookApi
    private lateinit var baseUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = LookApi()
        baseUrl = server.url("/").toString()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `process posts json envelope to process endpoint`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"status":"ok","model":"gemini-3.6-flash","provider":"google",
                    "answer":"Привет!","elapsed_ms":42}""",
            ),
        )

        val response = api.process(baseUrl, "gemini привет")

        assertEquals("ok", response.status)
        assertEquals("gemini-3.6-flash", response.model)
        assertEquals("google", response.provider)
        assertEquals("Привет!", response.answer)
        assertEquals(42L, response.elapsedMs)

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/process", recorded.path)
        assertEquals("POST", recorded.method)
        assertEquals("""{"text":"gemini привет"}""", recorded.body.readUtf8())
        assertTrue(recorded.getHeader("Content-Type")!!.startsWith("application/json"))
    }

    @Test
    fun `process parses error envelope without throwing`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"status":"error","error":{"code":"KEYWORD_NOT_FOUND","message":"нет такой модели"}}""",
            ),
        )

        val response = api.process(baseUrl, "кто ты")

        assertEquals("error", response.status)
        assertEquals("KEYWORD_NOT_FOUND", response.error?.code)
        assertEquals("нет такой модели", response.error?.message)
    }

    @Test
    fun `process with non-json error body reports http code`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("<html>boom</html>"))
        try {
            api.process(baseUrl, "привет")
            throw AssertionError("ожидался IOException")
        } catch (e: IOException) {
            assertEquals("HTTP 500", e.message)
        }
    }

    @Test
    fun `process with non-json success body rethrows parse error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("не json"))
        try {
            api.process(baseUrl, "привет")
            throw AssertionError("ожидалась ошибка разбора")
        } catch (e: IOException) {
            throw AssertionError("на 200 разбор должен упасть исходной ошибкой, а не HTTP-кодом", e)
        } catch (_: kotlinx.serialization.SerializationException) {
            // ожидаемо
        }
    }

    @Test
    fun `models fetches models endpoint`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"keywords":["старт"],
                    "aliases":{"фри":"openrouter/free"},
                    "providers":[{"name":"echo","models":["echo"]}]}""",
            ),
        )

        val response = api.models(baseUrl)

        assertEquals(listOf("старт"), response.keywords)
        assertEquals(mapOf("фри" to "openrouter/free"), response.aliases)
        assertEquals(listOf("echo"), response.providers.single().models)

        val recorded = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/models", recorded.path)
        assertEquals("GET", recorded.method)
    }

    @Test
    fun `models with http error throws with code`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        try {
            api.models(baseUrl)
            throw AssertionError("ожидался IOException")
        } catch (e: IOException) {
            assertEquals("HTTP 404", e.message)
        }
    }

    @Test
    fun `unknown fields in responses are ignored`() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"status":"ok","answer":"ок","future_field":{"a":1}}""",
            ),
        )
        val response = api.process(baseUrl, "текст")
        assertEquals("ок", response.answer)
    }

    @Test
    fun `urls are built with trailing slash tolerance`() {
        assertEquals("http://host:8080/api/v1/process", LookApi.processUrl("http://host:8080"))
        assertEquals("http://host:8080/api/v1/process", LookApi.processUrl("http://host:8080/"))
        assertEquals("http://host:8080/api/v1/models", LookApi.modelsUrl("http://host:8080/"))
    }
}
