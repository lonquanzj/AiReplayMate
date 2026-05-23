package com.lonquanzj.aireplaymate.overlay

import android.content.Context
import android.view.View
import android.widget.Toast
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugState
import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import com.lonquanzj.aireplaymate.session.RealReplySessionResult
import com.lonquanzj.aireplaymate.session.ReplyContextPreviewStore

internal fun Context.showCandidateGenerationToast(sessionResult: RealReplySessionResult) {
    if (sessionResult.usedLocalFallback) {
        Toast.makeText(
            this,
            localFallbackToastText(sessionResult.localFallbackReason),
            Toast.LENGTH_SHORT
        ).show()
    } else {
        Toast.makeText(this, "候选回复已生成", Toast.LENGTH_SHORT).show()
    }
}

internal fun recordCandidateGenerationSuccess(
    debugState: AccessibilityDebugState,
    sessionResult: RealReplySessionResult,
    candidates: List<OverlayCandidate>
) {
    ReplyContextPreviewStore.update(
        conversationTitle = debugState.conversationTitle,
        messages = sessionResult.context.messages
    )
    OverlayDiagnosticsStore.onCandidates(
        count = candidates.size,
        usedLocalFallback = sessionResult.usedLocalFallback,
        candidateSource = sessionResult.candidateSource
    )
}

internal fun buildGeneratedCandidatePanelView(
    context: Context,
    debugState: AccessibilityDebugState,
    styleProfile: ReplyStyleProfile,
    settings: AppSettings,
    sessionResult: RealReplySessionResult,
    draftText: String?,
    onRegenerate: (ReplyStyleProfile, String?) -> Unit,
    onClose: () -> Unit,
    onCandidateClick: (OverlayCandidate) -> Unit
): View {
    val candidates = sessionResult.candidates.toOverlayCandidates()
    recordCandidateGenerationSuccess(
        debugState = debugState,
        sessionResult = sessionResult,
        candidates = candidates
    )
    return buildCandidatePanelView(
        context = context,
        title = styleProfile.candidatePanelLabel,
        subtitle = sessionResult.toCandidatePanelSubtitle(settings, styleProfile),
        modeLabel = "",
        candidates = candidates,
        onRegenerate = { onRegenerate(styleProfile, draftText) },
        onClose = onClose,
        onCandidateClick = onCandidateClick
    )
}

private fun localFallbackToastText(reason: String?): String {
    val safeReason = reason ?: "未知错误"
    return if (
        safeReason.contains("候选不足") ||
        safeReason.contains("parse", ignoreCase = true) ||
        safeReason.contains("JSON", ignoreCase = true)
    ) {
        "Prompt 协议可能不匹配，已使用本地兜底"
    } else {
        "LLM 不可用，已使用本地兜底：$safeReason"
    }
}
