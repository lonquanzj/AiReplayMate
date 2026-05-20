package com.lonquanzj.aireplaymate.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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

        inputNode.prepareForTextInput()

        val setTextSuccess = inputNode.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    trimmed
                )
            }
        )
        if (setTextSuccess && isInputTextConfirmed(trimmed)) {
            val result = AutofillAttemptResult(true, "已通过 ACTION_SET_TEXT 写入并回读确认")
            AccessibilityDebugStore.onAutofillAttempt(result.message, trimmed)
            return result
        }

        val pasteSuccess = pasteViaClipboard(trimmed)
        val result = when {
            pasteSuccess && isInputTextConfirmed(trimmed) -> {
                AutofillAttemptResult(true, "已通过剪贴板粘贴兜底并回读确认")
            }

            pasteSuccess -> {
                AutofillAttemptResult(true, "已执行剪贴板粘贴，回读暂未确认，请检查输入框")
            }

            setTextSuccess -> {
                AutofillAttemptResult(true, "ACTION_SET_TEXT 已执行，但回读暂未确认，请检查输入框")
            }

            else -> {
                AutofillAttemptResult(false, "ACTION_SET_TEXT 与剪贴板粘贴均执行失败")
            }
        }
        AccessibilityDebugStore.onAutofillAttempt(result.message, trimmed)
        return result
    }

    private fun AccessibilityNodeInfo.prepareForTextInput() {
        performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun pasteViaClipboard(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val inputNode = WeChatAccessibilityAnalyzer.findChatInputNode(root) ?: return false
        inputNode.prepareForTextInput()

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
        return inputNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    private fun isInputTextConfirmed(expected: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val inputNode = WeChatAccessibilityAnalyzer.findChatInputNode(root) ?: return false
        inputNode.refresh()
        val actual = inputNode.text?.toString()?.trim().orEmpty()
        return actual == expected || actual.endsWith(expected)
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

    private companion object {
        const val CLIP_LABEL = "AiReplayMate reply"
    }
}
