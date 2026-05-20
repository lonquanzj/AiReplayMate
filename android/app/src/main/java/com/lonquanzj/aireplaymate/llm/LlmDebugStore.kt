package com.lonquanzj.aireplaymate.llm

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LlmDebugPhase(
    val label: String
) {
    IDLE("未请求"),
    SKIPPED("已跳过"),
    REQUESTING("请求中"),
    HTTP_RETURNED("HTTP 已返回"),
    PARSED("解析成功"),
    FAILED("请求失败")
}

enum class LlmFailureCategory(
    val label: String
) {
    NONE("无"),
    CONFIG("配置错误"),
    NETWORK("网络错误"),
    HTTP("HTTP 错误"),
    PARSE("解析错误"),
    INSUFFICIENT_CANDIDATES("候选不足"),
    UNKNOWN("未知错误")
}

data class LlmDebugHistoryEntry(
    val phase: LlmDebugPhase,
    val category: LlmFailureCategory,
    val baseUrl: String,
    val model: String,
    val httpStatus: Int?,
    val candidateCount: Int,
    val summary: String,
    val timestampMillis: Long
)

data class LlmDebugState(
    val phase: LlmDebugPhase = LlmDebugPhase.IDLE,
    val baseUrl: String = "",
    val model: String = "",
    val httpStatus: Int? = null,
    val candidateCount: Int = 0,
    val failureCategory: LlmFailureCategory = LlmFailureCategory.NONE,
    val responsePreview: String? = null,
    val errorSummary: String? = null,
    val updatedAtMillis: Long = 0L,
    val history: List<LlmDebugHistoryEntry> = emptyList()
)

object LlmDebugStore {
    private val _state = MutableStateFlow(LlmDebugState())
    val state: StateFlow<LlmDebugState> = _state.asStateFlow()

    fun onSkipped(
        baseUrl: String,
        model: String,
        reason: String
    ) {
        val current = _state.value
        val next = LlmDebugState(
            phase = LlmDebugPhase.SKIPPED,
            baseUrl = baseUrl,
            model = model,
            failureCategory = LlmFailureCategory.CONFIG,
            errorSummary = reason,
            updatedAtMillis = System.currentTimeMillis(),
            history = current.history
        )
        _state.value = next.copy(history = current.history.withEntry(next.toHistoryEntry(reason)))
    }

    fun onRequestStarted(
        baseUrl: String,
        model: String
    ) {
        val current = _state.value
        _state.value = LlmDebugState(
            phase = LlmDebugPhase.REQUESTING,
            baseUrl = baseUrl,
            model = model,
            updatedAtMillis = System.currentTimeMillis(),
            history = current.history
        )
    }

    fun onHttpReturned(
        status: Int,
        responseText: String
    ) {
        _state.value = _state.value.copy(
            phase = LlmDebugPhase.HTTP_RETURNED,
            httpStatus = status,
            responsePreview = responseText.compactPreview(),
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    fun onParsed(
        candidateCount: Int,
        contentPreview: String
    ) {
        val next = _state.value.copy(
            phase = LlmDebugPhase.PARSED,
            candidateCount = candidateCount,
            failureCategory = LlmFailureCategory.NONE,
            responsePreview = contentPreview.compactPreview(),
            errorSummary = null,
            updatedAtMillis = System.currentTimeMillis()
        )
        _state.value = next.copy(history = next.history.withEntry(next.toHistoryEntry("成功返回 $candidateCount 条候选")))
    }

    fun onFailed(error: Throwable) {
        val category = error.toFailureCategory()
        val summary = error.message ?: error::class.java.simpleName
        val next = _state.value.copy(
            phase = LlmDebugPhase.FAILED,
            failureCategory = category,
            errorSummary = summary,
            updatedAtMillis = System.currentTimeMillis()
        )
        _state.value = next.copy(history = next.history.withEntry(next.toHistoryEntry(summary)))
    }

    private fun String.compactPreview(): String {
        return replace(Regex("\\s+"), " ").trim().take(MAX_PREVIEW_LENGTH)
    }

    private fun LlmDebugState.toHistoryEntry(summary: String): LlmDebugHistoryEntry {
        return LlmDebugHistoryEntry(
            phase = phase,
            category = failureCategory,
            baseUrl = baseUrl,
            model = model,
            httpStatus = httpStatus,
            candidateCount = candidateCount,
            summary = summary.compactPreview(),
            timestampMillis = updatedAtMillis
        )
    }

    private fun List<LlmDebugHistoryEntry>.withEntry(entry: LlmDebugHistoryEntry): List<LlmDebugHistoryEntry> {
        return (listOf(entry) + this).take(MAX_HISTORY_SIZE)
    }

    private fun Throwable.toFailureCategory(): LlmFailureCategory {
        val text = message.orEmpty()
        return when {
            this is UnknownHostException || this is SocketTimeoutException -> LlmFailureCategory.NETWORK
            text.contains("HTTP", ignoreCase = true) -> LlmFailureCategory.HTTP
            text.contains("候选不足") -> LlmFailureCategory.INSUFFICIENT_CANDIDATES
            text.contains("JSONObject") ||
                text.contains("JSONArray") ||
                text.contains("No value for") -> LlmFailureCategory.PARSE
            text.contains("API Key") ||
                text.contains("Base URL") ||
                text.contains("Model") -> LlmFailureCategory.CONFIG
            else -> LlmFailureCategory.UNKNOWN
        }
    }

    private const val MAX_PREVIEW_LENGTH = 220
    private const val MAX_HISTORY_SIZE = 8
}
