package com.lonquanzj.aireplaymate.ocr

import android.graphics.Rect
import com.lonquanzj.aireplaymate.accessibility.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OcrLineCleaningTest {
    @Test
    fun clean_keeps_normal_friend_and_me_chat_lines() {
        val friend = OcrRecognizedLine(
            text = "刚刚到家",
            bounds = Rect(60, 420, 320, 460)
        ).clean(screenWidth = 1000, screenHeight = 2000)
        val me = OcrRecognizedLine(
            text = "那我等你消息",
            bounds = Rect(680, 520, 940, 560)
        ).clean(screenWidth = 1000, screenHeight = 2000)

        assertNull(friend.dropped)
        assertNotNull(friend.candidate)
        assertEquals(ChatRole.FRIEND, friend.candidate?.role)
        assertEquals("刚刚到家", friend.candidate?.text)

        assertNull(me.dropped)
        assertNotNull(me.candidate)
        assertEquals(ChatRole.ME, me.candidate?.role)
        assertEquals("那我等你消息", me.candidate?.text)
    }

    @Test
    fun clean_rejects_wechat_chrome_time_badge_and_system_notice() {
        assertDropped("微信", Rect(80, 300, 220, 340), OcrFilterReason.CHROME_TEXT)
        assertDropped("下午 7:52", Rect(420, 360, 620, 400), OcrFilterReason.TIME_OR_BADGE)
        assertDropped("99", Rect(920, 420, 970, 460), OcrFilterReason.TIME_OR_BADGE)
        assertDropped("对方撤回了一条消息", Rect(330, 500, 700, 540), OcrFilterReason.SYSTEM_NOTICE)
    }

    @Test
    fun clean_rejects_lines_outside_chat_content_area() {
        assertDropped("顶部标题", Rect(80, 80, 260, 120), OcrFilterReason.TOP_CHROME)
        assertDropped("输入框里的草稿", Rect(80, 1720, 520, 1760), OcrFilterReason.BOTTOM_INPUT)
        assertDropped(
            text = "这是一条横跨整屏的提示文案",
            bounds = Rect(20, 620, 940, 660),
            expectedReason = OcrFilterReason.TOO_WIDE
        )
    }

    @Test
    fun clean_reports_missing_and_invalid_bounds() {
        val missing = OcrRecognizedLine("缺少位置", null).clean(
            screenWidth = 1000,
            screenHeight = 2000
        )
        val invalid = OcrRecognizedLine("无效位置", Rect(100, 500, 100, 540)).clean(
            screenWidth = 1000,
            screenHeight = 2000
        )

        assertEquals(OcrFilterReason.MISSING_BOUNDS, missing.dropped?.reason)
        assertNull(missing.candidate)
        assertEquals(OcrFilterReason.INVALID_BOUNDS, invalid.dropped?.reason)
        assertNull(invalid.candidate)
    }

    private fun assertDropped(
        text: String,
        bounds: Rect,
        expectedReason: OcrFilterReason
    ) {
        val result = OcrRecognizedLine(text, bounds).clean(
            screenWidth = 1000,
            screenHeight = 2000
        )

        assertNull(result.candidate)
        assertEquals(expectedReason, result.dropped?.reason)
        assertEquals(text, result.dropped?.text)
    }
}
