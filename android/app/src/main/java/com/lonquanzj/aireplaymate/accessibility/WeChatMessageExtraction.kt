package com.lonquanzj.aireplaymate.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

internal fun detectConversationTitle(collectedTexts: List<NodeText>): String? {
    return collectedTexts
        .filter { it.top in 0..180 && !it.isLikelyControl }
        .map { it.text }
        .firstOrNull { text ->
            text.length in 2..24 &&
                text !in blockedUiTexts &&
                !text.contains(":") &&
            !text.contains("\uFF1A") &&
            !text.contains("\u5fae\u4fe1")
        }
}

internal fun extractMessages(
    collectedTexts: List<NodeText>,
    title: String?,
    rootBounds: Rect,
    inputNode: AccessibilityNodeInfo?
): List<ChatMessage> {
    val inputBounds = inputNode?.let { Rect().also(it::getBoundsInScreen) }
    val orderedMessages = collectedTexts
        .sortedWith(compareBy<NodeText> { it.top }.thenBy { it.left })
        .mapNotNull { item ->
            val text = item.text.trim()
            if (!item.isMessageCandidate(title, inputBounds, rootBounds)) return@mapNotNull null
            item.copy(text = text)
        }

    return orderedMessages
        .takeLast(MAX_EXTRACTED_MESSAGES)
        .mapIndexed { index, item ->
            val role = inferRole(item, rootBounds)
            ChatMessage(
                id = stableMessageId(index, item.text),
                role = role,
                content = item.text,
                timestamp = null,
                source = MessageSource.ACCESSIBILITY,
                confidence = confidenceFor(item, role),
                boundsHint = item.boundsHint
            )
        }
}

private fun inferRole(
    item: NodeText,
    rootBounds: Rect
): ChatRole {
    val text = item.text
    if (systemHintRegex.containsMatchIn(text)) return ChatRole.SYSTEM

    val rootWidth = rootBounds.width().takeIf { it > 0 } ?: return ChatRole.UNKNOWN
    val centerX = item.centerX - rootBounds.left
    val leftRatio = (item.left - rootBounds.left).toFloat() / rootWidth
    val rightRatio = (item.right - rootBounds.left).toFloat() / rootWidth
    val centerRatio = centerX.toFloat() / rootWidth

    return when {
        rightRatio > 0.66f && centerRatio > 0.48f -> ChatRole.ME
        leftRatio < 0.40f && centerRatio < 0.58f -> ChatRole.FRIEND
        centerRatio > 0.60f -> ChatRole.ME
        centerRatio < 0.50f -> ChatRole.FRIEND
        item.isCenteredSystemLike(rootBounds) -> ChatRole.UNKNOWN
        else -> ChatRole.UNKNOWN
    }
}

private fun confidenceFor(
    item: NodeText,
    role: ChatRole
): Float {
    val base = when (role) {
        ChatRole.ME,
        ChatRole.FRIEND -> 0.76f
        ChatRole.SYSTEM -> 0.84f
        ChatRole.UNKNOWN -> 0.45f
    }
    val sourcePenalty = if (item.source == NodeTextSource.CONTENT_DESCRIPTION) 0.08f else 0f
    val controlPenalty = if (item.isLikelyControl) 0.18f else 0f
    return (base - sourcePenalty - controlPenalty).coerceIn(0f, 1f)
}

private fun stableMessageId(
    index: Int,
    content: String
): String {
    val hash = content.hashCode().toUInt().toString(16)
    return "accessibility_${index}_$hash"
}

private fun NodeText.isMessageCandidate(
    title: String?,
    inputBounds: Rect?,
    rootBounds: Rect
): Boolean {
    val cleanText = text.trim()
    if (cleanText.isEmpty()) return false
    if (title != null && cleanText == title && isLikelyChatTitle()) return false
    if (cleanText.length > MAX_MESSAGE_TEXT_LENGTH) return false
    if (top < rootBounds.top + MIN_MESSAGE_TOP_OFFSET) return false
    if (inputBounds != null && bottom >= inputBounds.top - INPUT_AREA_PADDING) return false
    if (isImagePlaceholder) return false
    if (isLikelyControl) return false
    if (timestampRegex.matches(cleanText)) return false
    if (dateRegex.matches(cleanText)) return false
    if (emojiOnlyRegex.matches(cleanText)) return false
    if (badgeRegex.matches(cleanText)) return false
    if (cleanText in overlayNoiseExactTexts) return false
    if (overlayNoiseFragments.any { cleanText.contains(it) }) return false
    if (className.contains("TextView", ignoreCase = true).not() &&
        source == NodeTextSource.CONTENT_DESCRIPTION &&
        cleanText.length <= 2
    ) {
        return false
    }
    return true
}

