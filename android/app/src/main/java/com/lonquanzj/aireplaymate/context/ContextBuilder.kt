package com.lonquanzj.aireplaymate.context

import com.lonquanzj.aireplaymate.accessibility.ChatMessage
import com.lonquanzj.aireplaymate.accessibility.ChatRole

data class ContextBuildStats(
    val accessibilityInputCount: Int = 0,
    val ocrInputCount: Int = 0,
    val cleanedAccessibilityCount: Int = 0,
    val cleanedOcrCount: Int = 0,
    val mergedCount: Int = 0,
    val droppedEmptyCount: Int = 0,
    val droppedNoiseCount: Int = 0,
    val droppedSystemCount: Int = 0,
    val droppedCrossSourceDuplicateCount: Int = 0
)

private data class MutableContextBuildStats(
    var accessibilityInputCount: Int = 0,
    var ocrInputCount: Int = 0,
    var cleanedAccessibilityCount: Int = 0,
    var cleanedOcrCount: Int = 0,
    var mergedCount: Int = 0,
    var droppedEmptyCount: Int = 0,
    var droppedNoiseCount: Int = 0,
    var droppedSystemCount: Int = 0,
    var droppedCrossSourceDuplicateCount: Int = 0
) {
    fun toImmutable(): ContextBuildStats {
        return ContextBuildStats(
            accessibilityInputCount = accessibilityInputCount,
            ocrInputCount = ocrInputCount,
            cleanedAccessibilityCount = cleanedAccessibilityCount,
            cleanedOcrCount = cleanedOcrCount,
            mergedCount = mergedCount,
            droppedEmptyCount = droppedEmptyCount,
            droppedNoiseCount = droppedNoiseCount,
            droppedSystemCount = droppedSystemCount,
            droppedCrossSourceDuplicateCount = droppedCrossSourceDuplicateCount
        )
    }
}

interface ContextBuilder {
    fun build(
        accessibilityMessages: List<ChatMessage>,
        ocrMessages: List<ChatMessage> = emptyList(),
        targetApp: String,
        conversationType: ConversationType,
        onStats: (ContextBuildStats) -> Unit = {}
    ): ChatContext
}

object DefaultContextBuilder : ContextBuilder {
    override fun build(
        accessibilityMessages: List<ChatMessage>,
        ocrMessages: List<ChatMessage>,
        targetApp: String,
        conversationType: ConversationType,
        onStats: (ContextBuildStats) -> Unit
    ): ChatContext {
        val stats = MutableContextBuildStats(
            accessibilityInputCount = accessibilityMessages.size,
            ocrInputCount = ocrMessages.size
        )

        val cleanedAccessibility = accessibilityMessages
            .asSequence()
            .mapNotNull { it.cleanMessage(stats) }
            .toList()
        stats.cleanedAccessibilityCount = cleanedAccessibility.size

        val accessibilityContentKeys = cleanedAccessibility
            .asSequence()
            .map { normalizeContent(it.content) }
            .toSet()

        val cleanedOcr = ocrMessages
            .asSequence()
            .mapNotNull { it.cleanMessage(stats) }
            .filterNot { message ->
                val duplicated = normalizeContent(message.content) in accessibilityContentKeys
                if (duplicated) {
                    stats.droppedCrossSourceDuplicateCount += 1
                }
                duplicated
            }
            .toList()
        stats.cleanedOcrCount = cleanedOcr.size

        val merged = (cleanedAccessibility + cleanedOcr)
            .takeLast(MAX_CONTEXT_MESSAGES)
        stats.mergedCount = merged.size

        val context = ChatContext(
            messages = merged,
            targetApp = targetApp,
            conversationType = conversationType,
            collectedAt = System.currentTimeMillis()
        )

        onStats(stats.toImmutable())
        return context
    }

    private fun ChatMessage.cleanMessage(stats: MutableContextBuildStats): ChatMessage? {
        val content = content.trim()
        if (content.isEmpty()) {
            stats.droppedEmptyCount += 1
            return null
        }
        if (content.isLikelyNonConversationNoise()) {
            stats.droppedNoiseCount += 1
            return null
        }
        if (role == ChatRole.SYSTEM) {
            stats.droppedSystemCount += 1
            return null
        }

        return copy(
            content = content,
            confidence = confidence.coerceIn(0f, 1f)
        )
    }


    private fun normalizeContent(content: String): String {
        return whitespaceRegex.replace(content.trim(), " ")
    }

    private fun String.isLikelyNonConversationNoise(): Boolean {
        val normalized = normalizeContent(this)
        return timestampRegex.matches(normalized) ||
            dateRegex.matches(normalized) ||
            badgeRegex.matches(normalized)
    }

    private const val MAX_CONTEXT_MESSAGES = 20
    private val timestampRegex = Regex(
        """^((凌晨|早上|上午|中午|下午|晚上)\s*)?\d{1,2}[:：]\d{2}$"""
    )
    private val dateRegex = Regex(
        """^((\d{4}年)?\d{1,2}月\d{1,2}日|周[一二三四五六日天]|星期[一二三四五六日天]|昨天|今天|前天)(\s*((凌晨|早上|上午|中午|下午|晚上)\s*)?\d{1,2}[:：]\d{2})?$"""
    )
    private val badgeRegex = Regex("""^\d{1,3}$""")
    private val whitespaceRegex = Regex("\\s+")
}
