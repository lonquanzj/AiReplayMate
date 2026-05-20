package com.lonquanzj.aireplaymate.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class LlmResponseParserTest {
    @Test
    fun parseCandidates_handles_fenced_json_and_deduplicates_trimmed_texts() {
        val candidates = LlmResponseParser.parseCandidates(
            rawContent = """
                ```json
                {
                  "candidates": [
                    {"text": " 你好呀 "},
                    {"text": "“你好呀”"},
                    "在吗"
                  ]
                }
                ```
            """.trimIndent(),
            requestedCount = 3,
            sourceModel = "gpt-test"
        )

        assertEquals(listOf("你好呀", "在吗"), candidates.map { it.text })
        assertEquals(listOf(1, 2), candidates.map { it.rank })
        assertEquals(listOf("gpt-test", "gpt-test"), candidates.map { it.sourceModel })
    }

    @Test
    fun parseCandidates_falls_back_to_plain_text_when_json_is_invalid() {
        val candidates = LlmResponseParser.parseCandidates(
            rawContent = """
                ```json
                1. 你好
                2. 在吗
                3. 你好
                ```
            """.trimIndent(),
            requestedCount = 2,
            sourceModel = "gpt-test"
        )

        assertEquals(listOf("你好", "在吗"), candidates.map { it.text })
    }
}
