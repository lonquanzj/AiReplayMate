package com.lonquanzj.aireplaymate.llm

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
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
    val recoveryHint: String,
    val timestampMillis: Long
)

data class LlmDebugState(
    val phase: LlmDebugPhase = LlmDebugPhase.IDLE,
    val baseUrl: String = "",
    val model: String = "",
    val httpStatus: Int? = null,
    val candidateCount: Int = 0,
    val failureCategory: LlmFailureCategory = LlmFailureCategory.NONE,
    val requestPreview: String? = null,
    val responsePreview: String? = null,
    val errorSummary: String? = null,
    val recoveryHint: String = "暂无建议",
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
            recoveryHint = LlmFailureCategory.CONFIG.toRecoveryHint(null, reason),
            updatedAtMillis = System.currentTimeMillis(),
            history = current.history
        )
        _state.value = next.copy(history = current.history.withEntry(next.toHistoryEntry(reason)))
    }

    fun onRequestStarted(
        baseUrl: String,
        model: String,
        requestPreview: String
    ) {
        val current = _state.value
        _state.value = LlmDebugState(
            phase = LlmDebugPhase.REQUESTING,
            baseUrl = baseUrl,
            model = model,
            requestPreview = requestPreview.compactForDisplay(),
            recoveryHint = "正在请求模型，请稍等；若长时间无响应，优先检查网络或代理。",
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
            recoveryHint = if (status in 200..299) {
                "接口已返回成功状态，接下来会解析候选内容。"
            } else {
                LlmFailureCategory.HTTP.toRecoveryHint(status, responseText)
            },
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
            recoveryHint = "连接和解析正常，可以回到微信单聊页使用 AI 气泡。",
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
            recoveryHint = category.toRecoveryHint(_state.value.httpStatus, summary),
            updatedAtMillis = System.currentTimeMillis()
        )
        _state.value = next.copy(history = next.history.withEntry(next.toHistoryEntry(summary)))
    }

    private fun String.compactPreview(): String {
        return compactForDisplay().take(MAX_PREVIEW_LENGTH)
    }

    private fun String.compactForDisplay(): String {
        return replace(Regex("\\s+"), " ").trim()
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
            recoveryHint = recoveryHint.compactPreview(),
            timestampMillis = updatedAtMillis
        )
    }

    private fun List<LlmDebugHistoryEntry>.withEntry(entry: LlmDebugHistoryEntry): List<LlmDebugHistoryEntry> {
        return (listOf(entry) + this).take(MAX_HISTORY_SIZE)
    }

    private fun Throwable.toFailureCategory(): LlmFailureCategory {
        val text = message.orEmpty()
        return when {
            this is UnknownHostException ||
                this is SocketTimeoutException ||
                this is SSLException -> LlmFailureCategory.NETWORK
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

    private fun LlmFailureCategory.toRecoveryHint(
        httpStatus: Int?,
        summary: String
    ): String {
        return when (this) {
            LlmFailureCategory.NONE -> "暂无需要处理的问题。"
            LlmFailureCategory.CONFIG -> {
                when {
                    summary.contains("API Key", ignoreCase = true) -> "请在 LLM 设置里填写 API Key；如果刚复制过 Key，也检查是否多了空格。"
                    summary.contains("Base URL", ignoreCase = true) -> "请确认 Base URL 是完整地址，例如 https://api.openai.com 或你的兼容服务地址。"
                    summary.contains("Model", ignoreCase = true) -> "请填写服务端支持的模型名；模型名需要和供应商文档保持一致。"
                    else -> "请先检查 API Key、Base URL 和 Model 三项配置。"
                }
            }

            LlmFailureCategory.NETWORK -> {
                "请检查手机网络、代理/VPN、Base URL 域名是否可访问；如果是本地代理，确认手机能访问该局域网地址。"
            }

            LlmFailureCategory.HTTP -> {
                when (httpStatus) {
                    400 -> "请求格式或模型参数可能不被服务支持，请检查模型名、Base URL 是否对应 OpenAI 兼容 chat/completions 接口。"
                    401, 403 -> "鉴权失败，请检查 API Key 是否正确、是否过期，以及当前 Key 是否有该模型权限。"
                    404 -> "接口或模型不存在，请检查 Base URL 是否多/少了 /v1，或模型名是否写错。"
                    408, 429 -> "请求超时或频率受限，请稍后重试；如果频繁出现 429，需要降低请求频率或检查额度。"
                    in 500..599 -> "模型服务端异常，请稍后重试；若使用第三方兼容服务，可打开服务商状态页或切换 Base URL。"
                    else -> "服务返回了非成功 HTTP 状态，请结合 HTTP 码和返回预览检查 Key、模型名、额度或接口路径。"
                }
            }

            LlmFailureCategory.PARSE -> {
                "接口已返回内容，但格式不符合候选解析预期。请确认模型按 JSON 候选格式输出，或尝试换一个更稳定的模型。"
            }

            LlmFailureCategory.INSUFFICIENT_CANDIDATES -> {
                "模型返回的候选数量不足。可以重试一次，或降低候选数量要求；若频繁发生，需要强化提示词格式约束。"
            }

            LlmFailureCategory.UNKNOWN -> {
                "暂未识别具体原因，请复制 LLM 诊断给开发侧；优先检查返回预览、网络和模型配置。"
            }
        }
    }

    private const val MAX_PREVIEW_LENGTH = 220
    private const val MAX_HISTORY_SIZE = 8
}
