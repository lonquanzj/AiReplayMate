package com.lonquanzj.aireplaymate.ocr

import android.graphics.Rect
import com.lonquanzj.aireplaymate.accessibility.ChatRole

internal fun OcrRecognizedLine.clean(
    screenWidth: Int,
    screenHeight: Int
): OcrCleanResult {
    val content = text.trim()
    if (content.length < MIN_TEXT_LENGTH) return dropped(OcrFilterReason.TOO_SHORT)
    if (content.length > MAX_TEXT_LENGTH) return dropped(OcrFilterReason.TOO_LONG)
    if (content.isLikelyChromeText()) return dropped(OcrFilterReason.CHROME_TEXT)
    if (content.isTimeOrBadgeText()) return dropped(OcrFilterReason.TIME_OR_BADGE)
    if (content.isLikelySystemNotice()) return dropped(OcrFilterReason.SYSTEM_NOTICE)
    if (content.isLikelyImageOrNonChatSnippet()) {
        return dropped(OcrFilterReason.IMAGE_OR_NON_CHAT_SNIPPET)
    }

    val safeBounds = bounds ?: return dropped(OcrFilterReason.MISSING_BOUNDS)
    if (safeBounds.width() <= 0 || safeBounds.height() <= 0) return dropped(OcrFilterReason.INVALID_BOUNDS)
    if (safeBounds.top < screenHeight * TOP_CHROME_RATIO) return dropped(OcrFilterReason.TOP_CHROME)
    if (safeBounds.bottom > screenHeight * BOTTOM_INPUT_RATIO) return dropped(OcrFilterReason.BOTTOM_INPUT)
    if (safeBounds.width() > screenWidth * MAX_LINE_WIDTH_RATIO) return dropped(OcrFilterReason.TOO_WIDE)

    val role = inferRole(safeBounds, screenWidth)
    return OcrCleanResult(
        candidate = OcrLineCandidate(
            text = content,
            role = role,
            bounds = safeBounds
        )
    )
}

private fun OcrRecognizedLine.dropped(reason: OcrFilterReason): OcrCleanResult {
    return OcrCleanResult(
        dropped = OcrDroppedLine(
            reason = reason,
            text = text
        )
    )
}

private fun inferRole(
    bounds: Rect,
    screenWidth: Int
): ChatRole {
    val centerRatio = bounds.centerX().toFloat() / screenWidth.coerceAtLeast(1)
    return when {
        centerRatio >= ME_CENTER_RATIO -> ChatRole.ME
        centerRatio <= FRIEND_CENTER_RATIO -> ChatRole.FRIEND
        else -> ChatRole.UNKNOWN
    }
}

private fun String.isLikelyChromeText(): Boolean {
    return chromeTexts.any { equals(it, ignoreCase = true) } ||
        chromeTextFragments.any { contains(it, ignoreCase = true) }
}

private fun String.isTimeOrBadgeText(): Boolean {
    return timeRegex.matches(this) ||
        dateRegex.matches(this) ||
        allPunctuationRegex.matches(this) ||
        unreadBadgeRegex.matches(this)
}

private fun String.isLikelySystemNotice(): Boolean {
    return systemNoticeFragments.any { contains(it, ignoreCase = true) } ||
        withdrawNoticeRegex.containsMatchIn(this)
}

private fun String.isLikelyImageOrNonChatSnippet(): Boolean {
    return shortTimestampWithSymbolRegex.matches(this) ||
        isShortSymbolDigitNoise()
}

private fun String.isShortSymbolDigitNoise(): Boolean {
    val normalized = normalizeText(this)
    if (normalized.length > MAX_SHORT_NOISE_LENGTH) return false
    if (cjkRegex.containsMatchIn(normalized)) return false
    val hasDigit = normalized.any { it.isDigit() }
    val hasSymbol = normalized.any { !it.isLetterOrDigit() && !it.isWhitespace() }
    return hasDigit && hasSymbol
}

internal fun normalizeText(text: String): String {
    return whitespaceRegex.replace(text.trim(), " ")
}
