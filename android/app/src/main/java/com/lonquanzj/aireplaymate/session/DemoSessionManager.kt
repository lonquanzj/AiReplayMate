package com.lonquanzj.aireplaymate.session

import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugState
import com.lonquanzj.aireplaymate.accessibility.ChatMessage
import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.accessibility.MessageSource
import com.lonquanzj.aireplaymate.context.ChatContext
import com.lonquanzj.aireplaymate.context.ConversationType
import com.lonquanzj.aireplaymate.context.DefaultContextBuilder
import com.lonquanzj.aireplaymate.demo.DemoAuthor
import com.lonquanzj.aireplaymate.demo.DemoCandidate
import com.lonquanzj.aireplaymate.demo.DemoMessage
import com.lonquanzj.aireplaymate.ocr.PlaceholderOcrEngine
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class SessionState(
    val title: String,
    val detail: String,
    val progress: Int
) {
    IDLE("待机", "等待你触发一次回复生成", -1),
    VALIDATING_TARGET("校验目标页面", "确认当前处于微信单聊场景", 0),
    COLLECTING_ACCESSIBILITY("提取聊天上下文", "优先使用 Accessibility 抽取最近可见消息", 1),
    COLLECTING_OCR("OCR 兜底", "当 Accessibility 上下文不足时尝试 OCR 补齐", 2),
    BUILDING_CONTEXT("整理上下文", "过滤系统消息、去重并截取最近对话", 3),
    REQUESTING_LLM("生成候选回复", "根据上下文生成 3 条可直接发送的回复", 4),
    CANDIDATE_READY("候选已就绪", "底部面板展示候选，等待用户选择", 5),
    AUTOFILLING("自动填入", "把选中的回复写入输入框", 6),
    DONE("生成完成", "候选已填入输入框，用户仍保留最终发送权", 7),
    FAILED("执行失败", "当前条件不满足主链路要求，请调整后重试", -1),
    CANCELLED("已取消", "候选面板已关闭，本次会话结束", -1)
}

data class SessionUiState(
    val currentState: SessionState = SessionState.IDLE,
    val progressState: SessionState = SessionState.IDLE,
    val isRunning: Boolean = false,
    val showCandidateSheet: Boolean = false,
    val extractedMessages: List<DemoMessage> = emptyList(),
    val candidates: List<DemoCandidate> = emptyList(),
    val replyDraft: String = "",
    val activityLog: List<String> = emptyList(),
    val generationRound: Int = 0,
    val usesLiveAccessibility: Boolean = false,
    val statusNote: String? = null
)

class DemoSessionManager {
    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    suspend fun run(
        debugState: AccessibilityDebugState,
        styleProfile: ReplyStyleProfile = ReplyStyleProfile()
    ) {
        val current = _state.value
        if (current.isRunning) {
            appendLog("已有会话进行中，已忽略新的触发请求")
            return
        }

        _state.value = SessionUiState(
            currentState = SessionState.VALIDATING_TARGET,
            progressState = SessionState.VALIDATING_TARGET,
            isRunning = true
        )

        val liveTargetError = validateLiveTarget(debugState)
        appendLog("开始校验微信聊天页并准备整理上下文")
        delay(450)

        if (liveTargetError != null) {
            fail(liveTargetError)
            return
        }

        _state.update {
            it.copy(
                currentState = SessionState.COLLECTING_ACCESSIBILITY,
                progressState = SessionState.COLLECTING_ACCESSIBILITY,
                usesLiveAccessibility = true
            )
        }

        val rawMessages = debugState.extractedMessages

        appendLog(
            if (rawMessages.isNotEmpty()) {
                "Accessibility 提取到 ${rawMessages.size} 条真实消息"
            } else {
                "Accessibility 暂未提取到消息，准备尝试 OCR 兜底"
            }
        )
        delay(700)

        _state.update {
            it.copy(
                extractedMessages = rawMessages.toDemoMessages(),
                currentState = SessionState.BUILDING_CONTEXT,
                progressState = SessionState.BUILDING_CONTEXT
            )
        }

        var context = DefaultContextBuilder.build(
            accessibilityMessages = rawMessages,
            targetApp = WECHAT_TARGET_APP,
            conversationType = ConversationType.SINGLE_CHAT
        )

        if (!context.enoughForReply) {
            _state.update {
                it.copy(
                    currentState = SessionState.COLLECTING_OCR,
                    progressState = SessionState.COLLECTING_OCR
                )
            }
            appendLog("Accessibility 上下文不足，开始尝试 OCR 兜底")
            val ocrResult = PlaceholderOcrEngine.recognizeChatMessages(
                targetApp = WECHAT_TARGET_APP,
                reason = "首页整理上下文前补齐消息"
            )
            appendLog("OCR 兜底：${ocrResult.message}")
            delay(350)

            context = DefaultContextBuilder.build(
                accessibilityMessages = rawMessages,
                ocrMessages = ocrResult.messages,
                targetApp = WECHAT_TARGET_APP,
                conversationType = ConversationType.SINGLE_CHAT
            )
        }

        if (!context.enoughForReply) {
            fail("上下文里没有足够的对方消息，暂不生成候选")
            return
        }

        appendLog(
            if (context.isLowConfidence) {
                "已整理 ${context.messages.size} 条上下文，数量或置信度偏低"
            } else {
                "已整理 ${context.messages.size} 条可用于生成的上下文"
            }
        )
        delay(350)

        _state.update {
            it.copy(
                extractedMessages = context.messages.toDemoMessages(),
                currentState = SessionState.REQUESTING_LLM,
                progressState = SessionState.REQUESTING_LLM
            )
        }

        appendLog("正在根据最近一条消息生成回复候选")
        delay(900)

        val nextRound = _state.value.generationRound + 1
        val candidates = context.toUiCandidates(styleProfile, nextRound)

        _state.update {
            it.copy(
                currentState = SessionState.CANDIDATE_READY,
                progressState = SessionState.CANDIDATE_READY,
                candidates = candidates,
                generationRound = nextRound,
                showCandidateSheet = true,
                isRunning = false
            )
        }
        appendLog("已返回 3 条候选，等待用户选择")
    }

