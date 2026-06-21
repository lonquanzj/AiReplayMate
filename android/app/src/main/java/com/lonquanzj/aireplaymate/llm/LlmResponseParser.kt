package com.lonquanzj.aireplaymate.llm

import com.lonquanzj.aireplaymate.prompt.ReplyCandidate
import org.json.JSONObject
import org.json.JSONTokener

object LlmResponseParser {
    fun parseCandidates(
        rawContent: String,
        requestedCount: Int,
        sourceModel: String
    ): List<ReplyCandidate> {
        val cleaned = rawContent
            .trim()
            .removeMarkdownFence()

        val texts = parseJsonCandidates(cleaned)
            .ifEmpty { parsePlainTextCandidates(cleaned) }
            .map { it.trim().trim('"', '\'', '“', '”') }
            .filter { it.isNotBlank() }
            .distinct()
            .take(requestedCount)

        return texts.mapIndexed { index, text ->
            ReplyCandidate(
                id = "llm_${sourceModel}_${index}_${text.hashCode().toUInt().toString(16)}",
                text = text,
                sourceModel = sourceModel,
                rank = index + 1
            )
        }
    }

    private fun parseJsonCandidates(content: String): List<String> {
        // 尝试标准 JSON 解析
        val jsonContent = content.findJsonObjectText()
        if (jsonContent != null) {
            val root = parseJsonObject(jsonContent)
            if (root != null) {
                val candidates = root.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val result = buildList {
                        for (index in 0 until candidates.length()) {
                            val item = candidates.opt(index)
                            val text = when (item) {
                                is JSONObject -> item.extractCandidateText()
                                is String -> item
                                else -> ""
                            }
                            if (text.isNotBlank()) add(text)
                        }
                    }
                    if (result.isNotEmpty()) return result
                }
            }
        }

        // 兜底：用正则从畸形 JSON 中提取 text 字段值（应对缺 } 等语法错误）
        return extractTextFieldValues(content)
    }

    /**
     * 当 LLM 返回的 JSON 结构不完整时（如缺闭合括号），
     * 直接用正则提取所有 "text":"..." (或 content/reply/message) 的值。
     */
    private fun extractTextFieldValues(content: String): List<String> {
        val normalized = content.normalizeJsonQuotes()
        val regex = Regex(""""(?:text|content|reply|message)"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        return regex.findAll(normalized)
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun parsePlainTextCandidates(content: String): List<String> {
        return content
            .lineSequence()
            .map { line ->
                line.trim()
                    .replace(Regex("^[-*\\d.、)）\\s]+"), "")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun JSONObject.extractCandidateText(): String {
        return sequenceOf("text", "content", "reply", "message")
            .map { key -> optString(key) }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    private fun String.removeMarkdownFence(): String {
        val withoutPrefix = replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
        return withoutPrefix.replace(Regex("\\s*```$"), "").trim()
    }

    private fun String.findJsonObjectText(): String? {
        val decoded = decodeJsonStringLiteralIfNeeded()
        val start = decoded.indexOf('{')
        val end = decoded.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return decoded.substring(start, end + 1)
    }

    private fun String.decodeJsonStringLiteralIfNeeded(): String {
        val trimmed = trim()
        if (!trimmed.startsWith('"') || !trimmed.endsWith('"')) return this
        return runCatching {
            val value = JSONTokener(trimmed).nextValue()
            if (value is String) value.trim() else this
        }.getOrDefault(this)
    }

    private fun parseJsonObject(content: String): JSONObject? {
        return runCatching { JSONObject(content) }
            .recoverCatching { JSONObject(content.normalizeJsonQuotes()) }
            .getOrNull()
    }

    private fun String.normalizeJsonQuotes(): String {
        return replace('“', '"')
            .replace('”', '"')
    }
}
