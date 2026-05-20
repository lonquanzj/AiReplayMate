package com.lonquanzj.aireplaymate.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class WeChatInspectionResult(
    val looksLikeChatPage: Boolean,
    val reason: String,
    val conversationTitle: String?,
    val inputNodeFound: Boolean,
    val inputNodeHint: String?,
    val extractedMessages: List<ChatMessage>
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
        val rootBounds = Rect().also(root::getBoundsInScreen)

        val hasChatControl = collectedTexts.any { node ->
            node.text.contains("发送") ||
                node.text.contains("按住说话") ||
                node.text.contains("语音输入") ||
                node.text.contains("更多功能") ||
                node.text == "+"
        }

        val title = detectConversationTitle(collectedTexts)
        val inputNode = pickChatInputNode(editableNodes)
        val messages = extractMessages(collectedTexts, title, rootBounds)
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
            collectedTexts += NodeText(
                text = text,
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom
            )
        }

        if (description.isNotEmpty()) {
            collectedTexts += NodeText(
                text = description,
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom
            )
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

    private fun extractMessages(
        collectedTexts: List<NodeText>,
        title: String?,
        rootBounds: Rect
    ): List<ChatMessage> {
        val seen = linkedMapOf<String, NodeText>()

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
                seen.putIfAbsent(text, item)
            }

        return seen.values.toList()
            .takeLast(8)
            .mapIndexed { index, item ->
                val role = inferRole(item, rootBounds)
                ChatMessage(
                    id = stableMessageId(index, item.text),
                    role = role,
                    content = item.text,
                    timestamp = null,
                    source = MessageSource.ACCESSIBILITY,
                    confidence = confidenceForRole(role),
                    boundsHint = item.boundsHint
                )
            }
    }

    private fun inferRole(
        item: NodeText,
        rootBounds: Rect
    ): ChatRole {
        val text = item.text
        if (systemHintRegex.containsMatchIn(text)) return ChatRole.SYSTEM

        val rootWidth = rootBounds.width().takeIf { it > 0 } ?: return ChatRole.UNKNOWN
        val centerX = item.centerX - rootBounds.left
        val leftRatio = (item.left - rootBounds.left).toFloat() / rootWidth
        val centerRatio = centerX.toFloat() / rootWidth

        return when {
            leftRatio > 0.42f || centerRatio > 0.58f -> ChatRole.ME
            centerRatio < 0.52f -> ChatRole.FRIEND
            else -> ChatRole.UNKNOWN
        }
    }

    private fun confidenceForRole(role: ChatRole): Float = when (role) {
        ChatRole.ME,
        ChatRole.FRIEND -> 0.72f
        ChatRole.SYSTEM -> 0.82f
        ChatRole.UNKNOWN -> 0.45f
    }

    private fun stableMessageId(
        index: Int,
        content: String
    ): String {
        val hash = content.hashCode().toUInt().toString(16)
        return "accessibility_${index}_$hash"
    }

    private data class NodeText(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val centerX: Int = left + ((right - left) / 2)
        val boundsHint: String = "$left,$top,$right,$bottom"
    }

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
    private val systemHintRegex = Regex("撤回|以上是|以下是|系统")
}
