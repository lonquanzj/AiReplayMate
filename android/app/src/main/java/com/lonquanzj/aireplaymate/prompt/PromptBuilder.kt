package com.lonquanzj.aireplaymate.prompt

import com.lonquanzj.aireplaymate.accessibility.ChatMessage
import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.context.ChatContext

interface PromptBuilder {
    fun build(
        context: ChatContext,
        settings: AppSettings = AppSettings(),
        styleProfile: ReplyStyleProfile = ReplyStyleProfile(),
        draftText: String? = null
    ): LlmRequest
}

object DefaultPromptBuilder : PromptBuilder {
    override fun build(
        context: ChatContext,
        settings: AppSettings,
        styleProfile: ReplyStyleProfile,
        draftText: String?
    ): LlmRequest {
        val systemPrompt = buildSystemPrompt(settings, styleProfile)

        return LlmRequest(
            systemPrompt = systemPrompt,
            userPrompt = buildUserPrompt(context, settings.candidateCount, styleProfile, draftText),
            temperature = settings.temperature.coerceIn(0f, 2f),
            maxTokens = settings.maxTokens.coerceAtLeast(120),
            candidateCount = settings.candidateCount.coerceAtLeast(1)
        )
    }

    private fun buildSystemPrompt(
        settings: AppSettings,
        styleProfile: ReplyStyleProfile
    ): String {
        return buildString {
            appendLine(DEFAULT_SYSTEM_PROMPT)
            appendLine("当前角色：${styleProfile.persona.label}。${styleProfile.persona.promptGuide}")
            appendLine("当前模式：${styleProfile.mode.label}。")
            when (styleProfile.mode) {
                ReplyStyleMode.QUICK_REPLY -> {
                    appendLine("目标：基于最近聊天自然回复，优先像真人手动输入，不要像营销话术。")
                }

                ReplyStyleMode.PLAYBOOK -> {
                    val scene = styleProfile.playbookScene ?: ReplyStyleCatalog.defaultScene
                    appendLine("话术场景：${scene.categoryLabel} / ${scene.sceneLabel}。${scene.promptGuide}")
                }

                ReplyStyleMode.POLISH -> {
                    appendLine("润色目标：${styleProfile.polishGoal.label}。${styleProfile.polishGoal.promptGuide}")
                }
            }
            appendLine("安全边界：不得自动替用户承诺付款、见面、合同、发送文件、已经完成某个现实动作；不得冒犯、PUA、贬低、威胁或越界。")
            appendLine("表达要求：中文口语化、短句优先、有分寸；每条候选不超过 45 个字，除非上下文明确需要更长。")

            settings.customSystemPrompt?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { customPrompt ->
                    appendLine("用户补充偏好：$customPrompt")
                }
        }.trim()
    }

    private fun buildUserPrompt(
        context: ChatContext,
        candidateCount: Int,
        styleProfile: ReplyStyleProfile,
        draftText: String?
    ): String {
        if (styleProfile.mode == ReplyStyleMode.PLAYBOOK) {
            return buildPlaybookUserPrompt(candidateCount, styleProfile)
        }
        if (styleProfile.mode == ReplyStyleMode.POLISH) {
            return buildPolishUserPrompt(candidateCount, styleProfile, draftText.orEmpty())
        }

        val visibleMessages = context.messages.takeLast(MAX_PROMPT_MESSAGES)
        val latestFriendMessage = visibleMessages.lastOrNull { it.role == ChatRole.FRIEND }

        return buildString {
            appendLine("下面是微信单聊的最近聊天上下文。")
            appendLine("请基于上下文生成 $candidateCount 条中文候选回复，要求自然、简洁、可直接填入输入框。")
            appendLine("本次生成风格：${styleProfile.displayLabel}。")
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

    private fun buildPlaybookUserPrompt(
        candidateCount: Int,
        styleProfile: ReplyStyleProfile
    ): String {
        val scene = styleProfile.playbookScene ?: ReplyStyleCatalog.defaultScene
        return buildString {
            appendLine("请直接生成 $candidateCount 条中文聊天话术，不需要参考聊天上下文。")
            appendLine("话术用途：${scene.categoryLabel} / ${scene.sceneLabel}。")
            appendLine("角色风格：${styleProfile.persona.label}。")
            appendLine("要求：可直接填入微信输入框，像真人临场写出来，不要出现模板变量、占位符、解释说明或列表编号。")
            appendLine("安全边界：不替用户承诺现实动作，不冒犯、不压迫、不越界。")
            appendLine("请严格返回 JSON：{\"candidates\":[{\"text\":\"...\"},{\"text\":\"...\"},{\"text\":\"...\"}]}")
        }
    }

    private fun buildPolishUserPrompt(
        candidateCount: Int,
        styleProfile: ReplyStyleProfile,
        draftText: String
    ): String {
        return buildString {
            appendLine("请润色下面这段用户已经输入在微信输入框里的草稿，返回 $candidateCount 个不同版本。")
            appendLine("润色目标：${styleProfile.polishGoal.label}。")
            appendLine("角色风格：${styleProfile.persona.label}。")
            appendLine("要求：只改写表达，不新增事实、不替用户承诺现实动作；保留原意，适合直接回写到输入框。")
            appendLine("草稿：")
            appendLine(draftText.safeForPrompt())
            appendLine()
            appendLine("请严格返回 JSON：{\"candidates\":[{\"text\":\"...\"},{\"text\":\"...\"},{\"text\":\"...\"}]}")
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
