package com.lonquanzj.aireplaymate.ocr

import com.lonquanzj.aireplaymate.accessibility.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OcrDebugState(
    val engineConfigured: Boolean = false,
    val lastCategory: OcrAttemptCategory = OcrAttemptCategory.NONE,
    val lastStatus: String = "未尝试",
    val lastReason: String = "",
    val targetApp: String = "",
    val extractedMessages: List<ChatMessage> = emptyList(),
    val steps: List<String> = emptyList(),
    val updatedAtMillis: Long = 0L
) {
    val extractedMessagePreviews: List<String>
        get() = extractedMessages.mapIndexed { index, message ->
            "${index + 1}. ${message.role.name} ${(message.confidence * 100).toInt()}% ${message.content}"
        }
}

object OcrDebugStore {
    private val _state = MutableStateFlow(OcrDebugState())
    val state: StateFlow<OcrDebugState> = _state.asStateFlow()

    fun reset() {
        _state.value = OcrDebugState()
    }

    fun onAttempt(
        targetApp: String,
        reason: String,
        result: OcrAttemptResult
    ) {
        _state.value = OcrDebugState(
            engineConfigured = result.engineConfigured,
            lastCategory = result.category,
            lastStatus = result.message,
            lastReason = reason,
            targetApp = targetApp,
            extractedMessages = result.messages,
            steps = result.steps,
            updatedAtMillis = System.currentTimeMillis()
        )
    }
}
