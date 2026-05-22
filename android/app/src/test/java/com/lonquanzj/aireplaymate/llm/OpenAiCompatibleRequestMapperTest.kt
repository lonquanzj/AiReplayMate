package com.lonquanzj.aireplaymate.llm

import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.LlmRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiCompatibleRequestMapperTest {
    @Test
    fun chatCompletionsUrl_normalizes_openai_compatible_base_urls() {
        assertEquals(
            "https://api.example.test/v1/chat/completions",
            OpenAiCompatibleRequestMapper.chatCompletionsUrl(
                AppSettings(baseUrl = " https://api.example.test ")
            )
        )
        assertEquals(
            "https://api.example.test/v1/chat/completions",
            OpenAiCompatibleRequestMapper.chatCompletionsUrl(
                AppSettings(baseUrl = "https://api.example.test/v1/")
            )
        )
        assertEquals(
            "https://api.example.test/custom/chat/completions",
            OpenAiCompatibleRequestMapper.chatCompletionsUrl(
                AppSettings(baseUrl = "https://api.example.test/custom/chat/completions")
            )
        )
    }

    @Test
    fun toOpenAiJson_maps_model_sampling_and_two_message_prompt() {
        val json = OpenAiCompatibleRequestMapper.toOpenAiJson(
            request = LlmRequest(
                systemPrompt = "system rules",
                userPrompt = "user context",
                temperature = 0.8f,
                maxTokens = 180,
                candidateCount = 3
            ),
            model = "gpt-test"
        )

        val messages = json.getJSONArray("messages")

        assertEquals("gpt-test", json.getString("model"))
        assertEquals(0.8, json.getDouble("temperature"), 0.0001)
        assertEquals(180, json.getInt("max_tokens"))
        assertEquals(2, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("system rules", messages.getJSONObject(0).getString("content"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals("user context", messages.getJSONObject(1).getString("content"))
    }
}
