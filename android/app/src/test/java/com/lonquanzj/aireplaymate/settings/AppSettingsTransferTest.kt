package com.lonquanzj.aireplaymate.settings

import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.ContextSendPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTransferTest {
    @Test
    fun encode_and_decode_round_trips_llm_settings() {
        val settings = AppSettings(
            apiKey = "sk-test",
            baseUrl = "https://example.test/v1/",
            model = "gpt-test",
            temperature = 1.1f,
            maxTokens = 640,
            contextSendPolicy = ContextSendPolicy.LATEST_FRIEND_MESSAGE
        )

        val decoded = AppSettingsTransfer.decode(AppSettingsTransfer.encode(settings)).getOrThrow()

        assertEquals(settings.apiKey, decoded.apiKey)
        assertEquals(settings.baseUrl, decoded.baseUrl)
        assertEquals(settings.model, decoded.model)
        assertEquals(settings.temperature, decoded.temperature, 0.0001f)
        assertEquals(settings.maxTokens, decoded.maxTokens)
        assertEquals(settings.contextSendPolicy, decoded.contextSendPolicy)
    }

    @Test
    fun decode_clamps_numeric_values_and_defaults_unknown_policy() {
        val decoded = AppSettingsTransfer.decode(
            """
            {
              "apiKey": "sk-test",
              "baseUrl": "",
              "model": "",
              "temperature": 9.0,
              "maxTokens": 40,
              "contextSendPolicy": "REMOVED"
            }
            """.trimIndent()
        ).getOrThrow()
        val defaults = AppSettings()

        assertEquals("sk-test", decoded.apiKey)
        assertEquals(defaults.baseUrl, decoded.baseUrl)
        assertEquals(defaults.model, decoded.model)
        assertEquals(2f, decoded.temperature, 0.0001f)
        assertEquals(120, decoded.maxTokens)
        assertEquals(defaults.contextSendPolicy, decoded.contextSendPolicy)
    }

    @Test
    fun decode_returns_failure_for_invalid_json() {
        assertTrue(AppSettingsTransfer.decode("not-json").isFailure)
    }
}
