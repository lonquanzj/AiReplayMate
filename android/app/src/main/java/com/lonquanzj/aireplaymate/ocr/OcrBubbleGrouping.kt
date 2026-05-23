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
        val horizontalOverlap = minOf(bounds.right, line.bounds.right) -
            maxOf(bounds.left, line.bounds.left)
        return verticalGap in 0..maxGap && horizontalOverlap > 0
    }

    fun add(line: OcrLineCandidate) {
        lines += line
        bounds.union(line.bounds)
    }

    fun toChatMessage(): ChatMessage? {
        val content = lines.joinToString(separator = "\n") { it.text }.trim()
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
