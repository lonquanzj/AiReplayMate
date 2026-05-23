package com.lonquanzj.aireplaymate.overlay

import android.content.Context
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

internal class OverlayPanelHost(
    private val context: Context,
    private val windowManagerProvider: () -> WindowManager?,
    private val buttonLayoutParamsProvider: () -> WindowManager.LayoutParams?,
    private val stopProgressIndicatorAnimation: () -> Unit,
    private val startProgressIndicatorAnimation: (LinearLayout) -> Unit,
    private val animatePanelIn: (View) -> Unit
) {
    private var candidatePanelView: View? = null
    private var progressStatusView: TextView? = null
    private var progressIndicatorView: LinearLayout? = null

    fun removePanel() {
        // 进度点动画绑定在面板上，移除面板前先停掉动画避免泄漏。
        stopProgressIndicatorAnimation()
        candidatePanelView?.let { view ->
            windowManagerProvider()?.removeView(view)
        }
        candidatePanelView = null
        progressStatusView = null
        progressIndicatorView = null
    }

    fun showPanel(
        panel: View,
        desiredWidthDp: Int,
        desiredHeightDp: Int? = null,
        maxPanelHeight: Int? = null,
        animate: Boolean = false
    ) {
        candidatePanelView = panel
        val params = OverlayPanelLayoutCalculator.anchoredPanelLayoutParams(
            context = context,
            contentView = panel,
            buttonLayoutParams = buttonLayoutParamsProvider(),
            panelWidth = OverlayPanelLayoutCalculator.panelWidthPx(
                context = context,
                desiredDp = desiredWidthDp
            ),
            panelHeight = desiredHeightDp?.let(::dp) ?: WindowManager.LayoutParams.WRAP_CONTENT,
            maxPanelHeight = maxPanelHeight
        )
        windowManagerProvider()?.addView(panel, params)
        if (animate) {
            animatePanelIn(panel)
        }
    }

    fun updateAnchoredPanelLayout(
        panel: View,
        desiredWidthDp: Int,
        maxPanelHeight: Int? = null
    ) {
        if (panel.parent == null) return
        val nextParams = OverlayPanelLayoutCalculator.anchoredPanelLayoutParams(
            context = context,
            contentView = panel,
            buttonLayoutParams = buttonLayoutParamsProvider(),
            panelWidth = OverlayPanelLayoutCalculator.panelWidthPx(
                context = context,
                desiredDp = desiredWidthDp
            ),
            maxPanelHeight = maxPanelHeight
        )
        windowManagerProvider()?.updateViewLayout(panel, nextParams)
    }

    fun showProgressPanel(
        status: String,
        desiredWidthDp: Int
    ) {
        progressStatusView?.let { statusView ->
            // 如果进度面板已存在，只更新文案，避免反复 remove/add 造成闪烁。
            statusView.text = status
            return
        }

        removePanel()
        val progressViews = buildProgressPanelView(
            context = context,
            status = status
        )
        progressStatusView = progressViews.statusView
        progressIndicatorView = progressViews.indicator
        candidatePanelView = progressViews.panel
        val params = OverlayPanelLayoutCalculator.anchoredPanelLayoutParams(
            context = context,
            contentView = progressViews.panel,
            buttonLayoutParams = buttonLayoutParamsProvider(),
            panelWidth = dp(desiredWidthDp)
        )
        windowManagerProvider()?.addView(progressViews.panel, params)
        startProgressIndicatorAnimation(progressViews.indicator)
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
