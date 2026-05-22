package com.lonquanzj.aireplaymate.ocr

import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class AndroidScreenCaptureProvider(
    private val context: Context
) {
    suspend fun captureOnce(
        permissionState: OcrCapturePermissionState
    ): OcrScreenCaptureResult = withContext(Dispatchers.Default) {
        if (!permissionState.isReady || permissionState.resultCode == null || permissionState.data == null) {
            return@withContext finish(
                OcrScreenCaptureResult(
                    success = false,
                    status = OcrScreenCaptureStatus.PERMISSION_MISSING,
                    message = "请先完成屏幕截图授权",
                    steps = listOf("截图授权：${permissionState.status.label}")
                )
            )
        }

        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val densityDpi = metrics.densityDpi
        val steps = mutableListOf(
            "截图授权：已授权",
            "目标尺寸：${width}x${height} / $densityDpi dpi"
        )

        val foregroundStarted = OcrMediaProjectionForegroundService.ensureStarted(context)
        if (!foregroundStarted) {
            return@withContext finish(
                OcrScreenCaptureResult(
                    success = false,
                    status = OcrScreenCaptureStatus.FAILED,
                    message = "启动截图前台服务失败，请确认通知权限和前台服务权限可用",
                    steps = steps + "MediaProjection 前台服务：启动失败"
                )
            )
        }
        steps += "MediaProjection 前台服务：已启动"

        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        var projection: MediaProjection? = null
        var reader: ImageReader? = null
        var image: Image? = null
        var callback: MediaProjection.Callback? = null
        val virtualDisplay = try {
            projection = manager.getMediaProjection(permissionState.resultCode, permissionState.data)
            OcrCapturePermissionStore.markTokenUsed()
            callback = object : MediaProjection.Callback() {}
            projection.registerCallback(callback, Handler(Looper.getMainLooper()))
            reader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
            steps += "MediaProjection 已创建"
            projection.createVirtualDisplay(
                "AiReplayMateOcrCapture",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface,
                null,
                null
            )
        } catch (error: Throwable) {
            callback?.let { projection?.unregisterCallback(it) }
            projection?.stop()
            reader?.close()
            OcrMediaProjectionForegroundService.stop(context)
            return@withContext finish(
                OcrScreenCaptureResult(
                    success = false,
                    status = OcrScreenCaptureStatus.FAILED,
                    message = "创建截图数据流失败：${error.message ?: error.javaClass.simpleName}",
                    steps = steps + "异常：${error.javaClass.simpleName}"
                )
            )
        }

        try {
            steps += "VirtualDisplay 已创建，等待首帧"
            repeat(MAX_FRAME_WAIT_COUNT) {
                delay(FRAME_WAIT_INTERVAL_MS)
                if (image == null) {
                    image = reader?.acquireLatestImage()
                }
            }

            val capturedImage = image
            if (capturedImage == null) {
                finish(
                    OcrScreenCaptureResult(
                        success = false,
                        status = OcrScreenCaptureStatus.NO_IMAGE,
                        message = "截图数据流已创建，但未取到首帧",
                        width = width,
                        height = height,
                        steps = steps + "首帧：超时"
                    )
                )
            } else {
                val plane = capturedImage.planes.firstOrNull()
                val bitmap = capturedImage.toBitmap()
                finish(
                    OcrScreenCaptureResult(
                        success = true,
                        status = OcrScreenCaptureStatus.CAPTURED,
                        message = "已成功抓取一帧屏幕图像",
                        bitmap = bitmap,
                        width = capturedImage.width,
                        height = capturedImage.height,
                        rowStride = plane?.rowStride,
                        pixelStride = plane?.pixelStride,
                        steps = steps + "首帧：成功"
                    )
                )
            }
        } catch (error: Throwable) {
            finish(
                OcrScreenCaptureResult(
                    success = false,
                    status = OcrScreenCaptureStatus.FAILED,
                    message = "读取截图失败：${error.message ?: error.javaClass.simpleName}",
                    width = width,
                    height = height,
                    steps = steps + "异常：${error.javaClass.simpleName}"
                )
            )
        } finally {
            image?.close()
            virtualDisplay?.release()
            callback?.let { projection?.unregisterCallback(it) }
            projection?.stop()
            reader?.close()
            OcrMediaProjectionForegroundService.stop(context)
        }
    }

    private fun finish(result: OcrScreenCaptureResult): OcrScreenCaptureResult {
        OcrScreenCaptureStore.onResult(result)
        return result
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes.first()
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val paddedWidth = width + rowPadding / pixelStride
        val paddedBitmap = Bitmap.createBitmap(
            paddedWidth,
            height,
            Bitmap.Config.ARGB_8888
        )
        paddedBitmap.copyPixelsFromBuffer(plane.buffer)
        val cropped = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
        if (cropped !== paddedBitmap) {
            paddedBitmap.recycle()
        }
        return cropped
    }

    private companion object {
        const val MAX_FRAME_WAIT_COUNT = 10
        const val FRAME_WAIT_INTERVAL_MS = 120L
    }
}
