package com.lonquanzj.aireplaymate.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
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

    fun inspectCurrentWindow(): WindowInspectionSnapshot = inspectCurrentWindowSnapshot()

    fun performAutofill(text: String): AutofillAttemptResult = performAutofillText(text)

    fun readInputDraft(): InputDraftReadResult = readCurrentInputDraft()

    suspend fun takeScreenshotBitmap(): AccessibilityScreenshotResult = takeAccessibilityScreenshotBitmap()
}
