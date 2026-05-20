package com.lonquanzj.aireplaymate.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OverlayServiceStatus(
    val label: String
) {
    STOPPED("未运行"),
    STARTING("启动中"),
    RUNNING("运行中"),
    MISSING_PERMISSION("缺少权限")
}

data class OverlayServiceState(
    val status: OverlayServiceStatus = OverlayServiceStatus.STOPPED,
    val message: String = "气泡服务尚未启动",
    val bubbleVisible: Boolean = false,
    val updatedAtMillis: Long = 0L
)

object OverlayServiceStateStore {
    private val _state = MutableStateFlow(OverlayServiceState())
    val state: StateFlow<OverlayServiceState> = _state.asStateFlow()

    fun onStartRequested() {
        update(
            status = OverlayServiceStatus.STARTING,
            message = "已请求启动 AI 气泡",
            bubbleVisible = false
        )
    }

    fun onRunning(bubbleVisible: Boolean) {
        update(
            status = OverlayServiceStatus.RUNNING,
            message = if (bubbleVisible) {
                "AI 气泡服务运行中"
            } else {
                "AI 气泡服务运行中，但气泡视图尚未挂载"
            },
            bubbleVisible = bubbleVisible
        )
    }

    fun onMissingPermission() {
        update(
            status = OverlayServiceStatus.MISSING_PERMISSION,
            message = "缺少悬浮窗权限，无法显示 AI 气泡",
            bubbleVisible = false
        )
    }

    fun onStopped(message: String = "AI 气泡服务已停止") {
        update(
            status = OverlayServiceStatus.STOPPED,
            message = message,
            bubbleVisible = false
        )
    }

    private fun update(
        status: OverlayServiceStatus,
        message: String,
        bubbleVisible: Boolean
    ) {
        _state.value = OverlayServiceState(
            status = status,
            message = message,
            bubbleVisible = bubbleVisible,
            updatedAtMillis = System.currentTimeMillis()
        )
    }
}
