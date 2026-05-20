package com.lonquanzj.aireplaymate.accessibility

import java.lang.ref.WeakReference

enum class AutofillFailureCategory(
    val label: String
) {
    NONE("无"),
    SERVICE_DISCONNECTED("无障碍未连接"),
    EMPTY_TEXT("内容为空"),
    EMPTY_WINDOW("窗口为空"),
    INPUT_NOT_FOUND("未找到输入框"),
    SET_TEXT_FAILED("SET_TEXT 失败"),
    PASTE_FAILED("粘贴失败"),
    READBACK_MISMATCH("回读不一致"),
    UNKNOWN("未知")
}

data class AutofillAttemptResult(
    val success: Boolean,
    val message: String,
    val category: AutofillFailureCategory = if (success) {
        AutofillFailureCategory.NONE
    } else {
        AutofillFailureCategory.UNKNOWN
    },
    val steps: List<String> = emptyList()
)

data class InputDraftReadResult(
    val success: Boolean,
    val text: String = "",
    val message: String,
    val category: AutofillFailureCategory = if (success) {
        AutofillFailureCategory.NONE
    } else {
        AutofillFailureCategory.UNKNOWN
    },
    val steps: List<String> = emptyList()
)

data class WindowInspectionSnapshot(
    val success: Boolean,
    val state: AccessibilityDebugState = AccessibilityDebugState(),
    val message: String = ""
)

object AccessibilityActionBridge {
    private var serviceRef: WeakReference<ReplyAccessibilityService>? = null

    fun attach(service: ReplyAccessibilityService) {
        serviceRef = WeakReference(service)
    }

    fun detach(service: ReplyAccessibilityService) {
        if (serviceRef?.get() === service) {
            serviceRef = null
        }
    }

    fun tryAutofill(text: String): AutofillAttemptResult {
        val service = serviceRef?.get()
        if (service == null) {
            val result = AutofillAttemptResult(
                success = false,
                message = "无障碍服务未连接，无法执行真实填入",
                category = AutofillFailureCategory.SERVICE_DISCONNECTED,
                steps = listOf("服务连接：失败")
            )
            AccessibilityDebugStore.onAutofillAttempt(result, text)
            return result
        }

        return service.performAutofill(text)
    }

    fun tryReadInputDraft(): InputDraftReadResult {
        val service = serviceRef?.get()
        if (service == null) {
            return InputDraftReadResult(
                success = false,
                message = "无障碍服务未连接，无法读取输入框",
                category = AutofillFailureCategory.SERVICE_DISCONNECTED,
                steps = listOf("服务连接：失败")
            )
        }

        return service.readInputDraft()
    }

    fun tryInspectCurrentWindow(): WindowInspectionSnapshot {
        val service = serviceRef?.get()
        if (service == null) {
            return WindowInspectionSnapshot(
                success = false,
                message = "无障碍服务未连接，无法刷新当前窗口"
            )
        }

        return service.inspectCurrentWindow()
    }
}
