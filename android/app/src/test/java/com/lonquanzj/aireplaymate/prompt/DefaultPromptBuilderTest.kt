package com.lonquanzj.aireplaymate.prompt

import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.chatContext
import com.lonquanzj.aireplaymate.chatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPromptBuilderTest {
    @Test
    fun build_forQuickReply_includes_latest_friend_message_custom_prompt_and_clamps_values() {
        val request = DefaultPromptBuilder.build(
            context = chatContext(
                listOf(
                    chatMessage(id = "m1", role = ChatRole.ME, content = "在忙"),
                    chatMessage(id = "m2", role = ChatRole.FRIEND, content = "今晚有空吗？"),
                    chatMessage(id = "m3", role = ChatRole.ME, content = "我先看下")
                )
            ),
            settings = AppSettings(
                apiKey = "sk-test",
                temperature = 5f,
                maxTokens = 20,
                candidateCount = 0,
                customSystemPrompt = "少用感叹号"
            ),
            styleProfile = ReplyStyleProfile(
                mode = ReplyStyleMode.QUICK_REPLY,
                persona = ReplyPersona.WARM_GENTLE
            )
        )

        assertTrue(request.systemPrompt.contains("用户补充偏好：少用感叹号"))
        assertTrue(request.userPrompt.contains("最近一条对方消息："))
        assertTrue(request.userPrompt.contains("今晚有空吗？"))
        assertTrue(request.userPrompt.contains("请严格返回 JSON"))
        assertEquals(2f, request.temperature)
        assertEquals(120, request.maxTokens)
        assertEquals(1, request.candidateCount)
    }

    @Test
    fun build_forPlaybook_does_not_depend_on_chat_context() {
        val request = DefaultPromptBuilder.build(
            context = chatContext(emptyList()),
            settings = AppSettings(candidateCount = 2),
            styleProfile = ReplyStyleProfile(
                mode = ReplyStyleMode.PLAYBOOK,
                persona = ReplyPersona.ROMANCE_MASTER,
                playbookScene = ReplyStyleCatalog.sceneFromId("good_morning")
            )
        )

        assertTrue(request.userPrompt.contains("不需要参考聊天上下文"))
        assertTrue(request.userPrompt.contains("早安"))
        assertFalse(request.userPrompt.contains("聊天上下文："))
    }

    @Test
    fun build_forPolish_uses_input_draft() {
        val request = DefaultPromptBuilder.build(
            context = chatContext(emptyList()),
            settings = AppSettings(candidateCount = 2),
            styleProfile = ReplyStyleProfile(
                mode = ReplyStyleMode.POLISH,
                persona = ReplyPersona.MATURE_UNCLE,
                polishGoal = PolishGoal.FLIRTY
            ),
            draftText = "我晚点回你"
        )

        assertTrue(request.userPrompt.contains("草稿："))
        assertTrue(request.userPrompt.contains("我晚点回你"))
        assertTrue(request.systemPrompt.contains("润色目标：更暧昧"))
    }
}