    fun regenerateCandidates(
        styleProfile: ReplyStyleProfile = ReplyStyleProfile()
    ) {
        val current = _state.value
        if (current.currentState != SessionState.CANDIDATE_READY) return

        val nextRound = current.generationRound + 1
        val messages = current.extractedMessages
        val context = DefaultContextBuilder.build(
            accessibilityMessages = messages.toChatMessages(),
            targetApp = WECHAT_TARGET_APP,
            conversationType = ConversationType.SINGLE_CHAT
        )
        _state.update {
            it.copy(
                candidates = context.toUiCandidates(styleProfile, nextRound),
                generationRound = nextRound
            )
        }
        appendLog("已按 ${styleProfile.displayLabel} 重新生成 3 条候选")
    }

    suspend fun selectCandidate(candidate: DemoCandidate) {
        if (_state.value.currentState != SessionState.CANDIDATE_READY) return

        _state.update {
            it.copy(
                currentState = SessionState.AUTOFILLING,
                progressState = SessionState.AUTOFILLING,
                isRunning = true,
                showCandidateSheet = false,
                statusNote = null
            )
        }
        appendLog("用户选择了 ${candidate.tone} 风格候选")
        delay(350)

        _state.update {
            it.copy(
                replyDraft = candidate.text,
                currentState = SessionState.DONE,
                progressState = SessionState.DONE,
                isRunning = false
            )
        }
        appendLog("已把候选填入模拟输入框")
    }

    fun cancelCandidateSelection(reason: String) {
        _state.update {
            it.copy(
                currentState = SessionState.CANCELLED,
                isRunning = false,
                showCandidateSheet = false,
                statusNote = reason
            )
        }
        appendLog(reason)
    }

    fun reset() {
        _state.value = SessionUiState(
            activityLog = listOf("已重置当前状态")
        )
    }

    fun updateReplyDraft(text: String) {
        _state.update { it.copy(replyDraft = text) }
    }

    fun noteRealAutofillResult(message: String) {
        appendLog("真实填入：$message")
    }

    private fun fail(reason: String) {
        _state.update {
            it.copy(
                currentState = SessionState.FAILED,
                isRunning = false,
                showCandidateSheet = false,
                statusNote = reason
            )
        }
        appendLog(reason)
    }

    private fun appendLog(message: String) {
        _state.update { current ->
            current.copy(activityLog = current.activityLog + message)
        }
    }

    private fun validateLiveTarget(debugState: AccessibilityDebugState): String? {
        if (!debugState.serviceConnected) {
            return "无障碍服务未连接，请先开启后再试"
        }

        if (!debugState.isWechatPackage) {
            return "无障碍已连接，但当前不在微信页面，请先切回微信单聊"
        }

        if (!debugState.looksLikeChatPage) {
            return "当前页面还不像微信聊天页，暂不进入真实主链路"
        }

        return null
    }

    private fun List<ChatMessage>.toDemoMessages(): List<DemoMessage> {
        return takeLast(8).map { message ->
            DemoMessage(
                author = message.role.toDemoAuthor(),
                content = message.content
            )
        }
    }

    private fun List<DemoMessage>.toChatMessages(): List<ChatMessage> {
        return mapIndexed { index, message ->
            ChatMessage(
                id = "demo_${index}_${message.content.hashCode().toUInt().toString(16)}",
                role = message.author.toChatRole(),
                content = message.content,
                timestamp = null,
                source = MessageSource.ACCESSIBILITY,
                confidence = 1f
            )
        }
    }

    private fun ChatRole.toDemoAuthor(): DemoAuthor {
        return when (this) {
            ChatRole.ME -> DemoAuthor.ME
            ChatRole.FRIEND -> DemoAuthor.FRIEND
            ChatRole.SYSTEM -> DemoAuthor.SYSTEM
            ChatRole.UNKNOWN -> DemoAuthor.FRIEND
        }
    }

    private fun DemoAuthor.toChatRole(): ChatRole {
        return when (this) {
            DemoAuthor.ME -> ChatRole.ME
            DemoAuthor.FRIEND -> ChatRole.FRIEND
            DemoAuthor.SYSTEM -> ChatRole.SYSTEM
        }
    }

    private fun ChatContext.toUiCandidates(
        styleProfile: ReplyStyleProfile,
        round: Int
    ): List<DemoCandidate> {
        val seed = "$WECHAT_TARGET_APP:$round:" +
            messages.joinToString("|") { it.content }
        return LocalFallbackReplyGenerator.generate(
            context = this,
            styleProfile = styleProfile,
            seed = seed
        ).map { candidate ->
            DemoCandidate(
                id = "demo_${candidate.id}",
                text = candidate.text,
                tone = candidate.tone ?: "本地兜底"
            )
        }
    }

    private companion object {
        const val WECHAT_TARGET_APP = "wechat"
    }
}
