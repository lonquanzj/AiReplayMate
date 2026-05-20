package com.lonquanzj.aireplaymate.accessibility

enum class ChatRole {
    ME,
    FRIEND,
    SYSTEM,
    UNKNOWN
}

enum class MessageSource {
    ACCESSIBILITY,
    OCR,
    MERGED
}

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String,
    val timestamp: Long?,
    val source: MessageSource,
    val confidence: Float,
    val boundsHint: String? = null
)
