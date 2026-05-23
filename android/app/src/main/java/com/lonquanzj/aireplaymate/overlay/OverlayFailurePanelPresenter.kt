package com.lonquanzj.aireplaymate.overlay

import android.content.Context
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugState
import com.lonquanzj.aireplaymate.ocr.OcrDebugStore

internal fun showOverlayFailurePanel(
    context: Context,
    panelHost: OverlayPanelHost,
    message: String,
    debugState: AccessibilityDebugState,
    onOpenAccessibilitySettings: () -> Unit
) {
    panelHost.removePanel()
    val scrollView = buildFailurePanelView(
        context = context,
        message = message,
        debugState = debugState,
        ocrDebugState = OcrDebugStore.state.value,
        onClose = {
            OverlayDiagnosticsStore.onDone("用户关闭失败提示")
            panelHost.removePanel()
        },
        onOpenAccessibilitySettings = onOpenAccessibilitySettings
    )
    panelHost.showPanel(
        panel = scrollView,
        desiredWidthDp = FAILURE_PANEL_WIDTH_DP,
        desiredHeightDp = FAILURE_PANEL_HEIGHT_DP
    )
}

private const val FAILURE_PANEL_WIDTH_DP = 300
private const val FAILURE_PANEL_HEIGHT_DP = 500
