package com.lonquanzj.aireplaymate

import com.lonquanzj.aireplaymate.accessibility.ChatMessage
import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.accessibility.MessageSource
import com.lonquanzj.aireplaymate.context.ChatContext
import com.lonquanzj.aireplaymate.context.ConversationType

fun chatMessage(
    id: String,
    role: ChatRole = ChatRole.FRIEND,
    content: String,
    source: MessageSource = MessageSource.ACCESSIBILITY,
    confidence: Float = 0.9f,
    boundsHint: String? = null
): ChatMessage {
    return ChatMessage(
        id = id,
        role = role,
        content = content,
        timestamp = null,
        source = source,
        confidence = confidence,
        boundsHint = boundsHint
    )
}

fun chatContext(
    messages: List<ChatMessage>,
    targetApp: String = "wechat",
    conversationType: ConversationType = ConversationType.SINGLE_CHAT
): ChatContext {
    return ChatContext(
        messages = messages,
        targetApp = targetApp,
        conversationType = conversationType,
        collectedAt = 1_717_171_717L
    )
}
