package com.lonquanzj.aireplaymate.ui

import androidx.compose.material3.Text
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugState
import com.lonquanzj.aireplaymate.llm.LlmDebugState
import com.lonquanzj.aireplaymate.ocr.OcrDebugState
import com.lonquanzj.aireplaymate.ocr.OcrScreenCaptureState
import com.lonquanzj.aireplaymate.overlay.OverlayDiagnosticsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun buildAccessibilityDebugSnapshot(
    debugState: AccessibilityDebugState,
    ocrDebugState: OcrDebugState
): String {
    return buildString {
        appendLine("AiReplayMate Accessibility Debug Snapshot")
        appendLine("updatedAt=${formatTimestamp(debugState.updatedAtMillis)}")
        appendLine("serviceConnected=${debugState.serviceConnected}")
        appendLine("lastEvent=${debugState.lastEventName}")
        appendLine("package=${debugState.packageName.ifEmpty { "N/A" }}")
        appendLine("class=${debugState.className.ifEmpty { "N/A" }}")
        appendLine("editableNodeCount=${debugState.editableNodeCount}")
        appendLine("isWechatPackage=${debugState.isWechatPackage}")
        appendLine("looksLikeChatPage=${debugState.looksLikeChatPage}")
        appendLine("conversationTitle=${debugState.conversationTitle ?: "N/A"}")
        appendLine("inputNodeFound=${debugState.inputNodeFound}")
        appendLine("inputNodeHint=${debugState.inputNodeHint ?: "N/A"}")
        appendLine("chatDetectionReason=${debugState.chatDetectionReason.ifEmpty { "N/A" }}")
        appendLine("lastAutofillStatus=${debugState.lastAutofillStatus}")
        appendLine("lastAutofillCategory=${debugState.lastAutofillCategory.label}")
        appendLine("lastAutofillPreview=${debugState.lastAutofillPreview ?: "N/A"}")
        appendLine("ocrCategory=${ocrDebugState.lastCategory.label}")
        appendLine("ocrStatus=${ocrDebugState.lastStatus}")
        appendLine("ocrReason=${ocrDebugState.lastReason.ifBlank { "N/A" }}")
        appendLine()
        appendLine("Autofill Steps:")
        if (debugState.lastAutofillSteps.isEmpty()) {
            appendLine("N/A")
        } else {
            debugState.lastAutofillSteps.forEach(::appendLine)
        }
        appendLine()
        appendLine("Extracted Messages:")
        if (debugState.extractedMessageDebugPreviews.isEmpty()) {
            appendLine("N/A")
        } else {
            debugState.extractedMessageDebugPreviews.forEach(::appendLine)
        }
        appendLine()
        appendLine("Visible Text Sample:")
        if (debugState.visibleTextSample.isEmpty()) {
            appendLine("N/A")
        } else {
            debugState.visibleTextSample.forEachIndexed { index, sample ->
                appendLine("${index + 1}. $sample")
            }
        }
    }
}

internal fun buildOcrDebugSnapshot(
    debugState: OcrDebugState,
    screenCaptureState: OcrScreenCaptureState
): String {
    return buildString {
        appendLine("AiReplayMate OCR Debug Snapshot")
        appendLine("updatedAt=${formatTimestamp(debugState.updatedAtMillis)}")
        appendLine("captureSource=accessibility")
        appendLine("screenCaptureStatus=${screenCaptureState.status.label}")
        appendLine("screenCaptureMessage=${screenCaptureState.message}")
        appendLine("screenCaptureSize=${screenCaptureState.sizeLabel}")
        appendLine("screenCaptureRowStride=${screenCaptureState.rowStride ?: "N/A"}")
        appendLine("screenCapturePixelStride=${screenCaptureState.pixelStride ?: "N/A"}")
        appendLine("screenCaptureFrameStats=${screenCaptureState.frameStats.ifBlank { "N/A" }}")
        appendLine("screenCaptureDebugImage=${screenCaptureState.debugImagePath.ifBlank { "N/A" }}")
        appendLine("screenCaptureUpdatedAt=${formatTimestamp(screenCaptureState.updatedAtMillis)}")
        appendLine("engineConfigured=${debugState.engineConfigured}")
        appendLine("category=${debugState.lastCategory.label}")
        appendLine("status=${debugState.lastStatus}")
        appendLine("reason=${debugState.lastReason.ifBlank { "N/A" }}")
        appendLine("targetApp=${debugState.targetApp.ifBlank { "N/A" }}")
        appendLine("filterSummaryCount=${debugState.filterSummaries.size}")
        appendLine()
        appendLine("OCR Steps:")
        if (debugState.steps.isEmpty()) {
            appendLine("N/A")
        } else {
            debugState.steps.forEach(::appendLine)
        }
        appendLine()
        appendLine("Screen Capture Steps:")
        if (screenCaptureState.steps.isEmpty()) {
            appendLine("N/A")
        } else {
            screenCaptureState.steps.forEach(::appendLine)
        }
        appendLine()
        appendLine("OCR Filter Summary:")
        if (debugState.filterSummaryPreviews.isEmpty()) {
            appendLine("N/A")
        } else {
            debugState.filterSummaryPreviews.forEach(::appendLine)
        }
        appendLine()
        appendLine("OCR Messages:")
        if (debugState.extractedMessagePreviews.isEmpty()) {
            appendLine("N/A")
        } else {
            debugState.extractedMessagePreviews.forEach(::appendLine)
        }
    }
}

