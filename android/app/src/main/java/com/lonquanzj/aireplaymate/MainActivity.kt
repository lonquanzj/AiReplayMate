package com.lonquanzj.aireplaymate

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lonquanzj.aireplaymate.diagnostics.DiagnosticLogStore
import com.lonquanzj.aireplaymate.overlay.OverlayButtonService
import com.lonquanzj.aireplaymate.overlay.OverlayServiceStateStore
import com.lonquanzj.aireplaymate.settings.AppSettingsStore
import com.lonquanzj.aireplaymate.ui.MainScreen
import com.lonquanzj.aireplaymate.ui.theme.AiReplayMateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        DiagnosticLogStore.initialize(this)

        setContent {
            AiReplayMateTheme {
                MainScreen(
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenOverlaySettings = {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    },
                    onStartOverlayService = {
                        if (Settings.canDrawOverlays(this)) {
                            OverlayServiceStateStore.onStartRequested()
                            startService(Intent(this, OverlayButtonService::class.java))
                        } else {
                            OverlayServiceStateStore.onMissingPermission()
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                        }
                    },
                    onStopOverlayService = {
                        OverlayServiceStateStore.onStopped("已请求停止 AI 气泡")
                        stopService(Intent(this, OverlayButtonService::class.java))
                    },
                    loadPermissionSnapshot = { readPermissionSnapshot(this) },
                    loadAppSettings = { AppSettingsStore.load(this) },
                    saveAppSettings = { AppSettingsStore.save(this, it) }
                )
            }
        }
    }
}
