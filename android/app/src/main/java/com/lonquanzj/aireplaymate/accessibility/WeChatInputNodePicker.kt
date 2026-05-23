package com.lonquanzj.aireplaymate.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

internal fun pickChatInputNode(editableNodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
    return editableNodes
        .maxByOrNull(::chatInputScore)
        ?: editableNodes.firstOrNull()
}

private fun chatInputScore(node: AccessibilityNodeInfo): Int {
    val hint = node.hintText?.toString()?.trim().orEmpty()
    val text = node.text?.toString()?.trim().orEmpty()
    val className = node.className?.toString().orEmpty()
    val bounds = Rect().also(node::getBoundsInScreen)

    var score = 0
    if (className.contains("EditText")) score += 4
    if (hint.contains("\u8f93\u5165") || hint.contains("\u6d88\u606f")) score += 10
    if (text.contains("\u8f93\u5165") || text.contains("\u6d88\u606f")) score += 8
    if (hint.contains("\u641c\u7d22") || text.contains("\u641c\u7d22")) score -= 6

    // WeChat chat input is usually closer to the bottom.
    score += (bounds.top / 200).coerceAtMost(10)
    return score
}
