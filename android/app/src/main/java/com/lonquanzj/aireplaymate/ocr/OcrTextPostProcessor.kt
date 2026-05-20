package com.lonquanzj.aireplaymate.ocr

import android.graphics.Rect
import com.lonquanzj.aireplaymate.accessibility.ChatMessage
import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.accessibility.MessageSource
import java.util.UUID

data class OcrRecognizedLine(
    val text: String,
    val bounds: Rect?
)

data class OcrPostProcessResult(
    val messages: List<ChatMessage>,
    val rawLineCount: Int,
    val keptLineCount: Int,
    val droppedLineCount: Int
)

object OcrTextPostProcessor {
    fun toChatMessages(
        lines: List<OcrRecognizedLine>,
        screenWidth: Int,
        screenHeight: Int
    ): OcrPostProcessResult {
        val cleanedLines = lines
            .mapNotNull { it.clean(screenWidth, screenHeight) }
            .distinctBy { normalizeText(it.text) }
            .sortedWith(compareBy<OcrLineCandidate> { it.bounds.top }.thenBy { it.bounds.left })

        val grouped = cleanedLines
            .fold(mutableListOf<OcrBubbleCandidate>()) { groups, line ->
                val last = groups.lastOrNull()
                if (last != null && last.canMerge(line, screenHeight)) {
                    last.add(line)
                } else {
                    groups += OcrBubbleCandidate(line)
                }
                groups
            }

        val messages = grouped
            .mapNotNull { it.toChatMessage() }
            .takeLast(MAX_OCR_MESSAGES)

        return OcrPostProcessResult(
            messages = messages,
            rawLineCount = lines.size,
            keptLineCount = cleanedLines.size,
            droppedLineCount = lines.size - cleanedLines.size
        )
    }

    private fun OcrRecognizedLine.clean(
        screenWidth: Int,
        screenHeight: Int
    ): OcrLineCandidate? {
        val content = text.trim()
        if (content.length < MIN_TEXT_LENGTH) return null
        if (content.length > MAX_TEXT_LENGTH) return null
        if (content.isLikelyChromeText()) return null
        if (content.isTimeOrBadgeText()) return null

        val safeBounds = bounds ?: return null
        if (safeBounds.width() <= 0 || safeBounds.height() <= 0) return null
        if (safeBounds.top < screenHeight * TOP_CHROME_RATIO) return null
        if (safeBounds.bottom > screenHeight * BOTTOM_INPUT_RATIO) return null
        if (safeBounds.width() > screenWidth * MAX_LINE_WIDTH_RATIO) return null

        val role = inferRole(safeBounds, screenWidth)
        return OcrLineCandidate(
            text = content,
            role = role,
            bounds = safeBounds
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

    private fun normalizeText(text: String): String {
        return whitespaceRegex.replace(text.trim(), " ")
    }

    private data class OcrLineCandidate(
        val text: String,
        val role: ChatRole,
        val bounds: Rect
    )

    private class OcrBubbleCandidate(line: OcrLineCandidate) {
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

    private val chromeTexts = setOf(
        "微信",
        "发送",
        "按住 说话",
        "按住说话",
        "语音输入",
        "表情",
        "更多",
        "相册",
        "拍摄",
        "位置",
        "红包",
        "转账",
        "收藏",
        "聊天信息"
    )
    private val chromeTextFragments = listOf(
        "正在输入",
        "条新消息",
        "以上是打招呼",
        "以下是新消息",
        "轻触输入",
        "切换到键盘"
    )
    private val whitespaceRegex = Regex("\\s+")
    private val timeRegex = Regex("""^(\d{1,2}:\d{2}|上午\s*\d{1,2}:\d{2}|下午\s*\d{1,2}:\d{2})$""")
    private val dateRegex = Regex("""^(\d{1,2}月\d{1,2}日|星期[一二三四五六日天]|昨天|今天|周[一二三四五六日天]).*$""")
    private val allPunctuationRegex = Regex("""^[\p{P}\p{S}\s]+$""")
    private val unreadBadgeRegex = Regex("""^\d{1,3}$""")

    private const val MIN_TEXT_LENGTH = 2
    private const val MAX_TEXT_LENGTH = 120
    private const val MAX_OCR_MESSAGES = 20
    private const val TOP_CHROME_RATIO = 0.10f
    private const val BOTTOM_INPUT_RATIO = 0.84f
    private const val MAX_LINE_WIDTH_RATIO = 0.86f
    private const val FRIEND_CENTER_RATIO = 0.46f
    private const val ME_CENTER_RATIO = 0.54f
    private const val MAX_BUBBLE_LINE_GAP_RATIO = 0.018f
    private const val ROLE_CONFIDENCE = 0.62f
    private const val UNKNOWN_ROLE_CONFIDENCE = 0.48f
}

