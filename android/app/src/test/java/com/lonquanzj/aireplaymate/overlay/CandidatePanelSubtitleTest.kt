package com.lonquanzj.aireplaymate.overlay

import com.lonquanzj.aireplaymate.chatContext
import com.lonquanzj.aireplaymate.chatMessage
import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.ContextSendPolicy
import com.lonquanzj.aireplaymate.prompt.ReplyCandidate
import com.lonquanzj.aireplaymate.prompt.ReplyStyleMode
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import com.lonquanzj.aireplaymate.session.RealReplySessionResult
import org.junit.Assert.assertEquals
import org.junit.Test

class CandidatePanelSubtitleTest {
    @Test
    fun subtitle_forAiFullContext_mentionsFullContext() {
        val result = sessionResult()

        val subtitle = result.toCandidatePanelSubtitle(
            settings = AppSettings(contextSendPolicy = ContextSendPolicy.FULL_CONTEXT),
            styleProfile = ReplyStyleProfile(mode = ReplyStyleMode.QUICK_REPLY)
        )

        assertEquals("AI 生成 · 已参考完整上下文", subtitle)
    }

    @Test
    fun subtitle_forAiLatestFriendMessage_mentionsLatestMessage() {
        val result = sessionResult()

        val subtitle = result.toCandidatePanelSubtitle(
            settings = AppSettings(contextSendPolicy = ContextSendPolicy.LATEST_FRIEND_MESSAGE),
            styleProfile = ReplyStyleProfile(mode = ReplyStyleMode.QUICK_REPLY)
        )

        assertEquals("AI 生成 · 已参考最近一条消息", subtitle)
    }

    @Test
    fun subtitle_forOcr_mentionsOcrContextBeforeSendPolicy() {
        val result = sessionResult(usedOcr = true)

        val subtitle = result.toCandidatePanelSubtitle(
            settings = AppSettings(contextSendPolicy = ContextSendPolicy.FULL_CONTEXT),
            styleProfile = ReplyStyleProfile(mode = ReplyStyleMode.QUICK_REPLY)
        )

        assertEquals("AI 生成 · 已参考 OCR 上下文", subtitle)
    }

    @Test
    fun subtitle_forLocalFallback_keepsContextBasis() {
        val result = sessionResult(usedLocalFallback = true)

        val subtitle = result.toCandidatePanelSubtitle(
            settings = AppSettings(contextSendPolicy = ContextSendPolicy.LATEST_FRIEND_MESSAGE),
            styleProfile = ReplyStyleProfile(mode = ReplyStyleMode.QUICK_REPLY)
        )

        assertEquals("本地生成 · 已参考最近一条消息", subtitle)
    }

    @Test
    fun subtitle_forPlaybook_mentionsSceneGeneration() {
        val result = sessionResult()

        val subtitle = result.toCandidatePanelSubtitle(
            settings = AppSettings(),
            styleProfile = ReplyStyleProfile(mode = ReplyStyleMode.PLAYBOOK)
        )

        assertEquals("AI 生成 · 按场景生成", subtitle)
    }

    @Test
    fun subtitle_forPolish_mentionsDraft() {
        val result = sessionResult()

        val subtitle = result.toCandidatePanelSubtitle(
            settings = AppSettings(),
            styleProfile = ReplyStyleProfile(mode = ReplyStyleMode.POLISH)
        )

        assertEquals("AI 生成 · 已参考输入草稿", subtitle)
    }

    @Test
    fun subtitle_forEmptyContext_mentionsNoContext() {
        val result = sessionResult(hasContext = false, usedLocalFallback = true)

        val subtitle = result.toCandidatePanelSubtitle(
            settings = AppSettings(),
            styleProfile = ReplyStyleProfile(mode = ReplyStyleMode.QUICK_REPLY)
        )

        assertEquals("本地生成 · 暂无聊天上下文", subtitle)
    }

    private fun sessionResult(
        hasContext: Boolean = true,
        usedOcr: Boolean = false,
        usedLocalFallback: Boolean = false
    ): RealReplySessionResult {
        return RealReplySessionResult(
            context = chatContext(
                if (hasContext) {
                    listOf(chatMessage(id = "m1", content = "今晚有空吗？"))
                } else {
                    emptyList()
                }
            ),
            candidates = listOf(ReplyCandidate(id = "c1", text = "可以呀", rank = 1)),
            candidateSource = if (usedLocalFallback) "本地兜底" else "LLM",
            usedOcr = usedOcr,
            usedLocalFallback = usedLocalFallback
        )
    }
}
