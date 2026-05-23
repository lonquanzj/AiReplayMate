package com.lonquanzj.aireplaymate.accessibility

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

internal fun ReplyAccessibilityService.performAutofillText(text: String): AutofillAttemptResult {
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

private fun AccessibilityNodeInfo.prepareForTextInput() {
    performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    performAction(AccessibilityNodeInfo.ACTION_CLICK)
}

private fun ReplyAccessibilityService.pasteViaClipboard(text: String): Boolean {
    val root = rootInActiveWindow ?: return false
    val inputNode = WeChatAccessibilityAnalyzer.findChatInputNode(root) ?: return false
    inputNode.prepareForTextInput()

    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, text))
    return inputNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
}

private fun ReplyAccessibilityService.isInputTextConfirmed(expected: String): Boolean {
    val root = rootInActiveWindow ?: return false
    val inputNode = WeChatAccessibilityAnalyzer.findChatInputNode(root) ?: return false
    inputNode.refresh()
    val actual = inputNode.text?.toString()?.trim().orEmpty()
    return actual == expected || actual.endsWith(expected)
}

private const val CLIP_LABEL = "AiReplayMate reply"
