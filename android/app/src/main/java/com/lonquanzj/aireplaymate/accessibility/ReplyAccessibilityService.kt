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
        val eventName = eventTypeName(event.eventType)

        logWindowEventIfNeeded(
            eventName = eventName,
            packageName = packageName,
            className = className
        )
        AccessibilityDebugStore.onLightWindowEvent(
            eventName = eventName,
            packageName = packageName,
            className = className
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

    fun inspectCurrentWindow(): WindowInspectionSnapshot {
        val root = rootInActiveWindow
        if (root == null) {
            return WindowInspectionSnapshot(
                success = false,
                state = AccessibilityDebugStore.state.value.copy(
                    serviceConnected = true,
                    updatedAtMillis = System.currentTimeMillis()
                ),
                message = "当前窗口为空，暂时无法刷新"
            )
        }

        val packageName = root.packageName?.toString().orEmpty()
        val className = root.className?.toString().orEmpty()
        val snapshot = WeChatAccessibilityAnalyzer.inspectWindow(
            packageName = packageName,
            root = root
        )
        val inspection = snapshot.inspection
        val now = System.currentTimeMillis()
        val state = AccessibilityDebugState(
            serviceConnected = true,
            lastEventName = "主动刷新",
            packageName = packageName,
            className = className,
            editableNodeCount = snapshot.editableNodeCount,
            visibleTextSample = snapshot.visibleTextSample,
            isWechatPackage = packageName == WECHAT_PACKAGE_NAME,
            looksLikeChatPage = inspection.looksLikeChatPage,
            chatDetectionReason = inspection.reason,
            conversationTitle = inspection.conversationTitle,
            inputNodeFound = inspection.inputNodeFound,
            inputNodeHint = inspection.inputNodeHint,
            extractedMessages = inspection.extractedMessages,
            lastAutofillStatus = AccessibilityDebugStore.state.value.lastAutofillStatus,
            lastAutofillCategory = AccessibilityDebugStore.state.value.lastAutofillCategory,
            lastAutofillSteps = AccessibilityDebugStore.state.value.lastAutofillSteps,
            lastAutofillPreview = AccessibilityDebugStore.state.value.lastAutofillPreview,
            lastFullInspectionAtMillis = now,
            updatedAtMillis = now
        )

        AccessibilityDebugStore.setState(state)
        return WindowInspectionSnapshot(
            success = true,
            state = state,
            message = "已刷新当前窗口"
        )
    }

    fun performAutofill(text: String): AutofillAttemptResult {
        val trimmed = text.trim()
        val steps = mutableListOf<String>()
        if (trimmed.isEmpty()) {
            val result = AutofillAttemptResult(
                success = false,
                message = "填入内容为空，已跳过",
                category = AutofillFailureCategory.EMPTY_TEXT,
                steps = listOf("内容校验：失败，文本为空")
            )
            AccessibilityDebugStore.onAutofillAttempt(result, text)
            return result
        }
        steps += "内容校验：通过，长度 ${trimmed.length}"

        val root = rootInActiveWindow
        if (root == null) {
            val result = AutofillAttemptResult(
                success = false,
                message = "当前窗口为空，无法定位输入框",
                category = AutofillFailureCategory.EMPTY_WINDOW,
                steps = steps + "窗口获取：失败，rootInActiveWindow 为空"
            )
            AccessibilityDebugStore.onAutofillAttempt(result, trimmed)
            return result
        }
        steps += "窗口获取：通过"

        val inputNode = WeChatAccessibilityAnalyzer.findChatInputNode(root)
        if (inputNode == null) {
            val result = AutofillAttemptResult(
                success = false,
                message = "未找到可用输入框",
                category = AutofillFailureCategory.INPUT_NOT_FOUND,
                steps = steps + "输入框定位：失败"
            )
            AccessibilityDebugStore.onAutofillAttempt(result, trimmed)
            return result
        }
        steps += "输入框定位：通过"

        inputNode.prepareForTextInput()
        steps += "输入框准备：已执行 focus/click"

        val setTextSuccess = inputNode.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    trimmed
                )
            }
        )
        steps += "ACTION_SET_TEXT：${if (setTextSuccess) "成功" else "失败"}"
        if (setTextSuccess && isInputTextConfirmed(trimmed)) {
            val result = AutofillAttemptResult(
                success = true,
                message = "已通过 ACTION_SET_TEXT 写入并回读确认",
                category = AutofillFailureCategory.NONE,
                steps = steps + "回读确认：成功"
            )
            AccessibilityDebugStore.onAutofillAttempt(result, trimmed)
            return result
        }
        steps += "回读确认：SET_TEXT 后未确认"

        val pasteSuccess = pasteViaClipboard(trimmed)
        steps += "剪贴板粘贴：${if (pasteSuccess) "成功" else "失败"}"
        val pasteConfirmed = pasteSuccess && isInputTextConfirmed(trimmed)
        steps += if (pasteConfirmed) {
            "回读确认：粘贴后成功"
        } else {
            "回读确认：粘贴后未确认"
        }
        val result = when {
            pasteConfirmed -> {
                AutofillAttemptResult(
                    success = true,
                    message = "已通过剪贴板粘贴兜底并回读确认",
                    category = AutofillFailureCategory.NONE,
                    steps = steps
                )
            }

            pasteSuccess -> {
                AutofillAttemptResult(
                    success = true,
                    message = "已执行剪贴板粘贴，回读暂未确认，请检查输入框",
                    category = AutofillFailureCategory.READBACK_MISMATCH,
                    steps = steps
                )
            }

            setTextSuccess -> {
                AutofillAttemptResult(
                    success = true,
                    message = "ACTION_SET_TEXT 已执行，但回读暂未确认，请检查输入框",
                    category = AutofillFailureCategory.READBACK_MISMATCH,
                    steps = steps
                )
            }

            else -> {
                AutofillAttemptResult(
                    success = false,
                    message = "ACTION_SET_TEXT 与剪贴板粘贴均执行失败",
                    category = AutofillFailureCategory.PASTE_FAILED,
                    steps = steps
                )
            }
        }
        AccessibilityDebugStore.onAutofillAttempt(result, trimmed)
        return result
    }

    fun readInputDraft(): InputDraftReadResult {
        val steps = mutableListOf<String>()
        val root = rootInActiveWindow
        if (root == null) {
            return InputDraftReadResult(
                success = false,
                message = "当前窗口为空，无法读取输入框",
                category = AutofillFailureCategory.EMPTY_WINDOW,
                steps = steps + "窗口获取：失败，rootInActiveWindow 为空"
            )
        }
        steps += "窗口获取：通过"

        val inputNode = WeChatAccessibilityAnalyzer.findChatInputNode(root)
        if (inputNode == null) {
            return InputDraftReadResult(
                success = false,
                message = "未找到可用输入框",
                category = AutofillFailureCategory.INPUT_NOT_FOUND,
                steps = steps + "输入框定位：失败"
            )
        }
        steps += "输入框定位：通过"

        inputNode.refresh()
        val draft = inputNode.text?.toString()?.trim().orEmpty()
        if (draft.isBlank()) {
            return InputDraftReadResult(
                success = false,
                message = "输入框里还没有可润色的文字",
                category = AutofillFailureCategory.EMPTY_TEXT,
                steps = steps + "草稿读取：为空"
            )
        }

        return InputDraftReadResult(
            success = true,
            text = draft,
            message = "已读取输入框草稿",
            category = AutofillFailureCategory.NONE,
            steps = steps + "草稿读取：成功，长度 ${draft.length}"
        )
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

    private fun eventTypeName(eventType: Int): String = when (eventType) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "TYPE_WINDOW_CONTENT_CHANGED"
        AccessibilityEvent.TYPE_VIEW_CLICKED -> "TYPE_VIEW_CLICKED"
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TYPE_VIEW_TEXT_CHANGED"
        else -> "TYPE_$eventType"
    }

    private fun logWindowEventIfNeeded(
        eventName: String,
        packageName: String,
        className: String
    ) {
        val now = System.currentTimeMillis()
        val key = "$eventName|$packageName|$className"
        if (key == lastLoggedEventKey && now - lastLoggedEventAtMillis < EVENT_LOG_THROTTLE_MILLIS) {
            return
        }
        lastLoggedEventKey = key
        lastLoggedEventAtMillis = now
        Log.d(
            "AiReplayMate",
            "event=$eventName, package=$packageName, class=$className"
        )
    }

    private companion object {
        const val CLIP_LABEL = "AiReplayMate reply"
        const val EVENT_LOG_THROTTLE_MILLIS = 1_000L
        var lastLoggedEventAtMillis = 0L
        var lastLoggedEventKey = ""
    }
}
