package com.lonquanzj.aireplaymate.overlay

import android.content.Context
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugState
import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import com.lonquanzj.aireplaymate.session.RealReplySessionContextSnapshot
import com.lonquanzj.aireplaymate.session.RealReplySessionPhase
import com.lonquanzj.aireplaymate.session.RealReplySessionResult
import com.lonquanzj.aireplaymate.session.RealReplySessionRunner
import com.lonquanzj.aireplaymate.settings.AppSettingsStore

/**
 * Overlay 候选生成会话的轻量编排层。
 *
 * 目的：把 Service 从 runner/settings 细节中解耦，只关心 Success/Failure。
 */
internal data class OverlaySessionSuccess(
    val settings: AppSettings,
    val sessionResult: RealReplySessionResult
)

internal sealed interface OverlaySessionOutcome {
    data class Success(val value: OverlaySessionSuccess) : OverlaySessionOutcome

    data class Failure(val message: String) : OverlaySessionOutcome
}

internal suspend fun runOverlaySession(
    context: Context,
    debugState: AccessibilityDebugState,
    styleProfile: ReplyStyleProfile,
    draftText: String?,
    onPhase: (RealReplySessionPhase, String) -> Unit,
    onContext: (RealReplySessionContextSnapshot) -> Unit,
    onBeforeOcrCapture: suspend () -> Unit = {},
    onAfterOcrCapture: suspend () -> Unit = {}
): OverlaySessionOutcome {
    // 会话期间使用同一份设置，避免流程中途读取变化导致行为不一致。
    val settings = AppSettingsStore.load(context)
    val result = RealReplySessionRunner(context.applicationContext).run(
        debugState = debugState,
        settings = settings,
        styleProfile = styleProfile,
        draftText = draftText,
        onPhase = { phase, status ->
            onPhase(phase, status)
        },
        onContext = onContext,
        onBeforeOcrCapture = onBeforeOcrCapture,
        onAfterOcrCapture = onAfterOcrCapture
    )

    val sessionResult = result.getOrElse { error ->
        return OverlaySessionOutcome.Failure(error.message ?: "生成候选失败")
    }

    return OverlaySessionOutcome.Success(
        OverlaySessionSuccess(
            settings = settings,
            sessionResult = sessionResult
        )
    )
}

internal fun overlayProgressStatus(phase: RealReplySessionPhase): String {
    // 统一维护 Overlay 的阶段文案，避免 Service 内散落硬编码字符串。
    return when (phase) {
        RealReplySessionPhase.BUILDING_CONTEXT -> "正在整理聊天上下文..."
        RealReplySessionPhase.OCR_FALLBACK -> "正在用 OCR 兜底识别消息..."
        RealReplySessionPhase.REQUESTING_LLM -> "正在生成候选回复..."
        RealReplySessionPhase.LOCAL_FALLBACK -> "正在准备本地兜底候选..."
        RealReplySessionPhase.VALIDATING -> "正在校验微信页面..."
    }
}
