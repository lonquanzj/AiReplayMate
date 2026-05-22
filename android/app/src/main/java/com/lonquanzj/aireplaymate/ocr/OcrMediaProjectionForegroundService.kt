package com.lonquanzj.aireplaymate.ocr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.delay

class OcrMediaProjectionForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        createChannel()
        isRunning = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "OCR 截图",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "OCR 兜底截图时短暂运行"
            }
        )
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("AiReplayMate 正在识别屏幕")
                .setContentText("用于 OCR 兜底提取聊天文本")
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("AiReplayMate 正在识别屏幕")
                .setContentText("用于 OCR 兜底提取聊天文本")
                .setOngoing(true)
                .build()
        }
    }

    companion object {
        private const val CHANNEL_ID = "ocr_media_projection"
        private const val NOTIFICATION_ID = 2001
        @Volatile
        private var isRunning: Boolean = false

        suspend fun ensureStarted(context: Context): Boolean {
            val appContext = context.applicationContext
            return runCatching {
                appContext.startForegroundService(
                    Intent(appContext, OcrMediaProjectionForegroundService::class.java)
                )
                repeat(FOREGROUND_WAIT_RETRY_COUNT) {
                    if (isRunning) return true
                    delay(FOREGROUND_WAIT_INTERVAL_MS)
                }
                isRunning
            }.getOrDefault(false)
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            runCatching {
                appContext.stopService(
                    Intent(appContext, OcrMediaProjectionForegroundService::class.java)
                )
            }
            isRunning = false
        }

        private const val FOREGROUND_WAIT_RETRY_COUNT = 10
        private const val FOREGROUND_WAIT_INTERVAL_MS = 80L
    }
}
