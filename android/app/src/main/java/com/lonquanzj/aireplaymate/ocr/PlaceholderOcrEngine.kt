package com.lonquanzj.aireplaymate.ocr

object PlaceholderOcrEngine : OcrEngine {
    override suspend fun recognizeChatMessages(
        targetApp: String,
        reason: String
    ): OcrAttemptResult {
        val captureState = OcrCapturePermissionStore.state.value
        val result = if (!captureState.isReady) {
            OcrAttemptResult(
                success = false,
                category = OcrAttemptCategory.CAPTURE_UNAVAILABLE,
                message = "OCR 兜底尚未获得屏幕截图授权",
                steps = listOf(
                    "收到兜底请求：$reason",
                    "屏幕截图授权：${captureState.status.label}",
                    "未创建 MediaProjection，返回空结果"
                )
            )
        } else {
            OcrAttemptResult(
                success = false,
                category = OcrAttemptCategory.ENGINE_UNAVAILABLE,
                message = "OCR 兜底已具备截图授权，但尚未接入识别引擎",
                steps = listOf(
                    "收到兜底请求：$reason",
                    "屏幕截图授权：已授权",
                    "当前未接入 OCR 识别引擎，返回空结果"
                )
            )
        }
        OcrDebugStore.onAttempt(
            targetApp = targetApp,
            reason = reason,
            result = result
        )
        return result
    }
}