internal fun buildLlmDebugSnapshot(debugState: LlmDebugState): String {
    return buildString {
        appendLine("AiReplayMate LLM Debug Snapshot")
        appendLine("updatedAt=${formatTimestamp(debugState.updatedAtMillis)}")
        appendLine("phase=${debugState.phase.label}")
        appendLine("category=${debugState.failureCategory.label}")
        appendLine("baseUrl=${debugState.baseUrl.ifBlank { "N/A" }}")
        appendLine("model=${debugState.model.ifBlank { "N/A" }}")
        appendLine("httpStatus=${debugState.httpStatus ?: "N/A"}")
        appendLine("candidateCount=${debugState.candidateCount}")
        appendLine("errorSummary=${debugState.errorSummary ?: "N/A"}")
        appendLine("recoveryHint=${debugState.recoveryHint}")
        appendLine("requestPreview=${debugState.requestPreview ?: "N/A"}")
        appendLine("responsePreview=${debugState.responsePreview ?: "N/A"}")
        appendLine()
        appendLine("LLM History:")
        if (debugState.history.isEmpty()) {
            appendLine("N/A")
        } else {
            debugState.history.forEachIndexed { index, entry ->
                appendLine(
                    "${index + 1}. ${formatTimestamp(entry.timestampMillis)} " +
                        "${entry.phase.label}/${entry.category.label} " +
                        "http=${entry.httpStatus ?: "N/A"} " +
                        "candidates=${entry.candidateCount} " +
                        "model=${entry.model.ifBlank { "N/A" }} " +
                        "baseUrl=${entry.baseUrl.ifBlank { "N/A" }} " +
                        "summary=${entry.summary} " +
                        "recoveryHint=${entry.recoveryHint}"
                )
            }
        }
    }
}

internal fun buildOverlayDebugSnapshot(debugState: OverlayDiagnosticsState): String {
    return buildString {
        appendLine("AiReplayMate Overlay Debug Snapshot")
        appendLine("updatedAt=${formatTimestamp(debugState.updatedAtMillis)}")
        appendLine("phase=${debugState.phase.label}")
        appendLine("status=${debugState.status}")
        appendLine("accessibilityMessageCount=${debugState.accessibilityMessageCount}")
        appendLine("ocrMessageCount=${debugState.ocrMessageCount}")
        appendLine("mergedMessageCount=${debugState.mergedMessageCount}")
        appendLine("candidateCount=${debugState.candidateCount}")
        appendLine("candidateSource=${debugState.candidateSource}")
        appendLine("usedOcr=${debugState.usedOcr}")
        appendLine("usedLocalFallback=${debugState.usedLocalFallback}")
        appendLine("lastFailure=${debugState.lastFailure ?: "N/A"}")
        appendLine()
        appendLine("Overlay Steps:")
        if (debugState.steps.isEmpty()) {
            appendLine("N/A")
        } else {
            debugState.steps.forEach(::appendLine)
        }
    }
}

internal fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "暂无"
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
