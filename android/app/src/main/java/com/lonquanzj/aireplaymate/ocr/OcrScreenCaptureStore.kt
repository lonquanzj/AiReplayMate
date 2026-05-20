package com.lonquanzj.aireplaymate.ocr

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class OcrScreenCaptureStatus(
    val label: String
) {
    IDLE("未测试"),
    CAPTURED("已取图"),
    PERMISSION_MISSING("缺少授权"),
    NO_IMAGE("未取到图像"),
    FAILED("失败")
}

data class OcrScreenCaptureResult(
    val success: Boolean,
    val status: OcrScreenCaptureStatus,
    val message: String,
    val bitmap: Bitmap? = null,
    val width: Int? = null,
    val height: Int? = null,
    val rowStride: Int? = null,
    val pixelStride: Int? = null,
    val steps: List<String> = emptyList()
)

data class OcrScreenCaptureState(
    val status: OcrScreenCaptureStatus = OcrScreenCaptureStatus.IDLE,
    val message: String = "未测试",
    val width: Int? = null,
    val height: Int? = null,
    val rowStride: Int? = null,
    val pixelStride: Int? = null,
    val steps: List<String> = emptyList(),
    val updatedAtMillis: Long = 0L
) {
    val sizeLabel: String
        get() = if (width != null && height != null) {
            "${width}x${height}"
        } else {
            "暂无"
        }
}

object OcrScreenCaptureStore {
    private val _state = MutableStateFlow(OcrScreenCaptureState())
    val state: StateFlow<OcrScreenCaptureState> = _state.asStateFlow()

    fun onResult(result: OcrScreenCaptureResult) {
        _state.value = OcrScreenCaptureState(
            status = result.status,
            message = result.message,
            width = result.width,
            height = result.height,
            rowStride = result.rowStride,
            pixelStride = result.pixelStride,
            steps = result.steps,
            updatedAtMillis = System.currentTimeMillis()
        )
    }
}
