package com.lonquanzj.aireplaymate.context

import com.lonquanzj.aireplaymate.accessibility.ChatMessage
import com.lonquanzj.aireplaymate.accessibility.ChatRole

enum class ConversationType {
    SINGLE_CHAT,
    GROUP_CHAT,
    UNKNOWN
}

data class ChatContext(
    val messages: List<ChatMessage>,
    val targetApp: String,
    val conversationType: ConversationType,
    val collectedAt: Long
) {
    val enoughForReply: Boolean
        get() = messages.any { it.role == ChatRole.FRIEND } && messages.isNotEmpty()

    val isLowConfidence: Boolean
        get() = messages.size < RECOMMENDED_MESSAGE_COUNT ||
            messages.any { it.confidence < LOW_CONFIDENCE_THRESHOLD }

    companion object {
        private const val RECOMMENDED_MESSAGE_COUNT = 3
        private const val LOW_CONFIDENCE_THRESHOLD = 0.5f
    }
}
