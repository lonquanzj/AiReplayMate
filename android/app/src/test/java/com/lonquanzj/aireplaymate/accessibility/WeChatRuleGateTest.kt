package com.lonquanzj.aireplaymate.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WeChatRuleGateTest {
    @Test
    fun inspect_without_root_keeps_expected_reason_literals() {
        val nonWechat = WeChatAccessibilityAnalyzer.inspect(
            packageName = "com.example.other",
            root = null
        )
        val wechatButNoRoot = WeChatAccessibilityAnalyzer.inspect(
            packageName = WECHAT_PACKAGE_NAME,
            root = null
        )

        assertEquals("当前不是微信包名", nonWechat.reason)
        assertEquals("当前窗口根节点为空", wechatButNoRoot.reason)
    }

    @Test
    fun extraction_rules_keep_chinese_chrome_and_time_filters() {
        assertTrue("blockedUiTexts should contain 发送", blockedUiTexts.contains("发送"))
        assertTrue("blockedUiTexts should contain 更多功能", blockedUiTexts.contains("更多功能"))
        assertFalse("blockedUiTexts should not contain ??", blockedUiTexts.any { it.contains("??") })
        assertFalse(
            "blockedUiTextFragments should not contain ??",
            blockedUiTextFragments.any { it.contains("??") }
        )

        val result = extractMessages(
            collectedTexts = listOf(
                nodeText(text = "19:52", top = 260),
                nodeText(text = "下午 7:52", top = 360),
                nodeText(text = "今天 下午 7:52", top = 460),
                nodeText(text = "你好呀", top = 560)
            ),
            title = null,
            rootBounds = Rect(0, 0, 1080, 2000),
            inputNode = null
        )

        assertEquals(listOf("你好呀"), result.map { it.content })
        val systemMessage = extractMessages(
            collectedTexts = listOf(nodeText(text = "系统提示：你撤回了一条消息", top = 560)),
            title = null,
            rootBounds = Rect(0, 0, 1080, 2000),
            inputNode = null
        ).firstOrNull()
        assertNotNull("system hint text should be extracted", systemMessage)
        assertEquals("system hint text should map to SYSTEM role", ChatRole.SYSTEM, systemMessage?.role)
    }

    @Test
    fun input_picker_prefers_chat_input_over_search_box() {
        val searchNode = editableNode(hint = "搜索", top = 220)
        val chatNode = editableNode(hint = "输入消息", top = 1700)

        val selected = pickChatInputNode(listOf(searchNode, chatNode))

        assertEquals("输入消息", selected?.hintText?.toString())
    }

    private fun nodeText(text: String, top: Int): NodeText {
        return NodeText(
            text = text,
            source = NodeTextSource.TEXT,
            className = "android.widget.TextView",
            isEditable = false,
            isClickable = false,
            left = 120,
            top = top,
            right = 520,
            bottom = top + 64
        )
    }

    private fun editableNode(hint: String, top: Int): AccessibilityNodeInfo {
        return AccessibilityNodeInfo.obtain().apply {
            className = "android.widget.EditText"
            setHintText(hint)
            setBoundsInScreen(Rect(100, top, 980, top + 90))
        }
    }
}
