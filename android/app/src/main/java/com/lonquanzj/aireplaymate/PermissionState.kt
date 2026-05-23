package com.lonquanzj.aireplaymate

import android.content.Context
import android.provider.Settings
import android.provider.Settings.Secure
import com.lonquanzj.aireplaymate.accessibility.ReplyAccessibilityService

internal data class PermissionSnapshot(
    val accessibilityEnabled: Boolean,
    val overlayEnabled: Boolean
)

internal fun readPermissionSnapshot(context: Context): PermissionSnapshot {
    val enabledServices = Secure.getString(
        context.contentResolver,
        Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()

    val targetService = "${context.packageName}/${ReplyAccessibilityService::class.java.name}"
    val accessibilityEnabled = enabledServices
        .split(':')
        .any { it.equals(targetService, ignoreCase = true) }

    return PermissionSnapshot(
        accessibilityEnabled = accessibilityEnabled,
        overlayEnabled = Settings.canDrawOverlays(context)
    )
}
