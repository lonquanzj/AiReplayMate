package com.lonquanzj.aireplaymate.overlay

import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OverlayTriggerRequest(
    val id: Long,
    val debugState: AccessibilityDebugState
)

object OverlayTriggerStore {
    private val _request = MutableStateFlow<OverlayTriggerRequest?>(null)
    val request: StateFlow<OverlayTriggerRequest?> = _request.asStateFlow()

    fun requestRun(debugState: AccessibilityDebugState) {
        _request.value = OverlayTriggerRequest(
            id = System.currentTimeMillis(),
            debugState = debugState
        )
    }

    fun consume(id: Long) {
        if (_request.value?.id == id) {
            _request.value = null
        }
    }
}
