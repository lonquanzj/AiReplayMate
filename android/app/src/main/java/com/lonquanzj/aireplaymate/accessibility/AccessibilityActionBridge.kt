package com.lonquanzj.aireplaymate.accessibility

import java.lang.ref.WeakReference

data class AutofillAttemptResult(
    val success: Boolean,
    val message: String
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
                message = "无障碍服务未连接，无法执行真实填入"
            )
            AccessibilityDebugStore.onAutofillAttempt(result.message, text)
            return result
        }

        return service.performAutofill(text)
    }
}
