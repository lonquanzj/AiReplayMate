package com.lonquanzj.aireplaymate.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class ReplyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AiReplayMate", "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString().orEmpty()
        val className = event.className?.toString().orEmpty()

        Log.d(
            "AiReplayMate",
            "event=${event.eventType}, package=$packageName, class=$className"
        )
    }

    override fun onInterrupt() {
        Log.d("AiReplayMate", "Accessibility service interrupted")
    }
}
