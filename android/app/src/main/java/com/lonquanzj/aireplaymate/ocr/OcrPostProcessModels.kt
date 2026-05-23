package com.lonquanzj.aireplaymate.ocr

import android.graphics.Rect
import com.lonquanzj.aireplaymate.accessibility.ChatMessage

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
