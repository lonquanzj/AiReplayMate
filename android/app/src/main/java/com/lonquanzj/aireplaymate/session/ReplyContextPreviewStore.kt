package com.lonquanzj.aireplaymate.session

import com.lonquanzj.aireplaymate.accessibility.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ReplyContextPreviewState(
    val conversationTitle: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val updatedAtMillis: Long = 0L
)

object ReplyContextPreviewStore {
    private val _state = MutableStateFlow(ReplyContextPreviewState())
    val state: StateFlow<ReplyContextPreviewState> = _state.asStateFlow()

    fun update(
        conversationTitle: String?,
        messages: List<ChatMessage>
    ) {
        _state.value = ReplyContextPreviewState(
            conversationTitle = conversationTitle,
            messages = messages,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    fun clear() {
        _state.value = ReplyContextPreviewState()
    }
}
