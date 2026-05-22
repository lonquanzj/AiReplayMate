package com.lonquanzj.aireplaymate.llm

import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmDebugStoreTest {
    @Test
    fun records_request_http_parse_and_compacts_large_previews() {
        val previousHistorySize = LlmDebugStore.state.value.history.size

        LlmDebugStore.onRequestStarted(
            baseUrl = "https://api.example.test/v1/chat/completions",
            model = "gpt-test",
            requestPreview = "system\n\nuser     prompt"
        )
        LlmDebugStore.onHttpReturned(
            status = 200,
            responseText = "x".repeat(260)
        )
        LlmDebugStore.onParsed(
            candidateCount = 2,
            contentPreview = "ok"
        )

        val state = LlmDebugStore.state.value

        assertEquals(LlmDebugPhase.PARSED, state.phase)
        assertEquals(2, state.candidateCount)
        assertEquals("ok", state.responsePreview)
        assertEquals(LlmFailureCategory.NONE, state.failureCategory)
        assertEquals((previousHistorySize + 1).coerceAtMost(8), state.history.size)
        assertEquals(LlmDebugPhase.PARSED, state.history.first().phase)
        assertTrue(state.requestPreview!!.contains("system user prompt"))
    }

    @Test
    fun onFailed_classifies_timeout_as_network_and_keeps_history_bounded() {
        repeat(10) { index ->
            LlmDebugStore.onRequestStarted(
                baseUrl = "https://api.example.test/v1/chat/completions",
                model = "gpt-test",
                requestPreview = "request $index"
            )
            LlmDebugStore.onFailed(SocketTimeoutException("timeout $index"))
        }

        val state = LlmDebugStore.state.value

        assertEquals(LlmDebugPhase.FAILED, state.phase)
        assertEquals(LlmFailureCategory.NETWORK, state.failureCategory)
        assertEquals("timeout 9", state.errorSummary)
        assertEquals(8, state.history.size)
        assertEquals("timeout 9", state.history.first().summary)
    }
}
