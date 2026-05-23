package com.lonquanzj.aireplaymate.accessibility

import android.util.Log
import android.view.accessibility.AccessibilityEvent

internal fun eventTypeName(eventType: Int): String = when (eventType) {
    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "TYPE_WINDOW_CONTENT_CHANGED"
    AccessibilityEvent.TYPE_VIEW_CLICKED -> "TYPE_VIEW_CLICKED"
    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TYPE_VIEW_TEXT_CHANGED"
    else -> "TYPE_$eventType"
}

internal fun logWindowEventIfNeeded(
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

private const val EVENT_LOG_THROTTLE_MILLIS = 1_000L
private var lastLoggedEventAtMillis = 0L
private var lastLoggedEventKey = ""
