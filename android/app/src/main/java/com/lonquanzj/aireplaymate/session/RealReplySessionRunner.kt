package com.lonquanzj.aireplaymate.session

import android.content.Context
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugState
import com.lonquanzj.aireplaymate.accessibility.MessageSource
import com.lonquanzj.aireplaymate.context.ChatContext
import com.lonquanzj.aireplaymate.context.ConversationType
import com.lonquanzj.aireplaymate.context.DefaultContextBuilder
import com.lonquanzj.aireplaymate.llm.OpenAiCompatibleLlmGateway
import com.lonquanzj.aireplaymate.ocr.MlKitChineseOcrEngine
import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.DefaultPromptBuilder
import com.lonquanzj.aireplaymate.prompt.ReplyCandidate
import com.lonquanzj.aireplaymate.prompt.ReplyStyleMode
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile

enum class RealReplySessionPhase {
    VALIDATING,
    BUILDING_CONTEXT,
    OCR_FALLBACK,
    REQUESTING_LLM,
    LOCAL_FALLBACK
}

data class RealReplySessionContextSnapshot(
    val accessibilityMessageCount: Int,
    val ocrMessageCount: Int,
    val mergedMessageCount: Int,
    val usedOcr: Boolean
)

data class RealReplySessionResult(
    val context: ChatContext,
    val candidates: List<ReplyCandidate>,
    val candidateSource: String,
    val usedOcr: Boolean,
    val usedLocalFallback: Boolean,
    val localFallbackReason: String? = null
)

class RealReplySessionRunner(
    private val context: Context
) {
    suspend fun run(
        debugState: AccessibilityDebugState,
        settings: AppSettings,
        styleProfile: ReplyStyleProfile = ReplyStyleProfile(),
        draftText: String? = null,
        targetApp: String = WECHAT_TARGET_APP,
        conversationType: ConversationType = ConversationType.SINGLE_CHAT,
        onPhase: (RealReplySessionPhase, String) -> Unit = { _, _ -> },
        onContext: (RealReplySessionContextSnapshot) -> Unit = {}
    ): Result<RealReplySessionResult> {
        validateTarget(debugState)?.let { blocker ->
            return Result.failure(IllegalStateException(blocker))
        }

        onPhase(RealReplySessionPhase.BUILDING_CONTEXT, "页面校验通过，开始整理上下文")
        var usedOcr = false
        var chatContext = DefaultContextBuilder.build(
            accessibilityMessages = debugState.extractedMessages,
            targetApp = targetApp,
            conversationType = conversationType
        )
        onContext(
            RealReplySessionContextSnapshot(
                accessibilityMessageCount = debugState.extractedMessages.size,
                ocrMessageCount = 0,
                mergedMessageCount = chatContext.messages.size,
                usedOcr = false
            )
        )

        val requiresChatContext = styleProfile.mode == ReplyStyleMode.QUICK_REPLY

        if (requiresChatContext && !chatContext.enoughForReply) {
            usedOcr = true
            onPhase(RealReplySessionPhase.OCR_FALLBACK, "Accessibility 上下文不足，开始 OCR 兜底")
            val ocrResult = MlKitChineseOcrEngine(context.applicationContext).recognizeChatMessages(
                targetApp = targetApp,
                reason = "悬浮按钮触发时 Accessibility 上下文不足"
            )
            chatContext = DefaultContextBuilder.build(
                accessibilityMessages = debugState.extractedMessages,
                ocrMessages = ocrResult.messages,
                targetApp = targetApp,
                conversationType = conversationType
            )
            onContext(
                RealReplySessionContextSnapshot(
                    accessibilityMessageCount = debugState.extractedMessages.size,
                    ocrMessageCount = ocrResult.messages.size,
                    mergedMessageCount = chatContext.messages.size,
                    usedOcr = true
                )
            )
            if (!chatContext.enoughForReply) {
                return Result.failure(IllegalStateException("上下文不足，且 ${ocrResult.message}"))
            }
        } else if (!requiresChatContext && !chatContext.enoughForReply) {
            onPhase(RealReplySessionPhase.BUILDING_CONTEXT, "话术宝典无需聊天上下文，直接按场景生成")
        }

        val llmRequest = DefaultPromptBuilder.build(
            context = chatContext,
            settings = settings,
            styleProfile = styleProfile,
            draftText = draftText
        )
        onPhase(RealReplySessionPhase.REQUESTING_LLM, "正在生成候选回复")

        var usedLocalFallback = false
        var localFallbackReason: String? = null
        var candidateSource = buildCandidateSource(
            isLocalFallback = false,
            usedOcr = usedOcr || chatContext.messages.any { it.source == MessageSource.OCR },
            styleProfile = styleProfile
        )
        val candidates = OpenAiCompatibleLlmGateway(settings)
            .generateReplies(llmRequest)
            .fold(
                onSuccess = { llmCandidates ->
                    llmCandidates.withStyleTone(styleProfile)
                },
                onFailure = { error ->
                    usedLocalFallback = true
                    localFallbackReason = error.message ?: "未知错误"
                    candidateSource = buildCandidateSource(
                        isLocalFallback = true,
                        usedOcr = usedOcr || chatContext.messages.any { it.source == MessageSource.OCR },
                        styleProfile = styleProfile
                    )
                    onPhase(
                        RealReplySessionPhase.LOCAL_FALLBACK,
                        "LLM 不可用，切换到本地兜底：$localFallbackReason"
                    )
                    LocalFallbackReplyGenerator.generate(
                        context = chatContext,
                        styleProfile = styleProfile,
                        seed = llmRequest.userPrompt,
                        draftText = draftText
                    )
                }
            )

        return Result.success(
            RealReplySessionResult(
                context = chatContext,
                candidates = candidates,
                candidateSource = candidateSource,
                usedOcr = usedOcr,
                usedLocalFallback = usedLocalFallback,
                localFallbackReason = localFallbackReason
            )
        )
    }

    private fun validateTarget(debugState: AccessibilityDebugState): String? {
        if (!debugState.serviceConnected) {
            return "无障碍服务未连接"
        }
        if (!debugState.isWechatPackage) {
            return "当前不在微信页面"
        }
        if (!debugState.looksLikeChatPage) {
            return "当前不像微信单聊页"
        }
        return null
    }

    private fun buildCandidateSource(
        isLocalFallback: Boolean,
        usedOcr: Boolean,
        styleProfile: ReplyStyleProfile
    ): String {
        val base = if (isLocalFallback) "本地兜底" else "LLM"
        val styled = "$base · ${styleProfile.shortLabel}"
        return if (usedOcr) "$styled（含 OCR 上下文）" else styled
    }

    private fun List<ReplyCandidate>.withStyleTone(styleProfile: ReplyStyleProfile): List<ReplyCandidate> {
        return map { candidate ->
            candidate.copy(tone = candidate.tone ?: styleProfile.shortLabel)
        }
    }

    private companion object {
        const val WECHAT_TARGET_APP = "wechat"
    }
}
