package com.lonquanzj.aireplaymate.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent

class ReplyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AiReplayMate", "Accessibility service connected")
        AccessibilityActionBridge.attach(this)
        AccessibilityDebugStore.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString().orEmpty()
        val className = event.className?.toString().orEmpty()
        val root = rootInActiveWindow
        val editableNodeCount = countEditableNodes(root)
        val visibleTexts = collectVisibleTexts(root).take(6)
        val inspection = WeChatAccessibilityAnalyzer.inspect(
            packageName = packageName,
            root = root
        )
        val eventName = eventTypeName(event.eventType)

        Log.d(
            "AiReplayMate",
            "event=$eventName, package=$packageName, class=$className, editable=$editableNodeCount, chat=${inspection.looksLikeChatPage}"
        )

        AccessibilityDebugStore.onWindowEvent(
            eventName = eventName,
            packageName = packageName,
            className = className,
            editableNodeCount = editableNodeCount,
            visibleTextSample = visibleTexts,
            looksLikeChatPage = inspection.looksLikeChatPage,
            chatDetectionReason = inspection.reason,
            conversationTitle = inspection.conversationTitle,
            inputNodeFound = inspection.inputNodeFound,
            inputNodeHint = inspection.inputNodeHint,
            extractedMessages = inspection.extractedMessages
        )
    }

    override fun onInterrupt() {
        Log.d("AiReplayMate", "Accessibility service interrupted")
        AccessibilityDebugStore.onServiceInterrupted()
    }

    override fun onDestroy() {
        AccessibilityActionBridge.detach(this)
        AccessibilityDebugStore.onServiceInterrupted()
        super.onDestroy()
    }

    fun performAutofill(text: String): AutofillAttemptResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            val result = AutofillAttemptResult(false, "填入内容为空，已跳过")
            AccessibilityDebugStore.onAutofillAttempt(result.message, text)
            return result
        }

        val root = rootInActiveWindow
        if (root == null) {
            val result = AutofillAttemptResult(false, "当前窗口为空，无法定位输入框")
            AccessibilityDebugStore.onAutofillAttempt(result.message, trimmed)
            return result
        }

        val inputNode = WeChatAccessibilityAnalyzer.findChatInputNode(root)
        if (inputNode == null) {
            val result = AutofillAttemptResult(false, "未找到可用输入框")
            AccessibilityDebugStore.onAutofillAttempt(result.message, trimmed)
            return result
        }

        inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                trimmed
            )
        }
        val success = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        val result = if (success) {
            AutofillAttemptResult(true, "已尝试通过 ACTION_SET_TEXT 写入输入框")
        } else {
            AutofillAttemptResult(false, "ACTION_SET_TEXT 执行失败")
        }
        AccessibilityDebugStore.onAutofillAttempt(result.message, trimmed)
        return result
    }

    private fun countEditableNodes(node: AccessibilityNodeInfo?): Int {
        if (node == null) return 0

        var count = 0
        if (node.isEditable) {
            count += 1
        }

        for (index in 0 until node.childCount) {
            count += countEditableNodes(node.getChild(index))
        }

        return count
    }

    private fun collectVisibleTexts(node: AccessibilityNodeInfo?): List<String> {
        if (node == null) return emptyList()

        val results = linkedSetOf<String>()
        collectVisibleTextsInto(node, results)
        return results.toList()
    }

    private fun collectVisibleTextsInto(
        node: AccessibilityNodeInfo,
        results: MutableSet<String>
    ) {
        val text = node.text?.toString()?.trim().orEmpty()
        val description = node.contentDescription?.toString()?.trim().orEmpty()

        if (text.isNotEmpty()) {
            results += text
        }
        if (description.isNotEmpty()) {
            results += description
        }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            collectVisibleTextsInto(child, results)
        }
    }

    private fun eventTypeName(eventType: Int): String = when (eventType) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "TYPE_WINDOW_CONTENT_CHANGED"
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "TYPE_VIEW_CLICKED"
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TYPE_VIEW_TEXT_CHANGED"
        else -> "TYPE_$eventType"
    }
}
