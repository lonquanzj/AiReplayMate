package com.lonquanzj.aireplaymate.ocr

import android.graphics.Rect
import com.lonquanzj.aireplaymate.accessibility.ChatMessage
import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.accessibility.MessageSource
import java.util.UUID

internal data class OcrLineCandidate(
    val text: String,
    val role: ChatRole,
    val bounds: Rect
)

internal data class OcrDroppedLine(
    val reason: OcrFilterReason,
    val text: String
)

internal data class OcrCleanResult(
    val candidate: OcrLineCandidate? = null,
    val dropped: OcrDroppedLine? = null
)

internal class OcrBubbleCandidate(line: OcrLineCandidate) {
    private val lines = mutableListOf(line)
    private var bounds = Rect(line.bounds)
    private val role: ChatRole = line.role

    fun canMerge(
        line: OcrLineCandidate,
        screenHeight: Int
    ): Boolean {
        if (line.role != role) return false
        if (role == ChatRole.UNKNOWN) return false
        val verticalGap = line.bounds.top - bounds.bottom
        val maxGap = (screenHeight * MAX_BUBBLE_LINE_GAP_RATIO).toInt().coerceAtLeast(20)
        val maxOverlap = (screenHeight * MAX_BUBBLE_OVERLAP_RATIO).toInt().coerceAtLeast(20)
        val horizontalOverlap = minOf(bounds.right, line.bounds.right) -
            maxOf(bounds.left, line.bounds.left)
        return verticalGap in -maxOverlap..maxGap && horizontalOverlap > 0
    }

    fun add(line: OcrLineCandidate) {
        val previous = lines.lastOrNull()
        if (previous != null && previous.isLikelyDuplicateOf(line)) {
            bounds.union(line.bounds)
            return
        }
        lines += line
        bounds.union(line.bounds)
    }

    fun toChatMessage(): ChatMessage? {
        val content = lines.toBubbleContent()
        if (content.length < MIN_TEXT_LENGTH) return null
        return ChatMessage(
            id = "ocr_${UUID.randomUUID()}",
            role = role,
            content = content,
            timestamp = null,
            source = MessageSource.OCR,
            confidence = if (role == ChatRole.UNKNOWN) {
                UNKNOWN_ROLE_CONFIDENCE
            } else {
                ROLE_CONFIDENCE
            },
            boundsHint = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}"
        )
    }
}

private fun List<OcrLineCandidate>.toBubbleContent(): String {
    val nonImageLines = filterNot { it.text == IMAGE_PLACEHOLDER_TEXT }
    if (nonImageLines.isEmpty()) {
        return IMAGE_PLACEHOLDER_TEXT
    }

    val hasImagePlaceholder = size != nonImageLines.size
    val texts = nonImageLines.map { it.text }.toMutableList()
    if (hasImagePlaceholder) {
        val lastIndex = texts.lastIndex
        if (!texts[lastIndex].endsWith(IMAGE_SOURCE_MARKER)) {
            texts[lastIndex] = texts[lastIndex] + IMAGE_SOURCE_MARKER
        }
    }

    return texts.joinToString(separator = "\n").trim()
}

private fun OcrLineCandidate.isLikelyDuplicateOf(other: OcrLineCandidate): Boolean {
    if (normalizeText(text) != normalizeText(other.text)) return false
    val centerDeltaX = kotlin.math.abs(bounds.centerX() - other.bounds.centerX())
    val centerDeltaY = kotlin.math.abs(bounds.centerY() - other.bounds.centerY())
    return centerDeltaX <= DUPLICATE_CENTER_DELTA_PX && centerDeltaY <= DUPLICATE_CENTER_DELTA_PX
}

private const val DUPLICATE_CENTER_DELTA_PX = 20
