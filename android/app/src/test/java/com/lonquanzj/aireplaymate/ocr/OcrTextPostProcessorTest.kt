package com.lonquanzj.aireplaymate.ocr

import com.lonquanzj.aireplaymate.accessibility.ChatRole
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OcrTextPostProcessorTest {
    @Test
    fun toChatMessages_filters_noise_merges_neighbor_lines_and_infers_roles() {
        val result = OcrTextPostProcessor.toChatMessages(
            lines = listOf(
                OcrRecognizedLine("微信", Rect(80, 30, 220, 90)),
                OcrRecognizedLine("10:32", Rect(460, 180, 560, 230)),
                OcrRecognizedLine("99", Rect(960, 210, 990, 240)),
                OcrRecognizedLine("在忙吗", Rect(60, 300, 360, 330)),
                OcrRecognizedLine("方便回我一下", Rect(70, 334, 430, 364)),
                OcrRecognizedLine("我刚看到", Rect(650, 520, 900, 550)),
                OcrRecognizedLine("我刚看到", Rect(652, 522, 902, 552)),
                OcrRecognizedLine("发送", Rect(830, 1760, 950, 1810))
            ),
            screenWidth = 1000,
            screenHeight = 2000
        )

        assertEquals(8, result.rawLineCount)
        assertEquals(3, result.keptLineCount)
        assertEquals(5, result.droppedLineCount)
        assertEquals(2, result.messages.size)
        assertEquals(ChatRole.FRIEND, result.messages[0].role)
        assertEquals("在忙吗\n方便回我一下", result.messages[0].content)
        assertEquals(ChatRole.ME, result.messages[1].role)
        assertEquals("我刚看到", result.messages[1].content)
    }

    @Test
    fun toChatMessages_keeps_only_last_20_messages() {
        val lines = (0 until 25).map { index ->
            val left = if (index % 2 == 0) 60 else 700
            val right = if (index % 2 == 0) 360 else 940
            OcrRecognizedLine(
                text = "消息$index",
                bounds = Rect(left, 200 + (index * 50), right, 230 + (index * 50))
            )
        }

        val result = OcrTextPostProcessor.toChatMessages(
            lines = lines,
            screenWidth = 1000,
            screenHeight = 2000
        )

        assertEquals(20, result.messages.size)
        assertEquals("消息5", result.messages.first().content)
        assertEquals("消息24", result.messages.last().content)
        assertTrue(result.messages.all { it.role != ChatRole.UNKNOWN })
    }
}
