package com.lonquanzj.aireplaymate.overlay

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.lonquanzj.aireplaymate.R
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile

internal fun styleMenuHintRowView(
    context: Context,
    tab: StyleMenuTab,
    current: ReplyStyleProfile,
    onClose: () -> Unit
): LinearLayout {
    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    TextView(context).apply {
                        tag = STYLE_MENU_HINT_TAG
                        text = styleMenuSelectionHint(tab)
                        textSize = TEXT_SIZE_10_5
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setTextColor(COLOR_TEXT_MUTED)
                    }
                )
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        addView(
            TextView(context).apply {
                text = context.getString(R.string.overlay_icon_close)
                textSize = TEXT_SIZE_15
                gravity = Gravity.CENTER
                setTextColor(COLOR_TEXT_MUTED)
                setPadding(context.dpPx(STYLE_HINT_CLOSE_PADDING_H), 0, context.dpPx(STYLE_HINT_CLOSE_PADDING_H), 0)
                minWidth = context.dpPx(STYLE_HINT_CLOSE_MIN_WIDTH)
                minHeight = context.dpPx(STYLE_HINT_CLOSE_MIN_HEIGHT)
                applyPressedFeedback(this)
                setOnClickListener { onClose() }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }
}

internal fun updateStyleMenuHintRow(
    row: LinearLayout,
    tab: StyleMenuTab,
    @Suppress("UNUSED_PARAMETER") current: ReplyStyleProfile
) {
    val hintView = row.findViewWithTag<TextView>(STYLE_MENU_HINT_TAG)
    hintView?.text = styleMenuSelectionHint(tab)
}

private fun styleMenuSelectionHint(
    tab: StyleMenuTab
): String {
    return tab.hint
}

