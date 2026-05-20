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

        val inputNode = pickChatInputNode(editableNodes)
        val title = detectConversationTitle(collectedTexts)
        val messages = extractMessages(collectedTexts, title, rootBounds, inputNode)
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
                source = NodeTextSource.TEXT,
                className = className,
                isEditable = node.isEditable,
                isClickable = node.isClickable,
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom
            )
        }

        if (description.isNotEmpty()) {
            collectedTexts += NodeText(
                text = description,
                source = NodeTextSource.CONTENT_DESCRIPTION,
                className = className,
                isEditable = node.isEditable,
                isClickable = node.isClickable,
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
            .filter { it.top in 0..260 && !it.isLikelyControl }
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
        rootBounds: Rect,
        inputNode: AccessibilityNodeInfo?
    ): List<ChatMessage> {
        val inputBounds = inputNode?.let { Rect().also(it::getBoundsInScreen) }
        val seen = linkedMapOf<String, NodeText>()

        collectedTexts
            .sortedBy { it.top }
            .forEach { item ->
                val text = item.text.trim()
                if (!item.isMessageCandidate(title, inputBounds, rootBounds)) return@forEach
                val key = text.normalizedMessageKey()
                val existing = seen[key]
                if (existing == null || item.isBetterDuplicateThan(existing)) {
                    seen[key] = item.copy(text = text)
                }
            }

        return seen.values.toList()
            .takeLast(MAX_EXTRACTED_MESSAGES)
            .mapIndexed { index, item ->
                val role = inferRole(item, rootBounds)
                ChatMessage(
                    id = stableMessageId(index, item.text),
                    role = role,
                    content = item.text,
                    timestamp = null,
                    source = MessageSource.ACCESSIBILITY,
                    confidence = confidenceFor(item, role),
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
        val rightRatio = (item.right - rootBounds.left).toFloat() / rootWidth
        val centerRatio = centerX.toFloat() / rootWidth

        return when {
            item.isCenteredSystemLike(rootBounds) -> ChatRole.SYSTEM
            rightRatio > 0.66f && centerRatio > 0.48f -> ChatRole.ME
            leftRatio < 0.40f && centerRatio < 0.58f -> ChatRole.FRIEND
            centerRatio > 0.60f -> ChatRole.ME
            centerRatio < 0.50f -> ChatRole.FRIEND
            else -> ChatRole.UNKNOWN
        }
    }

    private fun confidenceFor(
        item: NodeText,
        role: ChatRole
    ): Float {
        val base = when (role) {
            ChatRole.ME,
            ChatRole.FRIEND -> 0.76f
            ChatRole.SYSTEM -> 0.84f
            ChatRole.UNKNOWN -> 0.45f
        }
        val sourcePenalty = if (item.source == NodeTextSource.CONTENT_DESCRIPTION) 0.08f else 0f
        val controlPenalty = if (item.isLikelyControl) 0.18f else 0f
        return (base - sourcePenalty - controlPenalty).coerceIn(0f, 1f)
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
        val source: NodeTextSource,
        val className: String,
        val isEditable: Boolean,
        val isClickable: Boolean,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val centerX: Int = left + ((right - left) / 2)
        val width: Int = right - left
        val boundsHint: String = "$left,$top,$right,$bottom"
        val isLikelyControl: Boolean =
            isEditable ||
                className.contains("Button", ignoreCase = true) ||
                className.contains("ImageView", ignoreCase = true) ||
                text in blockedUiTexts ||
                blockedUiTextFragments.any { text.contains(it) }
    }

    private enum class NodeTextSource {
        TEXT,
        CONTENT_DESCRIPTION
    }

    private fun NodeText.isMessageCandidate(
        title: String?,
        inputBounds: Rect?,
        rootBounds: Rect
    ): Boolean {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return false
        if (cleanText == title) return false
        if (cleanText.length > MAX_MESSAGE_TEXT_LENGTH) return false
        if (top < rootBounds.top + MIN_MESSAGE_TOP_OFFSET) return false
        if (inputBounds != null && bottom >= inputBounds.top - INPUT_AREA_PADDING) return false
        if (isLikelyControl) return false
        if (timeRegex.matches(cleanText)) return false
        if (dateRegex.matches(cleanText)) return false
        if (emojiOnlyRegex.matches(cleanText)) return false
        if (badgeRegex.matches(cleanText)) return false
        if (className.contains("TextView", ignoreCase = true).not() &&
            source == NodeTextSource.CONTENT_DESCRIPTION &&
            cleanText.length <= 2
        ) {
            return false
        }
        return true
    }

    private fun NodeText.isBetterDuplicateThan(other: NodeText): Boolean {
        if (source == NodeTextSource.TEXT && other.source == NodeTextSource.CONTENT_DESCRIPTION) return true
        if (source == other.source && width > other.width) return true
        return false
    }

    private fun NodeText.isCenteredSystemLike(rootBounds: Rect): Boolean {
        val rootWidth = rootBounds.width().takeIf { it > 0 } ?: return false
        val centerRatio = (centerX - rootBounds.left).toFloat() / rootWidth
        val widthRatio = width.toFloat() / rootWidth
        return centerRatio in 0.42f..0.58f && widthRatio < 0.72f && text.length <= 40
    }

    private fun String.normalizedMessageKey(): String {
        return whitespaceRegex.replace(trim(), " ")
    }

    private val blockedUiTexts = setOf(
        "发送",
        "更多功能",
        "按住说话",
        "语音输入",
        "切换到按住说话",
        "切换到键盘",
        "表情",
        "拍摄",
        "照片",
        "位置",
        "红包",
        "转账",
        "收藏",
        "语音通话",
        "视频通话",
        "+",
        "微信",
        "返回"
    )

    private val blockedUiTextFragments = listOf(
        "切换到",
        "更多",
        "聊天信息",
        "添加到通讯录",
        "消息免打扰"
    )

    private const val MAX_EXTRACTED_MESSAGES = 12
    private const val MAX_MESSAGE_TEXT_LENGTH = 180
    private const val MIN_MESSAGE_TOP_OFFSET = 132
    private const val INPUT_AREA_PADDING = 24
    private val timeRegex = Regex("^\\d{1,2}:\\d{2}$")
    private val dateRegex = Regex("^(周[一二三四五六日天]|昨天|今天|前天|\\d{1,2}月\\d{1,2}日).*$")
    private val badgeRegex = Regex("^\\d{1,3}$")
    private val emojiOnlyRegex = Regex("^[\\p{So}\\p{Cn}]+$")
    private val systemHintRegex = Regex("撤回|以上是|以下是|系统")
    private val whitespaceRegex = Regex("\\s+")
}
