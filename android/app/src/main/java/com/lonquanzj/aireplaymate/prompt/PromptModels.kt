package com.lonquanzj.aireplaymate.prompt

enum class ContextSendPolicy {
    FULL_CONTEXT,
    LATEST_FRIEND_MESSAGE
}

data class AppSettings(
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/",
    val model: String = "gpt-4o-mini",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 300,
    val customSystemPrompt: String? = null,
    val candidateCount: Int = 3,
    val contextSendPolicy: ContextSendPolicy = ContextSendPolicy.FULL_CONTEXT
)

data class LlmRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val temperature: Float,
    val maxTokens: Int,
    val candidateCount: Int
)

data class ReplyCandidate(
    val id: String,
    val text: String,
    val tone: String? = null,
    val sourceModel: String? = null,
    val rank: Int
)
