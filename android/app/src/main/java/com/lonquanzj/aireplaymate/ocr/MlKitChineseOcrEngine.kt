package com.lonquanzj.aireplaymate.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
            .captureOnce()
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
            val recognized = recognizeWithFallbacks(bitmap)
            val processResult = OcrTextPostProcessor.toChatMessages(
                lines = recognized.lines,
                screenWidth = bitmap.width,
                screenHeight = bitmap.height
            )
            val messages = processResult.messages
            val processingSteps = listOf(
                "ML Kit 中文文本块：${recognized.chineseBlockCount}",
                "ML Kit 默认文本块：${recognized.latinBlockCount}",
                "ML Kit 识别轮次：${recognized.passSummaries.joinToString("；")}",
                "OCR 输入截图：${recognized.debugImageSummary}",
                "ML Kit 合并行：${recognized.lines.size}",
                "OCR 原始行：${processResult.rawLineCount}",
                "OCR 保留行：${processResult.keptLineCount}",
                "OCR 过滤行：${processResult.droppedLineCount}",
                "OCR 过滤原因：${processResult.filterSummaries.toStepSummary()}",
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
                        filterSummaries = processResult.filterSummaries,
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
                        filterSummaries = processResult.filterSummaries,
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

    private suspend fun recognizeWithFallbacks(bitmap: Bitmap): MlKitRecognitionResult {
        val passResults = mutableListOf(recognizeChatCrop(bitmap))
        val debugImagePaths = passResults.mapNotNull { it.debugImagePath.ifBlank { null } }

        return MlKitRecognitionResult(
            chineseBlockCount = passResults.sumOf { it.chineseBlockCount },
            latinBlockCount = passResults.sumOf { it.latinBlockCount },
            lines = passResults.flatMap { it.lines },
            passSummaries = passResults.map {
                "${it.passName}:zh=${it.chineseBlockCount},latin=${it.latinBlockCount},lines=${it.lines.size}"
            },
            debugImageSummary = if (OcrDebugImageWriter.isEnabled) {
                debugImagePaths.joinToString("；")
                    .ifBlank { "无" }
            } else {
                "已关闭（非调试构建）"
            }
        )
    }

    private suspend fun recognizeChatCrop(bitmap: Bitmap): MlKitRecognitionPassResult {
        val crop = Rect(
            0,
            (bitmap.height * CHAT_AREA_TOP_RATIO).toInt(),
            bitmap.width,
            (bitmap.height * CHAT_AREA_BOTTOM_RATIO).toInt()
        )
        val cropped = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height())
        return try {
            val cropDebugPath = OcrDebugImageWriter.save(
                context = context,
                bitmap = cropped,
                label = "chatCrop1x"
            )
            recognizePass(
                bitmap = cropped,
                passName = "chatCrop1x:${cropped.width}x${cropped.height}",
                crop = crop,
                scale = 1f
            ).copy(debugImagePath = cropDebugPath)
        } finally {
            cropped.recycle()
        }
    }

    private suspend fun recognizePass(
        bitmap: Bitmap,
        passName: String,
        crop: Rect,
        scale: Float
    ): MlKitRecognitionPassResult {
        val chineseText = recognize(
            bitmap = bitmap,
            label = "$passName/chinese"
        ) {
            TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        }
        val latinText = recognize(
            bitmap = bitmap,
            label = "$passName/latin"
        ) {
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
        return MlKitRecognitionPassResult(
            passName = passName,
            chineseBlockCount = chineseText.textBlocks.size,
            latinBlockCount = latinText.textBlocks.size,
            lines = chineseText.toRecognizedLines(crop, scale) + latinText.toRecognizedLines(crop, scale)
        )
    }

    private suspend fun recognize(
        bitmap: Bitmap,
        label: String,
        recognizerFactory: () -> com.google.mlkit.vision.text.TextRecognizer
    ): Text {
        val recognizer = recognizerFactory()
        return try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        } catch (error: Throwable) {
            throw IllegalStateException("$label recognizer failed: ${error.message}", error)
        } finally {
            recognizer.close()
        }
    }

    private fun Text.toRecognizedLines(
        crop: Rect,
        scale: Float
    ): List<OcrRecognizedLine> {
        return textBlocks
            .flatMap { block -> block.lines }
            .map { line ->
                OcrRecognizedLine(
                    text = line.text,
                    bounds = line.boundingBox?.toOriginalBounds(crop, scale)
                )
            }
    }

    private fun Rect.toOriginalBounds(
        crop: Rect,
        scale: Float
    ): Rect {
        return Rect(
            crop.left + (left / scale).toInt(),
            crop.top + (top / scale).toInt(),
            crop.left + (right / scale).toInt(),
            crop.top + (bottom / scale).toInt()
        )
    }

    private fun List<OcrFilterSummary>.toStepSummary(): String {
        if (isEmpty()) return "无"
        return joinToString("；") { "${it.reason.label} ${it.count}" }
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

    private data class MlKitRecognitionResult(
        val chineseBlockCount: Int,
        val latinBlockCount: Int,
        val lines: List<OcrRecognizedLine>,
        val passSummaries: List<String>,
        val debugImageSummary: String
    )

    private data class MlKitRecognitionPassResult(
        val passName: String,
        val chineseBlockCount: Int,
        val latinBlockCount: Int,
        val lines: List<OcrRecognizedLine>,
        val debugImagePath: String = ""
    )

    private companion object {
        const val CHAT_AREA_TOP_RATIO = 0.10f
        const val CHAT_AREA_BOTTOM_RATIO = 0.84f
    }

}
