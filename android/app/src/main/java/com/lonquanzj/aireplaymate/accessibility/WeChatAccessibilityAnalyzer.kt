package com.lonquanzj.aireplaymate.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class WeChatInspectionResult(
    val looksLikeChatPage: Boolean,
    val reason: String,
    val conversationTitle: String?,
    val inputNodeFound: Boolean,
    val inputNodeHint: String?,
    val extractedMessages: List<String>
)

object WeChatAccessibilityAnalyzer {
    fun inspect(
        packageName: String,
        root: AccessibilityNodeInfo?
    ): WeChatInspectionResult {
        if (packageName != WECHAT_PACKAGE_NAME) {
            return WeChatInspectionResult(
                looksLikeChatPage = false,
                reason = "当前不是微信包名",
                conversationTitle = null,
                inputNodeFound = false,
                inputNodeHint = null,
                extractedMessages = emptyList()
            )
        }

        if (root == null) {
            return WeChatInspectionResult(
                looksLikeChatPage = false,
                reason = "当前窗口根节点为空",
                conversationTitle = null,
                inputNodeFound = false,
                inputNodeHint = null,
                extractedMessages = emptyList()
            )
        }

        val collectedTexts = mutableListOf<NodeText>()
        val editableNodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodeSignals(root, collectedTexts, editableNodes)

        val hasChatControl = collectedTexts.any { node ->
            node.text.contains("发送") ||
                node.text.contains("按住说话") ||
                node.text.contains("语音输入") ||
                node.text.contains("更多功能") ||
                node.text == "+"
        }

        val title = detectConversationTitle(collectedTexts)
        val inputNode = pickChatInputNode(editableNodes)
        val messages = extractMessageTexts(collectedTexts, title)
        val looksLikeChatPage = inputNode != null && (hasChatControl || messages.size >= 2)

        val reason = buildString {
            append(if (inputNode != null) "已找到输入框" else "未找到输入框")
            append("，")
            append(if (hasChatControl) "命中聊天控件" else "未命中聊天控件")
            append("，")
            append("提取到 ${messages.size} 条候选消息")
        }

        return WeChatInspectionResult(
            looksLikeChatPage = looksLikeChatPage,
            reason = reason,
            conversationTitle = title,
            inputNodeFound = inputNode != null,
            inputNodeHint = inputNode?.hintText?.toString()?.trim()?.ifEmpty { null }
                ?: inputNode?.text?.toString()?.trim()?.ifEmpty { null },
            extractedMessages = messages
        )
    }

    fun findChatInputNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val editableNodes = mutableListOf<AccessibilityNodeInfo>()
        collectEditableNodes(root, editableNodes)
        return pickChatInputNode(editableNodes)
    }

    private fun collectNodeSignals(
        node: AccessibilityNodeInfo,
        collectedTexts: MutableList<NodeText>,
        editableNodes: MutableList<AccessibilityNodeInfo>
    ) {
        val bounds = Rect().also(node::getBoundsInScreen)
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()
        val className = node.className?.toString().orEmpty()

        if (node.isEditable || className.contains("EditText")) {
            editableNodes += node
        }

        if (text.isNotEmpty()) {
            collectedTexts += NodeText(text = text, top = bounds.top)
        }

        if (description.isNotEmpty()) {
            collectedTexts += NodeText(text = description, top = bounds.top)
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectNodeSignals(child, collectedTexts, editableNodes)
        }
    }

    private fun detectConversationTitle(collectedTexts: List<NodeText>): String? {
        return collectedTexts
            .filter { it.top in 0..260 }
            .map { it.text }
            .firstOrNull { text ->
                text.length in 2..24 &&
                    text !in blockedUiTexts &&
                    !text.contains(":") &&
                    !text.contains("微信")
            }
    }

    private fun collectEditableNodes(
        node: AccessibilityNodeInfo,
        editableNodes: MutableList<AccessibilityNodeInfo>
    ) {
        val className = node.className?.toString().orEmpty()
        if (node.isEditable || className.contains("EditText")) {
            editableNodes += node
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectEditableNodes(child, editableNodes)
        }
    }

    private fun pickChatInputNode(editableNodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        return editableNodes.firstOrNull { node ->
            val hint = node.hintText?.toString()?.trim().orEmpty()
            val text = node.text?.toString()?.trim().orEmpty()
            val className = node.className?.toString().orEmpty()
            className.contains("EditText") ||
                hint.contains("输入") ||
                hint.contains("消息") ||
                text.contains("输入")
        } ?: editableNodes.firstOrNull()
    }

    private fun extractMessageTexts(
        collectedTexts: List<NodeText>,
        title: String?
    ): List<String> {
        val seen = linkedSetOf<String>()

        collectedTexts
            .sortedBy { it.top }
            .forEach { item ->
                val text = item.text.trim()
                if (text.isEmpty()) return@forEach
                if (text == title) return@forEach
                if (text in blockedUiTexts) return@forEach
                if (text.length > 120) return@forEach
                if (timeRegex.matches(text)) return@forEach
                if (emojiOnlyRegex.matches(text)) return@forEach
                seen += text
            }

        return seen.toList().takeLast(8)
    }

    private data class NodeText(
        val text: String,
        val top: Int
    )

    private val blockedUiTexts = setOf(
        "发送",
        "更多功能",
        "按住说话",
        "语音输入",
        "切换到按住说话",
        "切换到键盘",
        "表情",
        "+",
        "微信",
        "返回"
    )

    private val timeRegex = Regex("^\\d{1,2}:\\d{2}$")
    private val emojiOnlyRegex = Regex("^[\\p{So}\\p{Cn}]+$")
}
