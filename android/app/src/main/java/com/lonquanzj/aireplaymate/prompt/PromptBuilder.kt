package com.lonquanzj.aireplaymate.prompt

import com.lonquanzj.aireplaymate.accessibility.ChatMessage
import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.context.ChatContext

interface PromptBuilder {
    fun build(
        context: ChatContext,
        settings: AppSettings = AppSettings()
    ): LlmRequest
}

object DefaultPromptBuilder : PromptBuilder {
    override fun build(
        context: ChatContext,
        settings: AppSettings
    ): LlmRequest {
        val systemPrompt = settings.customSystemPrompt?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_SYSTEM_PROMPT

        return LlmRequest(
            systemPrompt = systemPrompt,
            userPrompt = buildUserPrompt(context, settings.candidateCount),
            temperature = settings.temperature.coerceIn(0f, 2f),
            maxTokens = settings.maxTokens.coerceAtLeast(120),
            candidateCount = settings.candidateCount.coerceAtLeast(1)
        )
    }

    private fun buildUserPrompt(
        context: ChatContext,
        candidateCount: Int
    ): String {
        val visibleMessages = context.messages.takeLast(MAX_PROMPT_MESSAGES)
        val latestFriendMessage = visibleMessages.lastOrNull { it.role == ChatRole.FRIEND }

        return buildString {
            appendLine("下面是微信单聊的最近聊天上下文。")
            appendLine("请基于上下文生成 $candidateCount 条中文候选回复，要求自然、简洁、可直接填入输入框。")
            appendLine("不要使用列表编号、引号、解释性前缀，也不要声称已经完成付款、确认合同、发送文件等线下动作。")
            appendLine("请严格返回 JSON：{\"candidates\":[{\"text\":\"...\"},{\"text\":\"...\"},{\"text\":\"...\"}]}")
            appendLine()
            appendLine("最近一条对方消息：")
            appendLine(latestFriendMessage?.content ?: "未识别到明确的对方消息")
            appendLine()
            appendLine("聊天上下文：")
            visibleMessages.forEach { message ->
                appendLine("${message.role.promptLabel}: ${message.content.safeForPrompt()}")
            }
        }
    }

    private fun String.safeForPrompt(): String {
        return replace(controlCharsRegex, " ").trim()
    }

    private val ChatRole.promptLabel: String
        get() = when (this) {
            ChatRole.ME -> "我"
            ChatRole.FRIEND -> "对方"
            ChatRole.SYSTEM -> "系统"
            ChatRole.UNKNOWN -> "未知"
        }

    private const val MAX_PROMPT_MESSAGES = 20
    private val controlCharsRegex = Regex("[\\p{Cntrl}&&[^\n\t]]+")

    private const val DEFAULT_SYSTEM_PROMPT =
        "你是一个微信聊天回复助手。你只生成供用户审核后手动发送的候选回复，不能代表用户承诺已经完成现实动作。"
}
