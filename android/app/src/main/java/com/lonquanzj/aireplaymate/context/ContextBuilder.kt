package com.lonquanzj.aireplaymate.context

import com.lonquanzj.aireplaymate.accessibility.ChatMessage
import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.accessibility.MessageSource

interface ContextBuilder {
    fun build(
        accessibilityMessages: List<ChatMessage>,
        ocrMessages: List<ChatMessage> = emptyList(),
        targetApp: String,
        conversationType: ConversationType
    ): ChatContext
}

object DefaultContextBuilder : ContextBuilder {
    override fun build(
        accessibilityMessages: List<ChatMessage>,
        ocrMessages: List<ChatMessage>,
        targetApp: String,
        conversationType: ConversationType
    ): ChatContext {
        val merged = (accessibilityMessages + ocrMessages)
            .asSequence()
            .mapNotNull(::cleanMessage)
            .filter { it.role != ChatRole.SYSTEM }
            .fold(linkedMapOf<String, ChatMessage>()) { acc, message ->
                val key = normalizeContent(message.content)
                val existing = acc[key]
                acc[key] = when {
                    existing == null -> message
                    shouldReplace(existing, message) -> message
                    else -> existing
                }
                acc
            }
            .values
            .toList()
            .takeLast(MAX_CONTEXT_MESSAGES)

        return ChatContext(
            messages = merged,
            targetApp = targetApp,
            conversationType = conversationType,
            collectedAt = System.currentTimeMillis()
        )
    }

    private fun cleanMessage(message: ChatMessage): ChatMessage? {
        val content = message.content.trim()
        if (content.isEmpty()) return null

        return message.copy(
            role = normalizeRole(message.role),
            content = content,
            confidence = message.confidence.coerceIn(0f, 1f)
        )
    }

    private fun normalizeRole(role: ChatRole): ChatRole {
        return if (role == ChatRole.UNKNOWN) ChatRole.FRIEND else role
    }

    private fun shouldReplace(
        existing: ChatMessage,
        candidate: ChatMessage
    ): Boolean {
        if (existing.source == MessageSource.ACCESSIBILITY &&
            candidate.source != MessageSource.ACCESSIBILITY
        ) {
            return false
        }

        if (existing.source != MessageSource.ACCESSIBILITY &&
            candidate.source == MessageSource.ACCESSIBILITY
        ) {
            return true
        }

        return candidate.confidence > existing.confidence
    }

    private fun normalizeContent(content: String): String {
        return whitespaceRegex.replace(content.trim(), " ")
    }

    private const val MAX_CONTEXT_MESSAGES = 20
    private val whitespaceRegex = Regex("\\s+")
}
