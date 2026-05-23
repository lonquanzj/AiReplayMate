package com.lonquanzj.aireplaymate.overlay

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.lonquanzj.aireplaymate.R

/**
 * 进度面板返回对象，供 Service 保存引用并动态更新状态文案/动画。
 */
internal data class OverlayProgressPanelViews(
    val panel: LinearLayout,
    val indicator: LinearLayout,
    val statusView: TextView
)

/**
 * 构建 AI 进行中面板。
 */
internal fun buildProgressPanelView(
    context: Context,
    status: String
): OverlayProgressPanelViews {
    val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dpPx(12), context.dpPx(10), context.dpPx(12), context.dpPx(10))
        background = context.overlayPanelBackground()
        elevation = PANEL_ELEVATION
    }
    panel.addView(
        TextView(context).apply {
            text = context.getString(R.string.overlay_progress_title)
            textSize = TEXT_SIZE_15
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_TEXT_PRIMARY)
        },
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )

    val indicator = createProgressIndicator(context)
    panel.addView(
        indicator,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = context.dpPx(8)
        }
    )

    val statusView = TextView(context).apply {
        text = status
        textSize = TEXT_SIZE_13
        setTextColor(COLOR_TEXT_SECONDARY)
        setPadding(0, context.dpPx(8), 0, 0)
    }
    panel.addView(
        statusView,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )

    return OverlayProgressPanelViews(
        panel = panel,
        indicator = indicator,
        statusView = statusView
    )
}

// 三点指示器仅负责结构，具体呼吸动画由 OverlayPanelAnimationController 驱动。
private fun createProgressIndicator(context: Context): LinearLayout {
    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        repeat(3) { index ->
            addView(
                View(context).apply {
                    background = roundedBackground(
                        when (index) {
                            1 -> COLOR_PROGRESS_DOT_IDLE
                            else -> COLOR_PROGRESS_DOT_ACTIVE
                        },
                        context.dpPx(5).toFloat()
                    )
                },
                LinearLayout.LayoutParams(context.dpPx(10), context.dpPx(10)).apply {
                    if (index > 0) {
                        marginStart = context.dpPx(8)
                    }
                }
            )
        }
    }
}
