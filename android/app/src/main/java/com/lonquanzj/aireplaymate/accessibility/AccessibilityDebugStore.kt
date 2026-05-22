package com.lonquanzj.aireplaymate.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AccessibilityDebugState(
    val serviceConnected: Boolean = false,
    val lastEventName: String = "暂无事件",
    val packageName: String = "",
    val className: String = "",
    val editableNodeCount: Int = 0,
    val visibleTextSample: List<String> = emptyList(),
    val isWechatPackage: Boolean = false,
    val looksLikeChatPage: Boolean = false,
    val chatDetectionReason: String = "",
    val conversationTitle: String? = null,
    val inputNodeFound: Boolean = false,
    val inputNodeHint: String? = null,
    val extractedMessages: List<ChatMessage> = emptyList(),
    val lastAutofillStatus: String = "未尝试",
    val lastAutofillCategory: AutofillFailureCategory = AutofillFailureCategory.NONE,
    val lastAutofillSteps: List<String> = emptyList(),
    val lastAutofillPreview: String? = null,
    val updatedAtMillis: Long = 0L
) {
    val extractedMessagePreviews: List<String>
        get() = extractedMessages.map { message ->
            "${message.role.label}: ${message.content}"
        }

    val extractedMessageDebugPreviews: List<String>
        get() = extractedMessages.mapIndexed { index, message ->
            val confidence = ((message.confidence * 100).toInt()).coerceIn(0, 100)
            val bounds = message.boundsHint ?: "no-bounds"
            "${index + 1}. ${message.role.label} ${confidence}% [$bounds] ${message.content}"
        }
}

object AccessibilityDebugStore {
    private val _state = MutableStateFlow(AccessibilityDebugState())
    val state: StateFlow<AccessibilityDebugState> = _state.asStateFlow()

    fun onServiceConnected() {
        _state.value = _state.value.copy(
            serviceConnected = true,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    fun onServiceInterrupted() {
        _state.value = _state.value.copy(
            serviceConnected = false,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    fun onWindowEvent(
        eventName: String,
        packageName: String,
        className: String,
        editableNodeCount: Int,
        visibleTextSample: List<String>,
        looksLikeChatPage: Boolean,
        chatDetectionReason: String,
        conversationTitle: String?,
        inputNodeFound: Boolean,
        inputNodeHint: String?,
        extractedMessages: List<ChatMessage>
    ) {
        _state.value = _state.value.copy(
            lastEventName = eventName,
            packageName = packageName,
            className = className,
            editableNodeCount = editableNodeCount,
            visibleTextSample = visibleTextSample,
            isWechatPackage = packageName == WECHAT_PACKAGE_NAME,
            looksLikeChatPage = looksLikeChatPage,
            chatDetectionReason = chatDetectionReason,
            conversationTitle = conversationTitle,
            inputNodeFound = inputNodeFound,
            inputNodeHint = inputNodeHint,
            extractedMessages = extractedMessages,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    fun onAutofillAttempt(result: AutofillAttemptResult, text: String) {
        _state.value = _state.value.copy(
            lastAutofillStatus = result.message,
            lastAutofillCategory = result.category,
            lastAutofillSteps = result.steps,
            lastAutofillPreview = text.take(30),
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    fun setState(state: AccessibilityDebugState) {
        _state.value = state
    }
}

private val ChatRole.label: String
    get() = when (this) {
        ChatRole.ME -> "我"
        ChatRole.FRIEND -> "对方"
        ChatRole.SYSTEM -> "系统"
        ChatRole.UNKNOWN -> "未知"
    }
