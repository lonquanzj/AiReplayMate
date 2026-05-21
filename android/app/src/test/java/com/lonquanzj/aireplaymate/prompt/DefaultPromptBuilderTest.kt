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
    fun build_forQuickReply_includes_persona_config_and_fixed_protocol() {
        val request = DefaultPromptBuilder.build(
            context = chatContext(
                listOf(
                    chatMessage(id = "m1", role = ChatRole.ME, content = "在忙"),
                    chatMessage(id = "m2", role = ChatRole.FRIEND, content = "今晚有空吗？")
                )
            ),
            settings = AppSettings(temperature = 5f, maxTokens = 20, candidateCount = 0),
            styleProfile = ReplyStyleProfile(
                mode = ReplyStyleMode.QUICK_REPLY,
                personaConfig = ReplyPersonaConfig(
                    id = "custom_persona",
                    label = "冷幽默",
                    identityPrompt = "你是一个冷幽默但不冒犯的人。",
                    promptGuide = "回复短一点，轻轻调侃。",
                    isBuiltin = false
                )
            )
        )

        assertTrue(request.systemPrompt.contains("你是一个冷幽默但不冒犯的人。"))
        assertTrue(request.systemPrompt.contains("回复短一点，轻轻调侃。"))
        assertTrue(request.systemPrompt.contains("输出协议"))
        assertTrue(request.userPrompt.contains("今晚有空吗？"))
        assertEquals(2f, request.temperature)
        assertEquals(120, request.maxTokens)
        assertEquals(1, request.candidateCount)
    }

    @Test
    fun build_forPlaybook_includes_playbook_identity_prompt_category_and_role() {
        val request = DefaultPromptBuilder.build(
            context = chatContext(emptyList()),
            settings = AppSettings(candidateCount = 2),
            styleProfile = ReplyStyleProfile(
                mode = ReplyStyleMode.PLAYBOOK,
                personaConfig = ReplyStyleCatalog.defaultPersonaConfig.copy(label = "细腻暖男"),
                playbookConfig = ReplyPlaybookConfig(
                    id = "custom_scene",
                    categoryLabel = "自定义邀约",
                    label = "周末约饭",
                    identityPrompt = "你正在帮用户发出自然邀约。",
                    promptGuide = "不要压迫对方，给对方选择空间。",
                    isBuiltin = false
                )
            )
        )

        assertTrue(request.systemPrompt.contains("话术身份：你正在帮用户发出自然邀约。"))
        assertTrue(request.userPrompt.contains("话术分类：自定义邀约"))
        assertTrue(request.userPrompt.contains("话术名称：周末约饭"))
        assertTrue(request.userPrompt.contains("不要压迫对方"))
        assertTrue(request.userPrompt.contains("叠加角色风格：细腻暖男"))
        assertFalse(request.userPrompt.contains("聊天上下文："))
    }

    @Test
    fun build_forPolish_includes_polish_identity_prompt_and_draft() {
        val request = DefaultPromptBuilder.build(
            context = chatContext(emptyList()),
            settings = AppSettings(candidateCount = 2),
            styleProfile = ReplyStyleProfile(
                mode = ReplyStyleMode.POLISH,
                polishGoalConfig = PolishGoalConfig(
                    id = "custom_polish",
                    label = "更松弛",
                    identityPrompt = "你正在把表达改得更松弛。",
                    promptGuide = "去掉压力感，保留原意。",
                    isBuiltin = false
                )
            ),
            draftText = "我晚点回你"
        )

        assertTrue(request.systemPrompt.contains("你正在把表达改得更松弛。"))
        assertTrue(request.userPrompt.contains("润色目标：更松弛"))
        assertTrue(request.userPrompt.contains("去掉压力感"))
        assertTrue(request.userPrompt.contains("我晚点回你"))
        assertTrue(request.systemPrompt.contains("输出协议"))
    }
}
