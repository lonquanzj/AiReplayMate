package com.lonquanzj.aireplaymate.context

import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.accessibility.MessageSource
import com.lonquanzj.aireplaymate.chatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultContextBuilderTest {
    @Test
    fun build_filters_empty_and_system_messages_and_prefers_accessibility_duplicates() {
        val context = DefaultContextBuilder.build(
            accessibilityMessages = listOf(
                chatMessage(id = "a1", role = ChatRole.UNKNOWN, content = "  你好  ", confidence = 0.4f),
                chatMessage(id = "a2", role = ChatRole.SYSTEM, content = "以上是打招呼的内容"),
                chatMessage(id = "a3", role = ChatRole.FRIEND, content = "   ")
            ),
            ocrMessages = listOf(
                chatMessage(
                    id = "o1",
                    role = ChatRole.FRIEND,
                    content = "你好",
                    source = MessageSource.OCR,
                    confidence = 0.98f
                )
            ),
            targetApp = "wechat",
            conversationType = ConversationType.SINGLE_CHAT
        )

        assertEquals(1, context.messages.size)
        assertEquals(ChatRole.FRIEND, context.messages.single().role)
        assertEquals(MessageSource.ACCESSIBILITY, context.messages.single().source)
        assertEquals("你好", context.messages.single().content)
        assertTrue(context.enoughForReply)
    }

    @Test
    fun build_prefers_higher_confidence_for_same_source_and_keeps_last_20_messages() {
        val accessibilityMessages = (0 until 24).map { index ->
            chatMessage(id = "m$index", role = ChatRole.FRIEND, content = "消息$index", confidence = 0.4f)
        }
        val ocrMessages = listOf(
            chatMessage(
                id = "ocr-low",
                role = ChatRole.FRIEND,
                content = "重复消息",
                source = MessageSource.OCR,
                confidence = 0.2f
            ),
            chatMessage(
                id = "ocr-high",
                role = ChatRole.FRIEND,
                content = "重复消息",
                source = MessageSource.OCR,
                confidence = 0.8f
            )
        )

        val context = DefaultContextBuilder.build(
            accessibilityMessages = accessibilityMessages,
            ocrMessages = ocrMessages,
            targetApp = "wechat",
            conversationType = ConversationType.SINGLE_CHAT
        )

        assertEquals(20, context.messages.size)
        assertEquals("消息5", context.messages.first().content)
        assertEquals("重复消息", context.messages.last().content)
        assertEquals(0.8f, context.messages.last().confidence, 0.0001f)
    }
}
