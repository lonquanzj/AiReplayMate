package com.lonquanzj.aireplaymate.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

internal data class NodeText(
    val text: String,
    val source: NodeTextSource,
    val className: String,
    val isEditable: Boolean,
    val isClickable: Boolean,
    val isImagePlaceholder: Boolean = false,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val centerX: Int = left + ((right - left) / 2)
    val width: Int = right - left
    val boundsHint: String = "$left,$top,$right,$bottom"
    val isLikelyControl: Boolean =
        isEditable ||
            className.contains("Button", ignoreCase = true) ||
            (className.contains("ImageView", ignoreCase = true) && !isImagePlaceholder) ||
            text in blockedUiTexts ||
            blockedUiTextFragments.any { text.contains(it) }
}

internal enum class NodeTextSource {
    TEXT,
    CONTENT_DESCRIPTION
}

internal const val IMAGE_PLACEHOLDER_TEXT = "[图片]"

internal class WindowNodeSignals {
    val collectedTexts = mutableListOf<NodeText>()
    val editableNodes = mutableListOf<AccessibilityNodeInfo>()
    val visibleTexts = linkedSetOf<String>()
    var editableNodeCount = 0
}

internal fun collectWindowSignals(
    node: AccessibilityNodeInfo,
    signals: WindowNodeSignals
) {
    val bounds = Rect().also(node::getBoundsInScreen)
    val text = node.text?.toString()?.trim().orEmpty()
    val description = node.contentDescription?.toString()?.trim().orEmpty()
    val className = node.className?.toString().orEmpty()

    if (node.isEditable || className.contains("EditText")) {
        signals.editableNodes += node
        signals.editableNodeCount += 1
    }

    if (text.isNotEmpty()) {
        signals.visibleTexts += text
        signals.collectedTexts += nodeTextFrom(text, NodeTextSource.TEXT, className, node, bounds)
    }

    if (description.isNotEmpty()) {
        signals.visibleTexts += description
        signals.collectedTexts += nodeTextFrom(description, NodeTextSource.CONTENT_DESCRIPTION, className, node, bounds)
    }

    for (index in 0 until node.childCount) {
        val child = node.getChild(index) ?: continue
        collectWindowSignals(child, signals)
    }
}

internal fun collectNodeSignals(
    node: AccessibilityNodeInfo,
    collectedTexts: MutableList<NodeText>,
    editableNodes: MutableList<AccessibilityNodeInfo>
) {
    val bounds = Rect().also(node::getBoundsInScreen)
    val text = node.text?.toString()?.trim().orEmpty()
    val description = node.contentDescription?.toString()?.trim().orEmpty()
    val className = node.className?.toString().orEmpty()

    if (node.isEditable || className.contains("EditText")) {
        editableNodes += node
    }

    if (text.isNotEmpty()) {
        collectedTexts += nodeTextFrom(text, NodeTextSource.TEXT, className, node, bounds)
    }

    if (description.isNotEmpty()) {
        collectedTexts += nodeTextFrom(description, NodeTextSource.CONTENT_DESCRIPTION, className, node, bounds)
    }

    for (index in 0 until node.childCount) {
        val child = node.getChild(index) ?: continue
        collectNodeSignals(child, collectedTexts, editableNodes)
    }
}

internal fun collectEditableNodes(
    node: AccessibilityNodeInfo,
    editableNodes: MutableList<AccessibilityNodeInfo>
) {
    val className = node.className?.toString().orEmpty()
    if (node.isEditable || className.contains("EditText")) {
        editableNodes += node
    }

    for (index in 0 until node.childCount) {
        val child = node.getChild(index) ?: continue
        collectEditableNodes(child, editableNodes)
    }
}

private fun nodeTextFrom(
    text: String,
    source: NodeTextSource,
    className: String,
    node: AccessibilityNodeInfo,
    bounds: Rect,
    isImagePlaceholder: Boolean = false
): NodeText {
    return NodeText(
        text = text,
        source = source,
        className = className,
        isEditable = node.isEditable,
        isClickable = node.isClickable,
        isImagePlaceholder = isImagePlaceholder,
        left = bounds.left,
        top = bounds.top,
        right = bounds.right,
        bottom = bounds.bottom
    )
}

