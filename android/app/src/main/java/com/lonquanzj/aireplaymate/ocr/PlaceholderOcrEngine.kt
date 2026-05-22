package com.lonquanzj.aireplaymate.ocr

object PlaceholderOcrEngine : OcrEngine {
    override suspend fun recognizeChatMessages(
        targetApp: String,
        reason: String
    ): OcrAttemptResult {
        val result = OcrAttemptResult(
            success = false,
            category = OcrAttemptCategory.ENGINE_UNAVAILABLE,
            message = "OCR 兜底尚未接入识别引擎",
            steps = listOf(
                "收到兜底请求：$reason",
                "截图方式：无障碍服务",
                "当前未接入 OCR 识别引擎，返回空结果"
            )
        )
        OcrDebugStore.onAttempt(
            targetApp = targetApp,
            reason = reason,
            result = result
        )
        return result
    }
}
