package com.lonquanzj.aireplaymate.ocr

import android.content.Context
import android.graphics.Bitmap
import com.lonquanzj.aireplaymate.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object OcrDebugImageWriter {
    val isEnabled: Boolean
        get() = BuildConfig.DEBUG

    fun save(
        context: Context,
        bitmap: Bitmap,
        label: String
    ): String {
        if (!isEnabled) return ""

        return runCatching {
            val dir = File(context.getExternalFilesDir(null), "ocr-debug")
            dir.mkdirs()
            dir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_DEBUG_IMAGES)
                ?.forEach { it.delete() }

            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
            val file = File(dir, "${timestamp}_${label.sanitizeLabel()}.png")
            file.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            file.absolutePath
        }.getOrDefault("")
    }

    private fun String.sanitizeLabel(): String {
        return replace(Regex("[^a-zA-Z0-9_.-]"), "_").take(40).ifBlank { "image" }
    }

    private const val MAX_DEBUG_IMAGES = 12
}
