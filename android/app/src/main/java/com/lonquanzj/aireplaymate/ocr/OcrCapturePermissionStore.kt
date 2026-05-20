package com.lonquanzj.aireplaymate.ocr

import android.app.Activity
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OcrCapturePermissionStatus(
    val label: String
) {
    NOT_REQUESTED("未请求"),
    READY("已授权"),
    TOKEN_USED("已使用"),
    DENIED("已拒绝")
}

data class OcrCapturePermissionState(
    val status: OcrCapturePermissionStatus = OcrCapturePermissionStatus.NOT_REQUESTED,
    val resultCode: Int? = null,
    val data: Intent? = null,
    val updatedAtMillis: Long = 0L
) {
    val isReady: Boolean
        get() = status == OcrCapturePermissionStatus.READY &&
            resultCode == Activity.RESULT_OK &&
            data != null
}

object OcrCapturePermissionStore {
    private val _state = MutableStateFlow(OcrCapturePermissionState())
    val state: StateFlow<OcrCapturePermissionState> = _state.asStateFlow()

    fun onPermissionResult(
        resultCode: Int,
        data: Intent?
    ) {
        _state.value = OcrCapturePermissionState(
            status = if (resultCode == Activity.RESULT_OK && data != null) {
                OcrCapturePermissionStatus.READY
            } else {
                OcrCapturePermissionStatus.DENIED
            },
            resultCode = resultCode,
            data = data,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    fun markTokenUsed() {
        _state.value = _state.value.copy(
            status = OcrCapturePermissionStatus.TOKEN_USED,
            updatedAtMillis = System.currentTimeMillis()
        )
    }
}
