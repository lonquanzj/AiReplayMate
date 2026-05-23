package com.lonquanzj.aireplaymate.overlay

import android.content.Context
import android.widget.ScrollView
import android.widget.Toast
import com.lonquanzj.aireplaymate.accessibility.AccessibilityActionBridge
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import com.lonquanzj.aireplaymate.settings.ReplyStyleCatalogStore
import com.lonquanzj.aireplaymate.settings.ReplyStyleSettingsStore

internal class OverlayStyleMenuLauncher(
    private val context: Context,
    private val panelHost: OverlayPanelHost,
    private val isGeneratingCandidates: () -> Boolean,
    private val onProfileChosen: (ReplyStyleProfile, String?) -> Unit
) {
    fun show() {
        if (isGeneratingCandidates()) {
            Toast.makeText(context, "正在生成候选回复，请稍等", Toast.LENGTH_SHORT).show()
            return
        }

        val current = ReplyStyleSettingsStore.load(context)
        val catalog = ReplyStyleCatalogStore.load(context)
        panelHost.removePanel()

        var styleMenuScrollView: ScrollView? = null
        val builtScrollView = buildStyleMenuPanelView(
            context = context,
            current = current,
            catalog = catalog,
            onClose = { panelHost.removePanel() },
            onProfileChosen = { profile, persistAsDefault, requiresDraft ->
                val draftText = if (requiresDraft) {
                    val readResult = AccessibilityActionBridge.tryReadInputDraft()
                    if (!readResult.success) {
                        Toast.makeText(context, readResult.message, Toast.LENGTH_SHORT).show()
                        return@buildStyleMenuPanelView
                    }
                    readResult.text
                } else {
                    null
                }
                if (persistAsDefault) {
                    ReplyStyleSettingsStore.save(context, profile.asDefaultReply())
                }
                panelHost.removePanel()
                onProfileChosen(profile, draftText)
            },
            onLayoutRefreshRequested = {
                val attachedScrollView = styleMenuScrollView
                if (attachedScrollView?.parent != null) {
                    panelHost.updateAnchoredPanelLayout(
                        panel = attachedScrollView,
                        desiredWidthDp = STYLE_MENU_PANEL_WIDTH_DP,
                        maxPanelHeight = OverlayPanelLayoutCalculator.styleMenuMaxHeightPx(context)
                    )
                }
            }
        )
        styleMenuScrollView = builtScrollView
        panelHost.showPanel(
            panel = builtScrollView,
            desiredWidthDp = STYLE_MENU_PANEL_WIDTH_DP,
            maxPanelHeight = OverlayPanelLayoutCalculator.styleMenuMaxHeightPx(context),
            animate = true
        )
    }

    private companion object {
        const val STYLE_MENU_PANEL_WIDTH_DP = 280
    }
}
