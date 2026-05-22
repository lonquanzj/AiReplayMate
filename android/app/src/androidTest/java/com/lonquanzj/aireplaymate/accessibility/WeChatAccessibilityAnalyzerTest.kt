package com.lonquanzj.aireplaymate.accessibility

import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lonquanzj.aireplaymate.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeChatAccessibilityAnalyzerTest {
    @Test
    fun inspect_returns_not_chat_page_for_non_wechat_package() {
        val result = WeChatAccessibilityAnalyzer.inspect(
            packageName = "com.example.other",
            root = null
        )

        assertFalse(result.looksLikeChatPage)
        assertEquals("当前不是微信包名", result.reason)
    }

    @Test
    fun inspect_returns_not_chat_page_when_root_is_null() {
        val result = WeChatAccessibilityAnalyzer.inspect(
            packageName = WECHAT_PACKAGE_NAME,
            root = null
        )

        assertFalse(result.looksLikeChatPage)
        assertEquals("当前窗口根节点为空", result.reason)
    }

    @Test
    fun inspect_detects_chat_page_and_title_without_using_control_text() {
        val result = inspectWithNodes(
            listOf(
                NodeSpec(text = "返回", left = 20, top = 60, width = 80),
                NodeSpec(text = "小王", left = 420, top = 80, width = 240),
                NodeSpec(text = "发送", left = 900, top = 80, width = 120),
                NodeSpec(text = "你好呀", left = 60, top = 280, width = 360),
                NodeSpec(text = "晚上聊", left = 650, top = 420, width = 300),
                NodeSpec(editable = true, hint = "输入消息", left = 120, top = 1700, width = 760)
            )
        )

        assertTrue(result.looksLikeChatPage)
        assertEquals("小王", result.conversationTitle)
        assertTrue(result.inputNodeFound)
        assertEquals("输入消息", result.inputNodeHint)
        assertEquals(2, result.extractedMessages.size)
    }

    @Test
    fun inspect_prefers_text_over_content_description_and_infers_roles() {
        val result = inspectWithNodes(
            listOf(
                NodeSpec(text = "小李", left = 430, top = 80, width = 220),
                NodeSpec(text = "你好呀", contentDescription = "你好呀", left = 60, top = 280, width = 360),
                NodeSpec(text = "以上是打招呼的内容", left = 330, top = 380, width = 420),
                NodeSpec(text = "我在路上", left = 680, top = 500, width = 260),
                NodeSpec(editable = true, hint = "输入消息", left = 120, top = 1700, width = 760)
            )
        )

        assertEquals(3, result.extractedMessages.size)
        assertEquals(1, result.extractedMessages.count { it.content == "你好呀" })
        assertEquals(ChatRole.FRIEND, result.extractedMessages.first { it.content == "你好呀" }.role)
        assertEquals(ChatRole.SYSTEM, result.extractedMessages.first { it.content == "以上是打招呼的内容" }.role)
        assertEquals(ChatRole.ME, result.extractedMessages.first { it.content == "我在路上" }.role)
    }

    @Test
    fun inspect_does_not_treat_timestamp_text_as_message() {
        val result = inspectWithNodes(
            listOf(
                NodeSpec(text = "小李", left = 430, top = 80, width = 220),
                NodeSpec(text = "19:52", left = 480, top = 180, width = 120),
                NodeSpec(text = "下午 7:52", left = 430, top = 300, width = 220),
                NodeSpec(text = "今天 下午 7:52", left = 360, top = 420, width = 360),
                NodeSpec(editable = true, hint = "输入消息", left = 120, top = 1700, width = 760)
            )
        )

        assertTrue(result.looksLikeChatPage)
        assertEquals(0, result.extractedMessages.size)
    }

    @Test
    fun findChatInputNode_prefers_real_chat_input_over_generic_search_box() {
        val inputNode = inspectInputNodeWithNodes(
            listOf(
                NodeSpec(editable = true, hint = "搜索", left = 140, top = 220, width = 500),
                NodeSpec(text = "你好", left = 80, top = 340, width = 320),
                NodeSpec(editable = true, hint = "输入消息", left = 120, top = 1700, width = 760)
            )
        )

        assertNotNull(inputNode)
        assertEquals("输入消息", inputNode?.hintText?.toString())
    }

    private fun inspectWithNodes(nodes: List<NodeSpec>): WeChatInspectionResult {
        lateinit var result: WeChatInspectionResult
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = buildRoot(activity, nodes)
                result = WeChatAccessibilityAnalyzer.inspect(WECHAT_PACKAGE_NAME, root)
            }
        }
        return result
    }

    private fun inspectInputNodeWithNodes(nodes: List<NodeSpec>): AccessibilityNodeInfo? {
        lateinit var inputNode: AccessibilityNodeInfo
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = buildRoot(activity, nodes)
                inputNode = WeChatAccessibilityAnalyzer.findChatInputNode(root)!!
            }
        }
        return inputNode
    }

    private fun buildRoot(
        activity: ComponentActivity,
        nodes: List<NodeSpec>
    ): AccessibilityNodeInfo {
        val container = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(ROOT_WIDTH, ROOT_HEIGHT)
        }
        nodes.forEach { spec ->
            container.addView(spec.createView(activity))
        }
        activity.setContentView(container)

        val widthSpec = View.MeasureSpec.makeMeasureSpec(ROOT_WIDTH, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(ROOT_HEIGHT, View.MeasureSpec.EXACTLY)
        container.measure(widthSpec, heightSpec)
        container.layout(0, 0, ROOT_WIDTH, ROOT_HEIGHT)
        return container.createAccessibilityNodeInfo()
    }

    private data class NodeSpec(
        val text: String? = null,
        val contentDescription: String? = null,
        val hint: String? = null,
        val editable: Boolean = false,
        val left: Int,
        val top: Int,
        val width: Int = 300,
        val height: Int = 90
    ) {
        fun createView(activity: ComponentActivity): View {
            val view = if (editable) {
                EditText(activity).apply {
                    this.hint = this@NodeSpec.hint
                    setText(this@NodeSpec.text.orEmpty())
                }
            } else {
                TextView(activity).apply {
                    this@NodeSpec.text?.let { setText(it) }
                }
            }
            view.contentDescription = contentDescription
            val params = FrameLayout.LayoutParams(width, height).apply {
                leftMargin = left
                topMargin = top
            }
            view.layoutParams = params
            return view
        }
    }

    private companion object {
        const val ROOT_WIDTH = 1080
        const val ROOT_HEIGHT = 2000
    }
}
