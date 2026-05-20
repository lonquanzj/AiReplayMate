package com.lonquanzj.aireplaymate.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class MlKitChineseOcrEngine(
    private val context: Context
) : OcrEngine {
    override suspend fun recognizeChatMessages(
        targetApp: String,
        reason: String
    ): OcrAttemptResult {
        val captureResult = AndroidScreenCaptureProvider(context)
            .captureOnce(OcrCapturePermissionStore.state.value)
        val bitmap = captureResult.bitmap
        if (!captureResult.success || bitmap == null) {
            return finish(
                targetApp = targetApp,
                reason = reason,
                result = OcrAttemptResult(
                    success = false,
                    category = if (captureResult.status == OcrScreenCaptureStatus.PERMISSION_MISSING) {
                        OcrAttemptCategory.CAPTURE_UNAVAILABLE
                    } else {
                        OcrAttemptCategory.UNKNOWN
                    },
                    message = captureResult.message,
                    steps = listOf("收到 OCR 请求：$reason") + captureResult.steps,
                    engineConfigured = true
                )
            )
        }

        return try {
            val recognizedText = recognize(bitmap)
            val processResult = recognizedText.toPostProcessResult(
                screenWidth = bitmap.width,
                screenHeight = bitmap.height
            )
            val messages = processResult.messages
            val processingSteps = listOf(
                "ML Kit 返回文本块：${recognizedText.textBlocks.size}",
                "OCR 原始行：${processResult.rawLineCount}",
                "OCR 保留行：${processResult.keptLineCount}",
                "OCR 过滤行：${processResult.droppedLineCount}",
                "OCR 聚合消息：${messages.size}"
            )
            finish(
                targetApp = targetApp,
                reason = reason,
                result = if (messages.isEmpty()) {
                    OcrAttemptResult(
                        success = false,
                        category = OcrAttemptCategory.NO_TEXT,
                        message = "OCR 未识别到可用聊天文本",
                        steps = listOf("收到 OCR 请求：$reason") +
                            captureResult.steps +
                            processingSteps,
                        engineConfigured = true
                    )
                } else {
                    OcrAttemptResult(
                        success = true,
                        category = OcrAttemptCategory.SUCCESS,
                        message = "OCR 识别到 ${messages.size} 条候选文本",
                        messages = messages,
                        steps = listOf("收到 OCR 请求：$reason") +
                            captureResult.steps +
                            processingSteps,
                        engineConfigured = true
                    )
                }
            )
        } catch (error: Throwable) {
            finish(
                targetApp = targetApp,
                reason = reason,
                result = OcrAttemptResult(
                    success = false,
                    category = OcrAttemptCategory.UNKNOWN,
                    message = "OCR 识别失败：${error.message ?: error.javaClass.simpleName}",
                    steps = listOf("收到 OCR 请求：$reason") +
                        captureResult.steps +
                        "异常：${error.javaClass.simpleName}",
                    engineConfigured = true
                )
            )
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun recognize(bitmap: Bitmap): Text {
        val recognizer = TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
        return try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        } finally {
            recognizer.close()
        }
    }

    private fun Text.toPostProcessResult(
        screenWidth: Int,
        screenHeight: Int
    ): OcrPostProcessResult {
        val lines = textBlocks
            .flatMap { block -> block.lines }
            .map { line ->
                OcrRecognizedLine(
                    text = line.text,
                    bounds = line.boundingBox
                )
            }
        return OcrTextPostProcessor.toChatMessages(
            lines = lines,
            screenWidth = screenWidth,
            screenHeight = screenHeight
        )
    }

    private fun finish(
        targetApp: String,
        reason: String,
        result: OcrAttemptResult
    ): OcrAttemptResult {
        OcrDebugStore.onAttempt(
            targetApp = targetApp,
            reason = reason,
            result = result
        )
        return result
    }

    private suspend fun <T> Task<T>.await(): T {
        return suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { value -> continuation.resume(value) }
            addOnFailureListener { error -> continuation.resumeWithException(error) }
            addOnCanceledListener { continuation.cancel() }
        }
    }

}
