package com.lonquanzj.aireplaymate.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 负责 Overlay 面板的尺寸与锚点定位计算。
 *
 * 规则：优先放在悬浮球下方，放不下时自动放到上方，并保持安全边距。
 */
internal object OverlayPanelLayoutCalculator {
    /**
     * 计算一个锚定在悬浮球附近的 panel LayoutParams。
     */
    fun anchoredPanelLayoutParams(
        context: Context,
        contentView: View,
        buttonLayoutParams: WindowManager.LayoutParams?,
        panelWidth: Int,
        panelHeight: Int = WindowManager.LayoutParams.WRAP_CONTENT,
        maxPanelHeight: Int? = null
    ): WindowManager.LayoutParams {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val horizontalMargin = context.dpPx(16)
        val verticalMargin = context.dpPx(16)
        val gap = context.dpPx(8)
        val bubbleX = buttonLayoutParams?.x ?: horizontalMargin
        val bubbleY = buttonLayoutParams?.y ?: context.dpPx(220)
        val bubbleWidth = buttonLayoutParams?.width?.takeIf { it > 0 } ?: context.dpPx(44)
        val bubbleHeight = buttonLayoutParams?.height?.takeIf { it > 0 } ?: context.dpPx(44)

        val actualPanelWidth = panelWidth.coerceAtMost(screenWidth - horizontalMargin * 2)
        val panelX = horizontalMargin
        val measuredHeight = resolvePanelHeight(contentView, actualPanelWidth, panelHeight)
        val actualPanelHeight = maxPanelHeight
            ?.let { measuredHeight.coerceAtMost(it) }
            ?: panelHeight

        val belowY = bubbleY + bubbleHeight + gap
        val layoutHeight = if (actualPanelHeight > 0) actualPanelHeight else measuredHeight
        val aboveY = bubbleY - layoutHeight - gap
        val panelY = if (belowY + layoutHeight + verticalMargin <= screenHeight) {
            belowY
        } else {
            aboveY.coerceAtLeast(verticalMargin)
        }

        return WindowManager.LayoutParams(
            actualPanelWidth,
            actualPanelHeight,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = panelX
            y = panelY
        }
    }

    fun panelWidthPx(
        context: Context,
        desiredDp: Int
    ): Int {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val horizontalMargin = context.dpPx(16)
        return minOf(context.dpPx(desiredDp), screenWidth - horizontalMargin * 2)
    }

    fun styleMenuMaxHeightPx(context: Context): Int {
        return (context.resources.displayMetrics.heightPixels * 0.38f).toInt()
    }

    private fun resolvePanelHeight(
        contentView: View,
        panelWidth: Int,
        panelHeight: Int
    ): Int {
        if (panelHeight > 0) return panelHeight
        val widthSpec = View.MeasureSpec.makeMeasureSpec(panelWidth, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        contentView.measure(widthSpec, heightSpec)
        return contentView.measuredHeight
    }
}
