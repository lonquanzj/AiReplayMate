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

    @Test
    fun parseCandidates_ignores_blank_missing_and_non_text_json_items() {
        val candidates = LlmResponseParser.parseCandidates(
            rawContent = """
                {
                  "candidates": [
                    {"text": "第一条"},
                    {"text": " "},
                    {"label": "缺少 text"},
                    42,
                    {"text": "'第二条'"}
                  ]
                }
            """.trimIndent(),
            requestedCount = 5,
            sourceModel = "gpt-test"
        )

        assertEquals(listOf("第一条", "第二条"), candidates.map { it.text })
        assertEquals(listOf(1, 2), candidates.map { it.rank })
    }

    @Test
    fun parseCandidates_limits_to_requested_count_after_deduplication() {
        val candidates = LlmResponseParser.parseCandidates(
            rawContent = """
                - 你好
                - “你好”
                - 在吗
                - 晚点聊
            """.trimIndent(),
            requestedCount = 2,
            sourceModel = "gpt-test"
        )

        assertEquals(listOf("你好", "在吗"), candidates.map { it.text })
    }
}
