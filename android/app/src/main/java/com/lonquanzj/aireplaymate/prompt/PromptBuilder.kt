package com.lonquanzj.aireplaymate.prompt

import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.accessibility.MessageSource
import com.lonquanzj.aireplaymate.accessibility.ChatMessage
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
        val candidateCount = settings.candidateCount.coerceAtLeast(1)
        return LlmRequest(
            systemPrompt = buildSystemPrompt(styleProfile),
            userPrompt = buildUserPrompt(context, candidateCount, styleProfile, draftText, settings.contextSendPolicy),
            temperature = LlmSampling.normalizeTemperature(settings.temperature),
            maxTokens = settings.maxTokens.coerceAtLeast(120),
            candidateCount = candidateCount
        )
    }

    private fun buildSystemPrompt(styleProfile: ReplyStyleProfile): String {
        return buildString {
            appendLine(DEFAULT_SYSTEM_PROMPT)
            appendLine(SAFETY_PROMPT)
            when (styleProfile.mode) {
                ReplyStyleMode.QUICK_REPLY -> {
                    appendLine("当前模式：快速回复。基于最近聊天自然回复，优先像真人手动输入，不要像营销话术。")
                }

                ReplyStyleMode.PLAYBOOK -> {
                    appendLine("当前模式：话术宝典。")
                }

                ReplyStyleMode.POLISH -> {
                    appendLine("当前模式：润色表达。")
                }
            }
            appendLine("表达要求：中文口语化、短句优先、有分寸；每条候选不超过 45 个字，除非上下文明显需要更长。")
            appendLine(OUTPUT_PROTOCOL_PROMPT)
        }.trim()
    }

    private fun buildUserPrompt(
        context: ChatContext,
        candidateCount: Int,
        styleProfile: ReplyStyleProfile,
        draftText: String?,
        contextSendPolicy: ContextSendPolicy
    ): String {
        return when (styleProfile.mode) {
            ReplyStyleMode.QUICK_REPLY -> buildQuickReplyPrompt(
                context = context,
                candidateCount = candidateCount,
                styleProfile = styleProfile,
                contextSendPolicy = contextSendPolicy
            )
            ReplyStyleMode.PLAYBOOK -> buildPlaybookPrompt(candidateCount, styleProfile)
            ReplyStyleMode.POLISH -> buildPolishPrompt(candidateCount, styleProfile, draftText.orEmpty())
        }
    }

    private fun buildQuickReplyPrompt(
        context: ChatContext,
        candidateCount: Int,
        styleProfile: ReplyStyleProfile,
        contextSendPolicy: ContextSendPolicy
    ): String {
        val visibleMessages = context.messages.takeLast(MAX_PROMPT_MESSAGES)
        val latestFriendMessage = when (contextSendPolicy) {
            ContextSendPolicy.FULL_CONTEXT -> visibleMessages.lastOrNull { it.role == ChatRole.FRIEND }
            ContextSendPolicy.LATEST_FRIEND_MESSAGE -> context.messages.lastOrNull { it.role == ChatRole.FRIEND }
        }
        val includesOcrContext = when (contextSendPolicy) {
            ContextSendPolicy.FULL_CONTEXT -> visibleMessages.any { it.isFromOcr }
            ContextSendPolicy.LATEST_FRIEND_MESSAGE -> latestFriendMessage?.isFromOcr == true
        }
        if (contextSendPolicy == ContextSendPolicy.LATEST_FRIEND_MESSAGE) {
            return buildString {
                appendLine("下面是微信单聊里最近一条对方消息。")
                appendLine("请基于这条消息生成 $candidateCount 条中文候选回复，要求自然、简洁、可直接填入输入框。")
                appendOcrContextHintIfNeeded(includesOcrContext)
                appendLine("本次角色：${styleProfile.personaConfig.label}。")
                appendLine("当前角色身份：${styleProfile.personaConfig.identityPrompt}")
                appendLine("当前角色提示词：${styleProfile.personaConfig.promptGuide}")
                appendLine()
                appendLine("最近一条对方消息：")
                appendLine(latestFriendMessage?.content.safeForPrompt("未识别到明确的对方消息"))
            }
        }
        return buildString {
            appendLine("下面是微信单聊的最近聊天上下文。")
            appendLine("请基于上下文生成 $candidateCount 条中文候选回复，要求自然、简洁、可直接填入输入框。")
            appendOcrContextHintIfNeeded(includesOcrContext)
            appendLine("本次角色：${styleProfile.personaConfig.label}。")
            appendLine("当前角色身份：${styleProfile.personaConfig.identityPrompt}")
            appendLine("当前角色提示词：${styleProfile.personaConfig.promptGuide}")
            appendLine()
            appendLine("最近一条对方消息：")
            appendLine(latestFriendMessage?.content.safeForPrompt("未识别到明确的对方消息"))
            appendLine()
            appendLine("聊天上下文：")
            visibleMessages.forEach { message ->
                appendLine("${message.role.promptLabel}: ${message.content.safeForPrompt()}")
            }
        }
    }

    private fun StringBuilder.appendOcrContextHintIfNeeded(includesOcrContext: Boolean) {
        if (includesOcrContext) {
            appendLine(OCR_CONTEXT_HINT)
        }
    }

    private fun buildPlaybookPrompt(
        candidateCount: Int,
        styleProfile: ReplyStyleProfile
    ): String {
        return buildString {
            appendLine("请直接生成 $candidateCount 条中文聊天话术，不需要参考聊天上下文。")
            appendLine("话术分类：${styleProfile.playbookConfig.categoryLabel}")
            appendLine("话术名称：${styleProfile.playbookConfig.label}")
            appendLine("话术身份：${styleProfile.playbookConfig.identityPrompt}")
            appendLine("话术提示词：${styleProfile.playbookConfig.promptGuide}")
            appendLine("叠加角色风格：${styleProfile.personaConfig.label}")
            appendLine("当前角色身份：${styleProfile.personaConfig.identityPrompt}")
            appendLine("当前角色提示词：${styleProfile.personaConfig.promptGuide}")
            appendLine("要求：可直接填入微信输入框，像真人临场写出来，不要出现模板变量、占位符、解释说明或列表编号。")
        }
    }

    private fun buildPolishPrompt(
        candidateCount: Int,
        styleProfile: ReplyStyleProfile,
        draftText: String
    ): String {
        return buildString {
            appendLine("请润色下面这段用户已经输入在微信输入框里的草稿，返回 $candidateCount 个不同版本。")
            appendLine("润色目标：${styleProfile.polishGoalConfig.label}")
            appendLine("润色身份：${styleProfile.polishGoalConfig.identityPrompt}")
            appendLine("润色提示词：${styleProfile.polishGoalConfig.promptGuide}")
            appendLine("叠加角色风格：${styleProfile.personaConfig.label}")
            appendLine("当前角色身份：${styleProfile.personaConfig.identityPrompt}")
            appendLine("当前角色提示词：${styleProfile.personaConfig.promptGuide}")
            appendLine("要求：只改写表达，不新增事实、不替用户承诺现实动作；保留原意，适合直接回写到输入框。")
            appendLine()
            appendLine("草稿：")
            appendLine(draftText.safeForPrompt())
        }
    }

    private fun String?.safeForPrompt(fallback: String = ""): String {
        return orEmpty()
            .replace(controlCharsRegex, " ")
            .trim()
            .ifBlank { fallback }
    }

    private val ChatRole.promptLabel: String
        get() = when (this) {
            ChatRole.ME -> "我"
            ChatRole.FRIEND -> "对方"
            ChatRole.SYSTEM -> "系统"
            ChatRole.UNKNOWN -> "未知"
        }

    private val ChatMessage.isFromOcr: Boolean
        get() = source == MessageSource.OCR || source == MessageSource.MERGED

    private const val MAX_PROMPT_MESSAGES = 20
    private val controlCharsRegex = Regex("[\\p{Cntrl}&&[^\n\t]]+")
    private const val DEFAULT_SYSTEM_PROMPT =
        "你是一个微信聊天回复助手。你只生成供用户审核后手动发送的候选回复。"
    private const val SAFETY_PROMPT =
        "安全边界：不得自动替用户承诺付款、合同、发送文件。"
    private const val OCR_CONTEXT_HINT =
        "上下文可能来自 OCR：理解语义前请温和纠正常见近形错、繁简差异和少量识别噪声。"
    private const val OUTPUT_PROTOCOL_PROMPT =
        "输出协议：请严格返回 JSON：{\"candidates\":[{\"text\":\"...\"},{\"text\":\"...\"},{\"text\":\"...\"}]}。不要使用列表编号、引号、解释性前缀。"
}
