package com.lonquanzj.aireplaymate.ocr

import android.graphics.Rect
import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.accessibility.MessageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OcrBubbleGroupingTest {
    @Test
    fun canMerge_allows_neighbor_lines_with_same_role_and_horizontal_overlap() {
        val group = OcrBubbleCandidate(
            line("第一行", ChatRole.FRIEND, Rect(60, 420, 340, 460))
        )
        val next = line("第二行", ChatRole.FRIEND, Rect(70, 478, 360, 518))

        assertTrue(group.canMerge(next, screenHeight = 2000))
        group.add(next)

        val message = group.toChatMessage()
        assertNotNull(message)
        assertEquals("第一行\n第二行", message?.content)
        assertEquals(ChatRole.FRIEND, message?.role)
        assertEquals(MessageSource.OCR, message?.source)
        assertEquals("60,420,360,518", message?.boundsHint)
    }

    @Test
    fun canMerge_rejects_different_roles_unknown_role_large_gap_and_no_overlap() {
        val group = OcrBubbleCandidate(
            line("第一行", ChatRole.FRIEND, Rect(60, 420, 340, 460))
        )

        assertFalse(
            group.canMerge(
                line("我的回复", ChatRole.ME, Rect(650, 478, 940, 518)),
                screenHeight = 2000
            )
        )
        assertFalse(
            group.canMerge(
                line("间隔太远", ChatRole.FRIEND, Rect(70, 560, 360, 600)),
                screenHeight = 2000
            )
        )
        assertFalse(
            group.canMerge(
                line("没有重叠", ChatRole.FRIEND, Rect(360, 478, 620, 518)),
                screenHeight = 2000
            )
        )

        val unknownGroup = OcrBubbleCandidate(
            line("未知角色", ChatRole.UNKNOWN, Rect(420, 420, 620, 460))
        )
        assertFalse(
            unknownGroup.canMerge(
                line("未知第二行", ChatRole.UNKNOWN, Rect(430, 478, 640, 518)),
                screenHeight = 2000
            )
        )
    }

    @Test
    fun toChatMessage_drops_too_short_group_and_sets_unknown_confidence() {
        val shortGroup = OcrBubbleCandidate(
            line("好", ChatRole.FRIEND, Rect(60, 420, 120, 460))
        )
        val unknownGroup = OcrBubbleCandidate(
            line("中间消息", ChatRole.UNKNOWN, Rect(420, 520, 620, 560))
        )

        assertNull(shortGroup.toChatMessage())
        val unknownMessage = unknownGroup.toChatMessage()
        assertNotNull(unknownMessage)
        assertEquals(ChatRole.UNKNOWN, unknownMessage?.role)
        assertEquals(UNKNOWN_ROLE_CONFIDENCE, unknownMessage?.confidence ?: 0f, 0.0001f)
    }

    private fun line(
        text: String,
        role: ChatRole,
        bounds: Rect
    ): OcrLineCandidate {
        return OcrLineCandidate(
            text = text,
            role = role,
            bounds = bounds
        )
    }
}
