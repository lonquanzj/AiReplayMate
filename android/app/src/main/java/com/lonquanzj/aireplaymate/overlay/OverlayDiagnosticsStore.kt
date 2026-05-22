package com.lonquanzj.aireplaymate.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OverlayRunPhase(
    val label: String
) {
    IDLE("未触发"),
    VALIDATING("校验页面"),
    BUILDING_CONTEXT("整理上下文"),
    OCR_FALLBACK("OCR 兜底"),
    REQUESTING_LLM("请求 LLM"),
    LOCAL_FALLBACK("本地兜底"),
    CANDIDATE_READY("候选已就绪"),
    AUTOFILLING("填入中"),
    DONE("完成"),
    FAILED("失败")
}

data class OverlayDiagnosticsState(
    val phase: OverlayRunPhase = OverlayRunPhase.IDLE,
    val status: String = "未触发",
    val accessibilityMessageCount: Int = 0,
    val ocrMessageCount: Int = 0,
    val mergedMessageCount: Int = 0,
    val candidateCount: Int = 0,
    val candidateSource: String = "暂无",
    val usedOcr: Boolean = false,
    val usedLocalFallback: Boolean = false,
    val lastFailure: String? = null,
    val steps: List<String> = emptyList(),
    val updatedAtMillis: Long = 0L
)

object OverlayDiagnosticsStore {
    private val _state = MutableStateFlow(OverlayDiagnosticsState())
    val state: StateFlow<OverlayDiagnosticsState> = _state.asStateFlow()

    fun reset() {
        _state.value = OverlayDiagnosticsState()
    }

    fun begin() {
        _state.value = OverlayDiagnosticsState(
            phase = OverlayRunPhase.VALIDATING,
            status = "已点击 AI 气泡，开始校验当前页面",
            steps = listOf("开始：点击 AI 气泡"),
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    fun onPhase(
        phase: OverlayRunPhase,
        status: String
    ) {
        update(
            phase = phase,
            status = status,
            step = status
        )
    }

    fun onContext(
        accessibilityMessageCount: Int,
        ocrMessageCount: Int,
        mergedMessageCount: Int,
        usedOcr: Boolean
    ) {
        update(
            phase = OverlayRunPhase.BUILDING_CONTEXT,
            status = "上下文已整理：Accessibility $accessibilityMessageCount 条，OCR $ocrMessageCount 条，合并 $mergedMessageCount 条",
            step = "上下文：accessibility=$accessibilityMessageCount, ocr=$ocrMessageCount, merged=$mergedMessageCount",
            accessibilityMessageCount = accessibilityMessageCount,
            ocrMessageCount = ocrMessageCount,
            mergedMessageCount = mergedMessageCount,
            usedOcr = usedOcr
        )
    }

    fun onCandidates(
        count: Int,
        usedLocalFallback: Boolean,
        candidateSource: String
    ) {
        update(
            phase = OverlayRunPhase.CANDIDATE_READY,
            status = if (usedLocalFallback) {
                "LLM 不可用，已使用本地兜底候选"
            } else {
                "LLM 已返回候选"
            },
            step = "候选：count=$count, source=$candidateSource, localFallback=$usedLocalFallback",
            candidateCount = count,
            candidateSource = candidateSource,
            usedLocalFallback = usedLocalFallback,
            lastFailure = null
        )
    }

    fun onAutofill(message: String) {
        update(
            phase = OverlayRunPhase.AUTOFILLING,
            status = message,
            step = "填入：$message"
        )
    }

    fun onDone(message: String) {
        update(
            phase = OverlayRunPhase.DONE,
            status = message,
            step = "完成：$message"
        )
    }

    fun onFailed(message: String) {
        update(
            phase = OverlayRunPhase.FAILED,
            status = message,
            step = "失败：$message",
            lastFailure = message
        )
    }

    private fun update(
        phase: OverlayRunPhase,
        status: String,
        step: String,
        accessibilityMessageCount: Int? = null,
        ocrMessageCount: Int? = null,
        mergedMessageCount: Int? = null,
        candidateCount: Int? = null,
        candidateSource: String? = null,
        usedOcr: Boolean? = null,
        usedLocalFallback: Boolean? = null,
        lastFailure: String? = _state.value.lastFailure
    ) {
        val current = _state.value
        _state.value = current.copy(
            phase = phase,
            status = status,
            accessibilityMessageCount = accessibilityMessageCount ?: current.accessibilityMessageCount,
            ocrMessageCount = ocrMessageCount ?: current.ocrMessageCount,
            mergedMessageCount = mergedMessageCount ?: current.mergedMessageCount,
            candidateCount = candidateCount ?: current.candidateCount,
            candidateSource = candidateSource ?: current.candidateSource,
            usedOcr = usedOcr ?: current.usedOcr,
            usedLocalFallback = usedLocalFallback ?: current.usedLocalFallback,
            lastFailure = lastFailure,
            steps = (current.steps + step).takeLast(MAX_STEPS),
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    private const val MAX_STEPS = 16
}
