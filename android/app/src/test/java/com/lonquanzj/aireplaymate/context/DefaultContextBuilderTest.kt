package com.lonquanzj.aireplaymate.context

import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.accessibility.MessageSource
import com.lonquanzj.aireplaymate.chatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(ChatRole.UNKNOWN, context.messages.single().role)
        assertEquals(MessageSource.ACCESSIBILITY, context.messages.single().source)
        assertEquals("你好", context.messages.single().content)
        assertTrue(context.enoughForReply)
    }

    @Test
    fun build_keeps_ocr_duplicates_and_keeps_last_20_messages() {
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
        assertEquals("消息6", context.messages.first().content)
        assertEquals("重复消息", context.messages[18].content)
        assertEquals(0.2f, context.messages[18].confidence, 0.0001f)
        assertEquals("重复消息", context.messages.last().content)
        assertEquals(0.8f, context.messages.last().confidence, 0.0001f)
    }

    @Test
    fun build_keeps_unknown_accessibility_messages_as_reply_context() {
        val context = DefaultContextBuilder.build(
            accessibilityMessages = (0 until 11).map { index ->
                chatMessage(
                    id = "a$index",
                    role = ChatRole.UNKNOWN,
                    content = "聊天内容$index",
                    confidence = 0.45f
                )
            },
            targetApp = "wechat",
            conversationType = ConversationType.SINGLE_CHAT
        )

        assertEquals(11, context.messages.size)
        assertTrue(context.enoughForReply)
        assertEquals(ChatRole.UNKNOWN, context.messages.first().role)
    }

    @Test
    fun build_filters_accessibility_timestamp_noise_so_ocr_can_fallback() {
        val context = DefaultContextBuilder.build(
            accessibilityMessages = listOf(
                chatMessage(id = "time-1", role = ChatRole.UNKNOWN, content = "19:52"),
                chatMessage(id = "time-2", role = ChatRole.UNKNOWN, content = "下午 7:52"),
                chatMessage(id = "date-1", role = ChatRole.UNKNOWN, content = "今天 下午 7:52"),
                chatMessage(id = "badge-1", role = ChatRole.UNKNOWN, content = "99")
            ),
            targetApp = "wechat",
            conversationType = ConversationType.SINGLE_CHAT
        )

        assertEquals(0, context.messages.size)
        assertFalse(context.enoughForReply)
    }

    @Test
    fun build_keeps_normal_sentence_starting_with_today() {
        val context = DefaultContextBuilder.build(
            accessibilityMessages = listOf(
                chatMessage(id = "a1", role = ChatRole.FRIEND, content = "今天天气不错")
            ),
            targetApp = "wechat",
            conversationType = ConversationType.SINGLE_CHAT
        )

        assertEquals(1, context.messages.size)
        assertEquals("今天天气不错", context.messages.single().content)
    }

    @Test
    fun build_reports_context_filter_stats() {
        var stats = ContextBuildStats()
        DefaultContextBuilder.build(
            accessibilityMessages = listOf(
                chatMessage(id = "a-empty", role = ChatRole.FRIEND, content = "  "),
                chatMessage(id = "a-time", role = ChatRole.FRIEND, content = "19:52"),
                chatMessage(id = "a-system", role = ChatRole.SYSTEM, content = "系统提示"),
                chatMessage(id = "a-ok", role = ChatRole.FRIEND, content = "你好")
            ),
            ocrMessages = listOf(
                chatMessage(
                    id = "o-dup",
                    role = ChatRole.FRIEND,
                    content = "你好",
                    source = MessageSource.OCR
                ),
                chatMessage(
                    id = "o-ok",
                    role = ChatRole.FRIEND,
                    content = "在吗",
                    source = MessageSource.OCR
                )
            ),
            targetApp = "wechat",
            conversationType = ConversationType.SINGLE_CHAT,
            onStats = { stats = it }
        )

        assertEquals(4, stats.accessibilityInputCount)
        assertEquals(2, stats.ocrInputCount)
        assertEquals(1, stats.cleanedAccessibilityCount)
        assertEquals(1, stats.cleanedOcrCount)
        assertEquals(2, stats.mergedCount)
        assertEquals(1, stats.droppedEmptyCount)
        assertEquals(1, stats.droppedNoiseCount)
        assertEquals(1, stats.droppedSystemCount)
        assertEquals(1, stats.droppedCrossSourceDuplicateCount)
    }
}
