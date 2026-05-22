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

enum class OcrFilterReason(
    val label: String
) {
    TOO_SHORT("文本过短"),
    TOO_LONG("文本过长"),
    CHROME_TEXT("疑似界面控件"),
    TIME_OR_BADGE("时间或角标"),
    MISSING_BOUNDS("缺少位置"),
    INVALID_BOUNDS("位置无效"),
    TOP_CHROME("顶部导航区"),
    BOTTOM_INPUT("底部输入区"),
    TOO_WIDE("文本行过宽"),
    SYSTEM_NOTICE("系统提示"),
    IMAGE_OR_NON_CHAT_SNIPPET("疑似图片或非聊天短片段"),
    DUPLICATE("重复文本")
}

data class OcrFilterSummary(
    val reason: OcrFilterReason,
    val count: Int,
    val samples: List<String> = emptyList()
) {
    val displayText: String
        get() = buildString {
            append("${reason.label} $count")
            if (samples.isNotEmpty()) {
                append("：")
                append(samples.joinToString(" / "))
            }
        }
}

data class OcrPostProcessResult(
    val messages: List<ChatMessage>,
    val rawLineCount: Int,
    val keptLineCount: Int,
    val droppedLineCount: Int,
    val filterSummaries: List<OcrFilterSummary> = emptyList()
)

object OcrTextPostProcessor {
    fun toChatMessages(
        lines: List<OcrRecognizedLine>,
        screenWidth: Int,
        screenHeight: Int
    ): OcrPostProcessResult {
        val cleanedResults = lines.map { it.clean(screenWidth, screenHeight) }
        val initiallyKept = cleanedResults.mapNotNull { it.candidate }
        val dropped = cleanedResults.mapNotNull { it.dropped }.toMutableList()
        val seenTexts = mutableSetOf<String>()
        val cleanedLines = initiallyKept
            .filter { candidate ->
                val normalized = normalizeText(candidate.text)
                val isNew = seenTexts.add(normalized)
                if (!isNew) {
                    dropped += OcrDroppedLine(
                        reason = OcrFilterReason.DUPLICATE,
                        text = candidate.text
                    )
                }
                isNew
            }
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
            droppedLineCount = dropped.size,
            filterSummaries = dropped.toFilterSummaries()
        )
    }

    private fun OcrRecognizedLine.clean(
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

    private fun normalizeText(text: String): String {
        return whitespaceRegex.replace(text.trim(), " ")
    }

    private data class OcrLineCandidate(
        val text: String,
        val role: ChatRole,
        val bounds: Rect
    )

    private data class OcrDroppedLine(
        val reason: OcrFilterReason,
        val text: String
    )

    private data class OcrCleanResult(
        val candidate: OcrLineCandidate? = null,
        val dropped: OcrDroppedLine? = null
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

    private fun List<OcrDroppedLine>.toFilterSummaries(): List<OcrFilterSummary> {
        return groupBy { it.reason }
            .map { (reason, lines) ->
                OcrFilterSummary(
                    reason = reason,
                    count = lines.size,
                    samples = lines
                        .map { it.text.toSampleText() }
                        .distinct()
                        .take(MAX_FILTER_SAMPLES_PER_REASON)
                )
            }
            .sortedWith(compareByDescending<OcrFilterSummary> { it.count }.thenBy { it.reason.ordinal })
    }

    private fun String.toSampleText(): String {
        val normalized = normalizeText(this)
        if (normalized.isBlank()) return "(空)"
        return normalized.take(MAX_FILTER_SAMPLE_LENGTH)
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
    private val systemNoticeFragments = listOf(
        "撤回了一条消息",
        "撒回了一条消息"
    )
    private val whitespaceRegex = Regex("\\s+")
    private val timeRegex = Regex("""^((凌晨|早上|上午|中午|下午|晚上)\s*)?\d{1,2}[:：]\d{2}$""")
    private val shortTimestampWithSymbolRegex = Regex("""^((凌晨|早上|上午|中午|下午|晚上)\s*)?\d{1,2}[:：]\d{2}[\s\p{P}\p{S}]+$""")
    private val dateRegex = Regex("""^(\d{1,2}月\d{1,2}日|星期[一二三四五六日天]|昨天|今天|周[一二三四五六日天]).*$""")
    private val allPunctuationRegex = Regex("""^[\p{P}\p{S}\s]+$""")
    private val unreadBadgeRegex = Regex("""^\d{1,3}$""")
    private val withdrawNoticeRegex = Regex("""[你我他她它对方]?.?[撤撒]回了?一条消息""")
    private val cjkRegex = Regex("""[\u4E00-\u9FFF]""")

    private const val MIN_TEXT_LENGTH = 2
    private const val MAX_TEXT_LENGTH = 120
    private const val MAX_SHORT_NOISE_LENGTH = 12
    private const val MAX_OCR_MESSAGES = 20
    private const val MAX_FILTER_SAMPLES_PER_REASON = 3
    private const val MAX_FILTER_SAMPLE_LENGTH = 24
    private const val TOP_CHROME_RATIO = 0.10f
    private const val BOTTOM_INPUT_RATIO = 0.84f
    private const val MAX_LINE_WIDTH_RATIO = 0.86f
    private const val FRIEND_CENTER_RATIO = 0.46f
    private const val ME_CENTER_RATIO = 0.54f
    private const val MAX_BUBBLE_LINE_GAP_RATIO = 0.018f
    private const val ROLE_CONFIDENCE = 0.62f
    private const val UNKNOWN_ROLE_CONFIDENCE = 0.48f
}

