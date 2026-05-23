package com.lonquanzj.aireplaymate.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalogState
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import com.lonquanzj.aireplaymate.settings.AppSettingsTransfer
import com.lonquanzj.aireplaymate.settings.ReplyStyleCatalogStore
import com.lonquanzj.aireplaymate.settings.ReplyStyleSettingsStore

internal data class SettingsTransferActions(
    val importSettings: () -> Unit,
    val exportSettings: () -> Unit
)

@Composable
internal fun rememberSettingsTransferActions(
    appSettings: AppSettings,
    replyStyleProfile: ReplyStyleProfile,
    replyStyleCatalog: ReplyStyleCatalogState,
    saveAppSettings: (AppSettings) -> Unit,
    onAppSettingsImported: (AppSettings) -> Unit,
    onReplyStyleProfileImported: (ReplyStyleProfile) -> Unit,
    onReplyStyleCatalogImported: (ReplyStyleCatalogState) -> Unit
): SettingsTransferActions {
    val context = LocalContext.current
    val exportSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val payload = AppSettingsTransfer.encode(
                settings = appSettings,
                replyStyleProfile = replyStyleProfile,
                replyStyleCatalog = replyStyleCatalog
            )
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(payload)
            } ?: error("Unable to open export file")
        }.onSuccess {
            Toast.makeText(context, "配置已导出", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            Toast.makeText(context, "导出失败：${error.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
        }
    }
    val importSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: error("Unable to open import file")
            AppSettingsTransfer.decodeFull(raw).getOrThrow()
        }.onSuccess { importedConfig ->
            onAppSettingsImported(importedConfig.appSettings)
            saveAppSettings(importedConfig.appSettings)

            onReplyStyleCatalogImported(importedConfig.replyStyleCatalog)
            ReplyStyleCatalogStore.save(context, importedConfig.replyStyleCatalog)

            val resolvedProfile = importedConfig.replyStyleProfile
                .withResolvedCatalog(importedConfig.replyStyleCatalog)
            onReplyStyleProfileImported(resolvedProfile)
            ReplyStyleSettingsStore.save(context, resolvedProfile)
            Toast.makeText(context, "配置已导入", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            Toast.makeText(context, "导入失败：${error.message ?: "配置文件无效"}", Toast.LENGTH_LONG).show()
        }
    }

    return remember(importSettingsLauncher, exportSettingsLauncher) {
        SettingsTransferActions(
            importSettings = { importSettingsLauncher.launch(arrayOf("application/json", "text/*")) },
            exportSettings = { exportSettingsLauncher.launch("aireplaymate-config.json") }
        )
    }
}