private fun NodeText.isCenteredSystemLike(rootBounds: Rect): Boolean {
    val rootWidth = rootBounds.width().takeIf { it > 0 } ?: return false
    val centerRatio = (centerX - rootBounds.left).toFloat() / rootWidth
    val widthRatio = width.toFloat() / rootWidth
    return centerRatio in 0.42f..0.58f && widthRatio < 0.72f && text.length <= 40
}

private fun NodeText.isLikelyChatTitle(): Boolean {
    return top in 0..180 &&
        width in 40..320 &&
        left >= 260 &&
        right <= 820 &&
        !className.contains("ImageView", ignoreCase = true)
}

internal const val VISIBLE_TEXT_SAMPLE_LIMIT = 6

private const val MAX_EXTRACTED_MESSAGES = 12
private const val MAX_MESSAGE_TEXT_LENGTH = 180
private const val MIN_MESSAGE_TOP_OFFSET = 132
private const val INPUT_AREA_PADDING = 24

internal val blockedUiTexts = setOf(
    "\u53d1\u9001",
    "\u66f4\u591a\u529f\u80fd",
    "\u6309\u4f4f\u8bf4\u8bdd",
    "\u8bed\u97f3\u8f93\u5165",
    "\u5207\u6362\u5230\u6309\u4f4f\u8bf4\u8bdd",
    "\u5207\u6362\u5230\u952e\u76d8",
    "\u8868\u60c5",
    "\u62cd\u6444",
    "\u7167\u7247",
    "\u4f4d\u7f6e",
    "\u7ea2\u5305",
    "\u8f6c\u8d26",
    "\u6536\u85cf",
    "\u8bed\u97f3\u901a\u8bdd",
    "\u89c6\u9891\u901a\u8bdd",
    "+",
    "\u5fae\u4fe1",
    "\u8fd4\u56de"
)

internal val blockedUiTextFragments = listOf(
    "\u5207\u6362\u5230",
    "\u66f4\u591a",
    "\u804a\u5929\u4fe1\u606f",
    "\u6dfb\u52a0\u5230\u901a\u8baf\u5f55",
    "\u6d88\u606f\u514d\u6253\u6270"
)

private val timestampRegex = Regex(
    """^((\u51cc\u6668|\u65e9\u4e0a|\u4e0a\u5348|\u4e2d\u5348|\u4e0b\u5348|\u665a\u4e0a)\s*)?\d{1,2}[:\uFF1A]\d{2}$"""
)
private val dateRegex = Regex(
    """^((\d{4}\u5e74)?\d{1,2}\u6708\d{1,2}\u65e5|\u5468[\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u65e5\u5929]|\u661f\u671f[\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u65e5\u5929]|\u6628\u5929|\u4eca\u5929|\u524d\u5929)(\s*((\u51cc\u6668|\u65e9\u4e0a|\u4e0a\u5348|\u4e2d\u5348|\u4e0b\u5348|\u665a\u4e0a)\s*)?\d{1,2}[:\uFF1A]\d{2})?$"""
)
private val badgeRegex = Regex("^\\d{1,3}$")
private val emojiOnlyRegex = Regex("^[\\p{So}\\p{Cn}]+$")
private val systemHintRegex = Regex("\u64a4\u56de|\u4ee5\u4e0a\u662f|\u4ee5\u4e0b\u662f|\u7cfb\u7edf")
private val overlayNoiseExactTexts = setOf(
    "浮窗",
    "聊天预览",
    "无障碍服务",
    "悬浮窗权限",
    "服务状态",
    "气泡视图",
    "更新时间",
    "启动/刷新气泡",
    "停止气泡",
    "关于",
    "项目说明与使用边界",
    "主界面",
    "回复风格",
    "LLM 设置",
    "高级调试"
)
private val overlayNoiseFragments = listOf(
    "当前会把这段上下文送去生成",
    "AI 小气泡正在显示",
    "若微信里看不到",
    "可以从真实微信聊天页触发生成"
)
