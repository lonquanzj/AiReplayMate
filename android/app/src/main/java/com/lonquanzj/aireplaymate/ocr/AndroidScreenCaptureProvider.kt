package com.lonquanzj.aireplaymate.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.lonquanzj.aireplaymate.accessibility.AccessibilityActionBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidScreenCaptureProvider(
    private val context: Context
) {
    suspend fun captureOnce(): OcrScreenCaptureResult = withContext(Dispatchers.Default) {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val densityDpi = metrics.densityDpi
        val steps = mutableListOf(
            "截图方式：无障碍服务",
            "目标尺寸：${width}x${height} / $densityDpi dpi"
        )

        val accessibilityResult = AccessibilityActionBridge.tryTakeScreenshot()
        steps += accessibilityResult.steps
        val accessibilityBitmap = accessibilityResult.bitmap
        if (accessibilityResult.success && accessibilityBitmap != null) {
            val debugImagePath = OcrDebugImageWriter.save(
                context = context,
                bitmap = accessibilityBitmap,
                label = "accessibilityFull"
            )
            return@withContext finish(
                OcrScreenCaptureResult(
                    success = true,
                    status = OcrScreenCaptureStatus.CAPTURED,
                    message = accessibilityResult.message,
                    bitmap = accessibilityBitmap,
                    width = accessibilityBitmap.width,
                    height = accessibilityBitmap.height,
                    frameStats = accessibilityBitmap.computeFrameStats().summary,
                    debugImagePath = debugImagePath,
                    steps = steps +
                        "截图来源：无障碍服务" +
                        "调试截图：${debugImagePath.ifBlank { "保存失败" }}"
                )
            )
        }
        finish(
            OcrScreenCaptureResult(
                success = false,
                status = OcrScreenCaptureStatus.PERMISSION_MISSING,
                message = accessibilityResult.message,
                width = width,
                height = height,
                steps = steps + "截图来源：无障碍服务不可用"
            )
        )
    }

    private fun finish(result: OcrScreenCaptureResult): OcrScreenCaptureResult {
        OcrScreenCaptureStore.onResult(result)
        return result
    }

    private fun Bitmap.computeFrameStats(): FrameStats {
        val sampleStepX = (width / FRAME_SAMPLE_GRID).coerceAtLeast(1)
        val sampleStepY = (height / FRAME_SAMPLE_GRID).coerceAtLeast(1)
        var count = 0
        var lumaSum = 0L
        var minLuma = 255
        var maxLuma = 0
        var nonDarkCount = 0

        var y = sampleStepY / 2
        while (y < height) {
            var x = sampleStepX / 2
            while (x < width) {
                val color = getPixel(x, y)
                val luma = (
                    Color.red(color) * 299 +
                        Color.green(color) * 587 +
                        Color.blue(color) * 114
                    ) / 1000
                lumaSum += luma
                minLuma = minOf(minLuma, luma)
                maxLuma = maxOf(maxLuma, luma)
                if (luma > NON_DARK_LUMA_THRESHOLD) nonDarkCount += 1
                count += 1
                x += sampleStepX
            }
            y += sampleStepY
        }

        if (count == 0) {
            return FrameStats(
                averageLuma = 0,
                lumaRange = 0,
                nonDarkRatio = 0f
            )
        }

        return FrameStats(
            averageLuma = (lumaSum / count).toInt(),
            lumaRange = maxLuma - minLuma,
            nonDarkRatio = nonDarkCount.toFloat() / count
        )
    }

    private data class FrameStats(
        val averageLuma: Int,
        val lumaRange: Int,
        val nonDarkRatio: Float
    ) {
        val summary: String
            get() = "avg=$averageLuma, range=$lumaRange, nonDark=${(nonDarkRatio * 100).toInt()}%"
    }

    private companion object {
        const val FRAME_SAMPLE_GRID = 24
        const val NON_DARK_LUMA_THRESHOLD = 16
    }
}
