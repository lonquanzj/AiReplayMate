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
    fun detect_conversation_title_does_not_take_top_message() {
        val titleCandidate = nodeText(text = "小号~军", top = 86, left = 430, right = 650)
        val messageCandidate = nodeText(text = "今天天气不错", top = 234, left = 120, right = 430)

        assertEquals(
            "小号~军",
            detectConversationTitle(listOf(titleCandidate, messageCandidate))
        )

        val singleMessageResult = extractMessages(
            collectedTexts = listOf(messageCandidate),
            title = null,
            rootBounds = Rect(0, 0, 1080, 2000),
            inputNode = null
        )
        val mixedMessageResult = extractMessages(
            collectedTexts = listOf(titleCandidate, messageCandidate),
            title = null,
            rootBounds = Rect(0, 0, 1080, 2000),
            inputNode = null
        )

        assertEquals(listOf("今天天气不错"), singleMessageResult.map { it.content })
        assertEquals(listOf("今天天气不错"), mixedMessageResult.map { it.content })
    }

    @Test
    fun extraction_filters_overlay_keywords_and_keeps_chat_order() {
        val result = extractMessages(
            collectedTexts = listOf(
                nodeText(text = "浮窗", top = 620, left = 120, right = 220),
                nodeText(text = "出来吃宵夜", top = 700, left = 120, right = 420),
                nodeText(text = "怎么说", top = 780, left = 120, right = 320)
            ),
            title = null,
            rootBounds = Rect(0, 0, 1080, 2000),
            inputNode = null
        )

        assertEquals(listOf("出来吃宵夜", "怎么说"), result.map { it.content })
    }

    @Test
    fun extraction_text_only_mode_ignores_image_placeholders() {
        val result = extractMessages(
            collectedTexts = listOf(
                nodeText(text = "今天天气不错", top = 240, left = 120, right = 430),
                imagePlaceholderNode(top = 420, left = 120, right = 420, bottom = 700),
                imagePlaceholderNode(top = 740, left = 120, right = 500, bottom = 980),
                imagePlaceholderNode(top = 1040, left = 120, right = 320, bottom = 1240)
            ),
            title = null,
            rootBounds = Rect(0, 0, 1080, 2400),
            inputNode = null
        )

        assertEquals(listOf("今天天气不错"), result.map { it.content })
    }

    @Test
    fun extraction_keeps_duplicate_texts_for_debugging() {
        val result = extractMessages(
            collectedTexts = listOf(
                nodeText(text = "出来吃宵夜", top = 700, left = 120, right = 420),
                nodeText(text = "出来吃宵夜", top = 760, left = 120, right = 420),
                nodeText(text = "怎么说", top = 840, left = 120, right = 320)
            ),
            title = null,
            rootBounds = Rect(0, 0, 1080, 2400),
            inputNode = null
        )

        assertEquals(
            listOf("出来吃宵夜", "出来吃宵夜", "怎么说"),
            result.map { it.content }
        )
    }

    @Test
    fun collect_node_signals_does_not_emit_image_placeholders_in_text_only_mode() {
        val imageBubble = AccessibilityNodeInfo.obtain().apply {
            className = "android.widget.ImageView"
            setBoundsInScreen(Rect(120, 520, 320, 720))
        }
        val avatar = AccessibilityNodeInfo.obtain().apply {
            className = "android.widget.ImageView"
            setBoundsInScreen(Rect(120, 840, 180, 900))
        }

        val collectedTexts = mutableListOf<NodeText>()
        val editableNodes = mutableListOf<AccessibilityNodeInfo>()

        collectNodeSignals(imageBubble, collectedTexts, editableNodes)
        collectNodeSignals(avatar, collectedTexts, editableNodes)

        assertTrue(collectedTexts.isEmpty())
    }

    @Test
    fun input_picker_prefers_chat_input_over_search_box() {
        val searchNode = editableNode(hint = "搜索", top = 220)
        val chatNode = editableNode(hint = "输入消息", top = 1700)

        val selected = pickChatInputNode(listOf(searchNode, chatNode))

        assertEquals("输入消息", selected?.hintText?.toString())
    }

    private fun nodeText(
        text: String,
        top: Int,
        left: Int = 120,
        right: Int = left + 400,
        bottom: Int = top + 64
    ): NodeText {
        return NodeText(
            text = text,
            source = NodeTextSource.TEXT,
            className = "android.widget.TextView",
            isEditable = false,
            isClickable = false,
            left = left,
            top = top,
            right = right,
            bottom = bottom
        )
    }

    private fun imagePlaceholderNode(
        top: Int,
        left: Int,
        right: Int,
        bottom: Int
    ): NodeText {
        return NodeText(
            text = "[图片]",
            source = NodeTextSource.TEXT,
            className = "android.widget.ImageView",
            isEditable = false,
            isClickable = false,
            isImagePlaceholder = true,
            left = left,
            top = top,
            right = right,
            bottom = bottom
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
