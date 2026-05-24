package com.lonquanzj.aireplaymate.overlay

import android.os.Build
import android.view.WindowManager
import com.lonquanzj.aireplaymate.prompt.ReplyCandidate
import com.lonquanzj.aireplaymate.prompt.ReplyStyleMode
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import com.lonquanzj.aireplaymate.session.RealReplySessionPhase

/**
 * Overlay 拆分组件共享的数据模型、常量和轻量转换函数。
 */
internal enum class StyleMenuTab(
    val label: String,
    val hint: String
) {
    PERSONA("角色", "选择全局角色"),
    PLAYBOOK("话术", "生成对应场景话术"),
    POLISH("润色", "润色聊天框草稿")
}

internal enum class DockedSide {
    LEFT,
    RIGHT
}

internal data class StyleMenuGroup<T>(
    val id: String,
    val label: String,
    val items: List<T>
)

internal data class OverlayCandidate(
    val id: String,
    val text: String
)

internal const val DRAG_SLOP = 8
internal const val LONG_PRESS_TIMEOUT_MS = 520L
internal const val STYLE_MENU_ALL_GROUP_ID = "all"
internal const val STYLE_MENU_HINT_TAG = "style_menu_hint"
internal const val FLOATING_BUTTON_SIZE_DP = 56
internal const val DOCKED_VISIBLE_WIDTH_DP = 15
internal const val DOCK_ANIMATION_DURATION_MS = 220L
internal const val AUTO_DOCK_DELAY_MS = 3000L
internal const val IDLE_BLINK_MIN_DELAY_MS = 2_000L
internal const val IDLE_BLINK_MAX_DELAY_MS = 4_000L
internal const val IDLE_BLINK_DURATION_MS = 360L

// 将 session 层候选转换为 overlay 展示模型，避免 UI 直接依赖 prompt 模型。
internal fun List<ReplyCandidate>.toOverlayCandidates(): List<OverlayCandidate> {
    return map { candidate ->
        OverlayCandidate(
            id = candidate.id,
            text = candidate.text
        )
    }
}

// 会话阶段映射到 overlay 诊断阶段枚举。
internal fun RealReplySessionPhase.toOverlayPhase(): OverlayRunPhase {
    return when (this) {
        RealReplySessionPhase.VALIDATING -> OverlayRunPhase.VALIDATING
        RealReplySessionPhase.BUILDING_CONTEXT -> OverlayRunPhase.BUILDING_CONTEXT
        RealReplySessionPhase.OCR_FALLBACK -> OverlayRunPhase.OCR_FALLBACK
        RealReplySessionPhase.REQUESTING_LLM -> OverlayRunPhase.REQUESTING_LLM
        RealReplySessionPhase.LOCAL_FALLBACK -> OverlayRunPhase.LOCAL_FALLBACK
    }
}

// 悬浮球入口统一回到 QUICK_REPLY 模式。
internal fun ReplyStyleProfile.asDefaultReply(): ReplyStyleProfile {
    return copy(mode = ReplyStyleMode.QUICK_REPLY)
}

// Android O+ 使用 APPLICATION_OVERLAY，低版本兼容旧类型。
internal fun overlayWindowType(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }
}
