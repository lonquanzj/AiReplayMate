package com.lonquanzj.aireplaymate.overlay

import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.ContextSendPolicy
import com.lonquanzj.aireplaymate.prompt.ReplyStyleMode
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import com.lonquanzj.aireplaymate.session.RealReplySessionResult

fun RealReplySessionResult.toCandidatePanelSubtitle(
    settings: AppSettings,
    styleProfile: ReplyStyleProfile
): String {
    val generatorLabel = if (usedLocalFallback) "本地生成" else "AI 生成"
    val basisLabel = when {
        styleProfile.mode == ReplyStyleMode.PLAYBOOK -> "按场景生成"
        styleProfile.mode == ReplyStyleMode.POLISH -> "已参考输入草稿"
        usedOcr -> "已参考 OCR 上下文"
        context.messages.isEmpty() -> "暂无聊天上下文"
        settings.contextSendPolicy == ContextSendPolicy.LATEST_FRIEND_MESSAGE -> "已参考最近一条消息"
        settings.contextSendPolicy == ContextSendPolicy.FULL_CONTEXT -> "已参考完整上下文"
        else -> "已参考当前聊天"
    }

    return "$generatorLabel · $basisLabel"
}
