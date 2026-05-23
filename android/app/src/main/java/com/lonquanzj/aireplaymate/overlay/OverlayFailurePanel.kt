package com.lonquanzj.aireplaymate.overlay

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.lonquanzj.aireplaymate.R
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugState
import com.lonquanzj.aireplaymate.ocr.OcrDebugState

/**
 * 失败面板 UI 构建器。
 *
 * 展示失败原因、恢复建议，以及可选的 OCR 诊断片段。
 */
internal fun buildFailurePanelView(
    context: Context,
    message: String,
    debugState: AccessibilityDebugState,
    ocrDebugState: OcrDebugState,
    onClose: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
): ScrollView {
    val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dpPx(12), context.dpPx(10), context.dpPx(12), context.dpPx(10))
        background = context.overlayPanelBackground()
        elevation = PANEL_ELEVATION
    }

    panel.addView(
        failurePanelHeaderView(context, onClose)
    )

    panel.addView(
        TextView(context).apply {
            text = message
            textSize = TEXT_SIZE_14
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_TEXT_PRIMARY)
            setPadding(0, context.dpPx(8), 0, 0)
        },
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )

    panel.addView(
        TextView(context).apply {
            text = buildFailureHint(context, message, debugState)
            textSize = TEXT_SIZE_12
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(0, context.dpPx(6), 0, 0)
        },
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )

    addOcrDiagnosticSection(context, panel, ocrDebugState)

    if (message.contains("无障碍")) {
        panel.addView(
            TextView(context).apply {
                text = context.getString(R.string.overlay_open_accessibility_settings)
                textSize = TEXT_SIZE_13
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(COLOR_TEXT_INVERSE)
                setPadding(context.dpPx(10), context.dpPx(8), context.dpPx(10), context.dpPx(8))
                background = context.selectedMenuButtonBackground()
                setOnClickListener { onOpenAccessibilitySettings() }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = context.dpPx(10)
            }
        )
    }

    return ScrollView(context).apply {
        addView(panel)
    }
}

// 失败面板头部：标题 + 收起动作。
private fun failurePanelHeaderView(
    context: Context,
    onClose: () -> Unit
): LinearLayout {
    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            TextView(context).apply {
                text = context.getString(R.string.overlay_failure_title)
                textSize = TEXT_SIZE_15
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(COLOR_TEXT_PRIMARY)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        addView(
            TextView(context).apply {
                text = context.getString(R.string.overlay_collapse)
                textSize = TEXT_SIZE_12
                gravity = Gravity.CENTER
                setTextColor(COLOR_TEXT_SECONDARY)
                setPadding(context.dpPx(8), context.dpPx(4), context.dpPx(8), context.dpPx(4))
                background = context.softPurpleCardBackground()
                setOnClickListener { onClose() }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }
}

private fun addOcrDiagnosticSection(
    context: Context,
    panel: LinearLayout,
    debugState: OcrDebugState
) {
    if (debugState.updatedAtMillis <= 0L) return
    val filterLines = debugState.filterSummaryPreviews.take(5)
    val messageLines = debugState.extractedMessagePreviews.takeLast(5)
    if (filterLines.isEmpty() && messageLines.isEmpty()) return

    panel.addView(
        TextView(context).apply {
            text = context.getString(R.string.overlay_ocr_filter_summary)
            textSize = TEXT_SIZE_12
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_TEXT_PRIMARY)
            setPadding(0, context.dpPx(10), 0, 0)
        }
    )
    panel.addView(
        TextView(context).apply {
            text = if (filterLines.isEmpty()) {
                context.getString(R.string.overlay_na)
            } else {
                filterLines.joinToString(separator = "\n") { "- $it" }
            }
            textSize = TEXT_SIZE_11
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(0, context.dpPx(4), 0, 0)
        }
    )

    panel.addView(
        TextView(context).apply {
            text = context.getString(R.string.overlay_ocr_messages)
            textSize = TEXT_SIZE_12
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_TEXT_PRIMARY)
            setPadding(0, context.dpPx(10), 0, 0)
        }
    )
    panel.addView(
        TextView(context).apply {
            text = if (messageLines.isEmpty()) {
                context.getString(R.string.overlay_na)
            } else {
                messageLines.joinToString(separator = "\n") { "- $it" }
            }
            textSize = TEXT_SIZE_11
            setTextColor(COLOR_TEXT_SECONDARY)
            setPadding(0, context.dpPx(4), 0, 0)
        }
    )
}

// 根据错误类型生成更具可执行性的提示文案。
private fun buildFailureHint(
    context: Context,
    message: String,
    debugState: AccessibilityDebugState
): String {
    return when {
        message.contains("无障碍") -> {
            context.getString(R.string.overlay_failure_hint_accessibility)
        }

        message.contains("微信") -> {
            context.getString(
                R.string.overlay_failure_hint_wechat,
                debugState.packageName.ifBlank { context.getString(R.string.overlay_unknown) }
            )
        }

        message.contains("单聊") -> {
            val reason = debugState.chatDetectionReason.ifBlank {
                context.getString(R.string.overlay_failure_reason_insufficient_feature)
            }
            context.getString(R.string.overlay_failure_hint_chat, reason)
        }

        else -> {
            context.getString(R.string.overlay_failure_hint_generic)
        }
    }
}
