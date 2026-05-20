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
    val extractedMessages: List<String> = emptyList(),
    val lastAutofillStatus: String = "未尝试",
    val lastAutofillPreview: String? = null,
    val updatedAtMillis: Long = 0L
)

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
        extractedMessages: List<String>
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

    fun onAutofillAttempt(
        status: String,
        text: String
    ) {
        _state.value = _state.value.copy(
            lastAutofillStatus = status,
            lastAutofillPreview = text.take(30),
            updatedAtMillis = System.currentTimeMillis()
        )
    }
}

const val WECHAT_PACKAGE_NAME = "com.tencent.mm"
