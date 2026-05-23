package com.lonquanzj.aireplaymate.accessibility

import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal suspend fun ReplyAccessibilityService.takeAccessibilityScreenshotBitmap(): AccessibilityScreenshotResult {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return AccessibilityScreenshotResult(
            success = false,
            message = "当前系统版本不支持无障碍截图",
            steps = listOf("无障碍截图：系统版本低于 Android 11")
        )
    }

    return suspendCancellableCoroutine { continuation ->
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val hardwareBuffer = screenshot.hardwareBuffer
                    val bitmap = Bitmap.wrapHardwareBuffer(
                        hardwareBuffer,
                        screenshot.colorSpace
                    )?.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBuffer.close()

                    continuation.resume(
                        if (bitmap == null) {
                            AccessibilityScreenshotResult(
                                success = false,
                                message = "无障碍截图转换失败",
                                steps = listOf("无障碍截图：Bitmap 转换失败")
                            )
                        } else {
                            AccessibilityScreenshotResult(
                                success = true,
                                bitmap = bitmap,
                                message = "已通过无障碍服务截图",
                                steps = listOf(
                                    "无障碍截图：成功",
                                    "无障碍截图尺寸：${bitmap.width}x${bitmap.height}"
                                )
                            )
                        }
                    )
                }

                override fun onFailure(errorCode: Int) {
                    continuation.resume(
                        AccessibilityScreenshotResult(
                            success = false,
                            message = "无障碍截图失败：$errorCode",
                            steps = listOf("无障碍截图：失败 $errorCode")
                        )
                    )
                }
            }
        )
    }
}
