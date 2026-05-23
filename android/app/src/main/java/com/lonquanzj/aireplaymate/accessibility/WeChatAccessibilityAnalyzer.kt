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

data class WindowSignalSnapshot(
    val editableNodeCount: Int,
    val visibleTextSample: List<String>,
    val inspection: WeChatInspectionResult
)

object WeChatAccessibilityAnalyzer {
    fun inspectWindow(
        packageName: String,
        root: AccessibilityNodeInfo?
    ): WindowSignalSnapshot {
        if (packageName != WECHAT_PACKAGE_NAME) {
            return WindowSignalSnapshot(
                editableNodeCount = 0,
                visibleTextSample = emptyList(),
                inspection = emptyInspectionResult("\u5f53\u524d\u4e0d\u662f\u5fae\u4fe1\u5305\u540d")
            )
        }

        if (root == null) {
            return WindowSignalSnapshot(
                editableNodeCount = 0,
                visibleTextSample = emptyList(),
                inspection = emptyInspectionResult("\u5f53\u524d\u7a97\u53e3\u6839\u8282\u70b9\u4e3a\u7a7a")
            )
        }

        val signals = WindowNodeSignals()
        collectWindowSignals(root, signals)
        val inspection = inspectSignals(
            collectedTexts = signals.collectedTexts,
            editableNodes = signals.editableNodes,
            root = root,
            includeEnglishControls = true
        )

        return WindowSignalSnapshot(
            editableNodeCount = signals.editableNodeCount,
            visibleTextSample = signals.visibleTexts.take(VISIBLE_TEXT_SAMPLE_LIMIT),
            inspection = inspection
        )
    }

    fun inspect(
        packageName: String,
        root: AccessibilityNodeInfo?
    ): WeChatInspectionResult {
        if (packageName != WECHAT_PACKAGE_NAME) {
            return emptyInspectionResult("\u5f53\u524d\u4e0d\u662f\u5fae\u4fe1\u5305\u540d")
        }

        if (root == null) {
            return emptyInspectionResult("\u5f53\u524d\u7a97\u53e3\u6839\u8282\u70b9\u4e3a\u7a7a")
        }

        val collectedTexts = mutableListOf<NodeText>()
        val editableNodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodeSignals(root, collectedTexts, editableNodes)

        return inspectSignals(
            collectedTexts = collectedTexts,
            editableNodes = editableNodes,
            root = root,
            includeEnglishControls = false
        )
    }

    fun findChatInputNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val editableNodes = mutableListOf<AccessibilityNodeInfo>()
        collectEditableNodes(root, editableNodes)
        return pickChatInputNode(editableNodes)
    }

    private fun inspectSignals(
        collectedTexts: List<NodeText>,
        editableNodes: List<AccessibilityNodeInfo>,
        root: AccessibilityNodeInfo,
        includeEnglishControls: Boolean
    ): WeChatInspectionResult {
        val rootBounds = Rect().also(root::getBoundsInScreen)
        val hasChatControl = hasChatControl(collectedTexts, includeEnglishControls)
        val inputNode = pickChatInputNode(editableNodes)
        val title = detectConversationTitle(collectedTexts)
        val messages = extractMessages(collectedTexts, title, rootBounds, inputNode)
        val looksLikeChatPage = hasChatControl || messages.size >= 2 || inputNode != null

        return WeChatInspectionResult(
            looksLikeChatPage = looksLikeChatPage,
            reason = buildInspectionReason(inputNode != null, hasChatControl, messages.size),
            conversationTitle = title,
            inputNodeFound = inputNode != null,
            inputNodeHint = inputNode?.hintText?.toString()?.trim()?.ifEmpty { null }
                ?: inputNode?.text?.toString()?.trim()?.ifEmpty { null },
            extractedMessages = messages
        )
    }

    private fun emptyInspectionResult(reason: String): WeChatInspectionResult {
        return WeChatInspectionResult(
            looksLikeChatPage = false,
            reason = reason,
            conversationTitle = null,
            inputNodeFound = false,
            inputNodeHint = null,
            extractedMessages = emptyList()
        )
    }

    private fun hasChatControl(
        collectedTexts: List<NodeText>,
        includeEnglishControls: Boolean
    ): Boolean {
        return collectedTexts.any { node ->
            node.text.contains("\u53d1\u9001") ||
                node.text.contains("\u6309\u4f4f\u8bf4\u8bdd") ||
                node.text.contains("\u8bed\u97f3\u8f93\u5165") ||
                node.text.contains("\u66f4\u591a\u529f\u80fd") ||
                if (includeEnglishControls) {
                    node.text.equals("+", ignoreCase = true) ||
                        node.text.contains("send", ignoreCase = true) ||
                        node.text.contains("voice", ignoreCase = true) ||
                        node.text.contains("more", ignoreCase = true)
                } else {
                    node.text == "+"
                }
        }
    }

    private fun buildInspectionReason(
        inputNodeFound: Boolean,
        hasChatControl: Boolean,
        messageCount: Int
    ): String {
        return buildString {
            append(if (inputNodeFound) "\u5df2\u627e\u5230\u8f93\u5165\u6846" else "\u672a\u627e\u5230\u8f93\u5165\u6846")
            append("\uff0c")
            append(if (hasChatControl) "\u547d\u4e2d\u804a\u5929\u63a7\u4ef6" else "\u672a\u547d\u4e2d\u804a\u5929\u63a7\u4ef6")
            append("\uff0c")
            append("\u63d0\u53d6\u5230 $messageCount \u6761\u5019\u9009\u6d88\u606f")
        }
    }
}
