package com.lonquanzj.aireplaymate.llm

import com.lonquanzj.aireplaymate.prompt.ReplyCandidate
import org.json.JSONArray
import org.json.JSONObject

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
        return runCatching {
            val root = JSONObject(content)
            val candidates = root.optJSONArray("candidates") ?: JSONArray()
            buildList {
                for (index in 0 until candidates.length()) {
                    val item = candidates.opt(index)
                    val text = when (item) {
                        is JSONObject -> item.optString("text")
                        is String -> item
                        else -> ""
                    }
                    if (text.isNotBlank()) add(text)
                }
            }
        }.getOrDefault(emptyList())
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

    private fun String.removeMarkdownFence(): String {
        val withoutPrefix = replace(Regex("^```(?:json)?\\s*", RegexOption.IGNORE_CASE), "")
        return withoutPrefix.replace(Regex("\\s*```$"), "").trim()
    }
}
