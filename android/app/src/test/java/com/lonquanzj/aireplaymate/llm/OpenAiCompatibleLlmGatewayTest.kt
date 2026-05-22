package com.lonquanzj.aireplaymate.llm

import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.LlmRequest
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OpenAiCompatibleLlmGatewayTest {
    private var server: FakeHttpServer? = null

    @After
    fun tearDown() {
        server?.close()
        server = null
    }

    @Test
    fun generateReplies_fails_with_http_category_for_non_2xx_response() = runTest {
        server = FakeHttpServer(status = 401, body = """{"error":"bad key"}""")

        val result = newGateway().generateReplies(defaultRequest())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("HTTP 401"))
        assertEquals(LlmDebugPhase.FAILED, LlmDebugStore.state.value.phase)
        assertEquals(LlmFailureCategory.HTTP, LlmDebugStore.state.value.failureCategory)
        assertEquals(401, LlmDebugStore.state.value.httpStatus)
    }

    @Test
    fun generateReplies_fails_with_parse_category_when_response_shape_is_invalid() = runTest {
        server = FakeHttpServer(status = 200, body = """{"choices":[]}""")

        val result = newGateway().generateReplies(defaultRequest())

        assertTrue(result.isFailure)
        assertEquals(LlmDebugPhase.FAILED, LlmDebugStore.state.value.phase)
        assertEquals(LlmFailureCategory.PARSE, LlmDebugStore.state.value.failureCategory)
    }

    @Test
    fun generateReplies_fails_when_model_returns_fewer_candidates_than_requested() = runTest {
        server = FakeHttpServer(
            status = 200,
            body = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\"candidates\":[{\"text\":\"one\"}]}"
                      }
                    }
                  ]
                }
            """.trimIndent()
        )

        val result = newGateway().generateReplies(defaultRequest(candidateCount = 2))

        assertTrue(result.isFailure)
        assertEquals(LlmDebugPhase.FAILED, LlmDebugStore.state.value.phase)
        assertEquals(LlmFailureCategory.INSUFFICIENT_CANDIDATES, LlmDebugStore.state.value.failureCategory)
    }

    @Test
    fun generateReplies_sends_authorization_header_and_parses_candidates() = runTest {
        server = FakeHttpServer(
            status = 200,
            body = """
                {
                  "choices": [
                    {
                      "message": {
                        "content": "{\"candidates\":[{\"text\":\"one\"},{\"text\":\"two\"}]}"
                      }
                    }
                  ]
                }
            """.trimIndent()
        )

        val result = newGateway().generateReplies(defaultRequest(candidateCount = 2))

        assertTrue(result.isSuccess)
        assertEquals(listOf("one", "two"), result.getOrThrow().map { it.text })
        assertEquals("Bearer sk-test", server!!.authorizationHeader)
        assertTrue(server!!.requestBody.contains("\"model\":\"gpt-test\""))
        assertEquals(LlmDebugPhase.PARSED, LlmDebugStore.state.value.phase)
        assertEquals(2, LlmDebugStore.state.value.candidateCount)
    }

    private fun newGateway(): OpenAiCompatibleLlmGateway {
        return OpenAiCompatibleLlmGateway(
            AppSettings(
                apiKey = "sk-test",
                baseUrl = "http://127.0.0.1:${server!!.port}/custom",
                model = "gpt-test"
            )
        )
    }

    private fun defaultRequest(candidateCount: Int = 1): LlmRequest {
        return LlmRequest(
            systemPrompt = "system",
            userPrompt = "user",
            temperature = 0.7f,
            maxTokens = 120,
            candidateCount = candidateCount
        )
    }

    private class FakeHttpServer(
        private val status: Int,
        private val body: String
    ) : AutoCloseable {
        private val socket = ServerSocket(0)
        private val worker: Thread
        val port: Int = socket.localPort
        @Volatile
        var authorizationHeader: String = ""
            private set
        @Volatile
        var requestBody: String = ""
            private set

        init {
            worker = thread(start = true, isDaemon = true) {
                socket.accept().use(::handle)
            }
        }

        private fun handle(client: Socket) {
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            var contentLength = 0
            generateSequence { reader.readLine() }
                .takeWhile { it.isNotEmpty() }
                .forEach { line ->
                    if (line.startsWith("Authorization:", ignoreCase = true)) {
                        authorizationHeader = line.substringAfter(":").trim()
                    }
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(":").trim().toInt()
                    }
                }
            if (contentLength > 0) {
                val chars = CharArray(contentLength)
                reader.read(chars)
                requestBody = String(chars)
            }

            val bytes = body.toByteArray(Charsets.UTF_8)
            client.getOutputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write("HTTP/1.1 $status Test\r\n")
                writer.write("Content-Type: application/json; charset=utf-8\r\n")
                writer.write("Content-Length: ${bytes.size}\r\n")
                writer.write("Connection: close\r\n")
                writer.write("\r\n")
                writer.flush()
                client.getOutputStream().write(bytes)
                client.getOutputStream().flush()
            }
        }

        override fun close() {
            runCatching { socket.close() }
            runCatching { worker.join(1_000) }
        }
    }
}
