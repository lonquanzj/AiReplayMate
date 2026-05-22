package com.lonquanzj.aireplaymate.ocr

import com.lonquanzj.aireplaymate.accessibility.ChatMessage

enum class OcrAttemptCategory(
    val label: String
) {
    NONE("无"),
    NOT_CONFIGURED("未配置"),
    CAPTURE_UNAVAILABLE("截图不可用"),
    ENGINE_UNAVAILABLE("识别引擎不可用"),
    NO_TEXT("未识别到文本"),
    SUCCESS("成功"),
    UNKNOWN("未知")
}

data class OcrAttemptResult(
    val success: Boolean,
    val category: OcrAttemptCategory,
    val message: String,
    val messages: List<ChatMessage> = emptyList(),
    val filterSummaries: List<OcrFilterSummary> = emptyList(),
    val steps: List<String> = emptyList(),
    val engineConfigured: Boolean = false
)

interface OcrEngine {
    suspend fun recognizeChatMessages(
        targetApp: String,
        reason: String
    ): OcrAttemptResult
}
