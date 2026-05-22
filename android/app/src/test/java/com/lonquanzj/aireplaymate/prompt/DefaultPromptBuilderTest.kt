package com.lonquanzj.aireplaymate.prompt

import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.accessibility.MessageSource
import com.lonquanzj.aireplaymate.chatContext
import com.lonquanzj.aireplaymate.chatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPromptBuilderTest {
    @Test
    fun build_forQuickReply_keeps_persona_config_in_user_prompt_and_fixed_protocol_in_system() {
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

        assertFalse(request.systemPrompt.contains("你是一个冷幽默但不冒犯的人。"))
        assertFalse(request.systemPrompt.contains("回复短一点，轻轻调侃。"))
        assertTrue(request.userPrompt.contains("你是一个冷幽默但不冒犯的人。"))
        assertTrue(request.userPrompt.contains("回复短一点，轻轻调侃。"))
        assertFalse(request.userPrompt.contains("上下文可能来自 OCR"))
        assertFalse(request.userPrompt.contains("近形错"))
        assertTrue(request.systemPrompt.contains("输出协议"))
        assertTrue(request.userPrompt.contains("今晚有空吗？"))
        assertEquals(2f, request.temperature)
        assertEquals(120, request.maxTokens)
        assertEquals(1, request.candidateCount)
    }

    @Test
    fun build_normalizes_temperature_to_one_decimal_place() {
        val request = DefaultPromptBuilder.build(
            context = chatContext(
                listOf(chatMessage(id = "m1", role = ChatRole.FRIEND, content = "今晚有空吗？"))
            ),
            settings = AppSettings(temperature = 0.699999988079071f),
            styleProfile = ReplyStyleProfile(mode = ReplyStyleMode.QUICK_REPLY)
        )

        assertEquals(0.7f, request.temperature, 0.0f)
    }

    @Test
    fun build_forQuickReply_withLatestFriendPolicy_sends_only_latest_friend_message() {
        val request = DefaultPromptBuilder.build(
            context = chatContext(
                listOf(
                    chatMessage(id = "m1", role = ChatRole.FRIEND, content = "早上那条旧消息"),
                    chatMessage(id = "m2", role = ChatRole.ME, content = "我自己的回复"),
                    chatMessage(id = "m3", role = ChatRole.FRIEND, content = "今晚有空吗？")
                )
            ),
            settings = AppSettings(contextSendPolicy = ContextSendPolicy.LATEST_FRIEND_MESSAGE),
            styleProfile = ReplyStyleProfile(mode = ReplyStyleMode.QUICK_REPLY)
        )

        assertTrue(request.userPrompt.contains("最近一条对方消息"))
        assertTrue(request.userPrompt.contains("今晚有空吗？"))
        assertFalse(request.userPrompt.contains("早上那条旧消息"))
        assertFalse(request.userPrompt.contains("我自己的回复"))
        assertFalse(request.userPrompt.contains("聊天上下文："))
    }

    @Test
    fun build_forQuickReply_adds_ocr_hint_only_when_sent_context_uses_ocr() {
        val latestFriendRequest = DefaultPromptBuilder.build(
            context = chatContext(
                listOf(
                    chatMessage(id = "m1", role = ChatRole.FRIEND, content = "无障碍旧消息"),
                    chatMessage(
                        id = "m2",
                        role = ChatRole.FRIEND,
                        content = "你又橄回了什么",
                        source = MessageSource.OCR
                    )
                )
            ),
            settings = AppSettings(contextSendPolicy = ContextSendPolicy.LATEST_FRIEND_MESSAGE),
            styleProfile = ReplyStyleProfile(mode = ReplyStyleMode.QUICK_REPLY)
        )

        assertTrue(latestFriendRequest.userPrompt.contains("上下文可能来自 OCR"))
        assertTrue(latestFriendRequest.userPrompt.contains("近形错"))

        val accessibilityOnlyRequest = DefaultPromptBuilder.build(
            context = chatContext(
                listOf(
                    chatMessage(id = "m1", role = ChatRole.FRIEND, content = "今晚有空吗？")
                )
            ),
            settings = AppSettings(contextSendPolicy = ContextSendPolicy.LATEST_FRIEND_MESSAGE),
            styleProfile = ReplyStyleProfile(mode = ReplyStyleMode.QUICK_REPLY)
        )

        assertFalse(accessibilityOnlyRequest.userPrompt.contains("上下文可能来自 OCR"))
    }

    @Test
    fun build_forPlaybook_keeps_identity_prompts_in_user_prompt() {
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

        assertFalse(request.systemPrompt.contains("你正在帮用户发出自然邀约。"))
        assertFalse(request.systemPrompt.contains("细腻暖男"))
        assertTrue(request.userPrompt.contains("话术分类：自定义邀约"))
        assertTrue(request.userPrompt.contains("话术名称：周末约饭"))
        assertTrue(request.userPrompt.contains("话术身份：你正在帮用户发出自然邀约。"))
        assertTrue(request.userPrompt.contains("不要压迫对方"))
        assertTrue(request.userPrompt.contains("叠加角色风格：细腻暖男"))
        assertFalse(request.userPrompt.contains("聊天上下文："))
    }

    @Test
    fun build_forPolish_keeps_identity_prompts_in_user_prompt_and_draft() {
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

        assertFalse(request.systemPrompt.contains("你正在把表达改得更松弛。"))
        assertTrue(request.userPrompt.contains("润色目标：更松弛"))
        assertTrue(request.userPrompt.contains("润色身份：你正在把表达改得更松弛。"))
        assertTrue(request.userPrompt.contains("去掉压力感"))
        assertTrue(request.userPrompt.contains("我晚点回你"))
        assertTrue(request.systemPrompt.contains("输出协议"))
    }

    @Test
    fun build_forQuickReply_fullContext_keeps_only_last_20_messages() {
        val request = DefaultPromptBuilder.build(
            context = chatContext(
                (0 until 25).map { index ->
                    chatMessage(id = "m$index", role = ChatRole.FRIEND, content = "message-$index")
                }
            ),
            settings = AppSettings(contextSendPolicy = ContextSendPolicy.FULL_CONTEXT),
            styleProfile = ReplyStyleProfile(mode = ReplyStyleMode.QUICK_REPLY)
        )

        assertFalse(request.userPrompt.contains("message-0"))
        assertFalse(request.userPrompt.contains("message-4"))
        assertTrue(request.userPrompt.contains("message-5"))
        assertTrue(request.userPrompt.contains("message-24"))
    }

    @Test
    fun build_forQuickReply_latestFriendPolicy_does_not_send_my_message_when_no_friend_message() {
        val request = DefaultPromptBuilder.build(
            context = chatContext(
                listOf(
                    chatMessage(id = "m1", role = ChatRole.ME, content = "my private draft")
                )
            ),
            settings = AppSettings(contextSendPolicy = ContextSendPolicy.LATEST_FRIEND_MESSAGE),
            styleProfile = ReplyStyleProfile(mode = ReplyStyleMode.QUICK_REPLY)
        )

        assertFalse(request.userPrompt.contains("my private draft"))
        assertFalse(request.userPrompt.contains("聊天上下文："))
    }

    @Test
    fun build_sanitizes_control_characters_in_prompt_content() {
        val request = DefaultPromptBuilder.build(
            context = chatContext(
                listOf(
                    chatMessage(id = "m1", role = ChatRole.FRIEND, content = "hi\u0000there\u0007")
                )
            ),
            settings = AppSettings(contextSendPolicy = ContextSendPolicy.FULL_CONTEXT),
            styleProfile = ReplyStyleProfile(mode = ReplyStyleMode.QUICK_REPLY)
        )

        assertTrue(request.userPrompt.contains("hi there"))
        assertFalse(request.userPrompt.contains("\u0000"))
        assertFalse(request.userPrompt.contains("\u0007"))
    }
}
