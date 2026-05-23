package com.lonquanzj.aireplaymate.overlay

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.lonquanzj.aireplaymate.R

internal fun styleMenuTabButtonView(
    context: Context,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
): TextView {
    return TextView(context).apply {
        text = label
        textSize = TEXT_SIZE_12
        setTextColor(if (isSelected) COLOR_TEXT_ACCENT else COLOR_TEXT_PRIMARY)
        typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        minHeight = context.dpPx(STYLE_TAB_MIN_HEIGHT)
        setPadding(
            context.dpPx(STYLE_TAB_PADDING_H),
            context.dpPx(STYLE_TAB_PADDING_V),
            context.dpPx(STYLE_TAB_PADDING_H),
            context.dpPx(STYLE_TAB_PADDING_V)
        )
        elevation = if (isSelected) context.dpPx(1).toFloat() else 0f
        background = if (isSelected) {
            context.styleMenuTabSelectedBackground()
        } else {
            context.styleMenuTabIdleBackground()
        }
        applyPressedFeedback(this)
        setOnClickListener { onClick() }
    }
}

internal fun styleMenuGroupButtonView(
    context: Context,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
): TextView {
    return TextView(context).apply {
        text = label
        textSize = TEXT_SIZE_10
        setTextColor(if (isSelected) COLOR_TEXT_ACCENT else COLOR_TEXT_GROUP_IDLE)
        typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        minHeight = context.dpPx(STYLE_GROUP_MIN_HEIGHT)
        setPadding(
            context.dpPx(STYLE_GROUP_PADDING_H),
            context.dpPx(STYLE_GROUP_PADDING_TOP),
            context.dpPx(STYLE_GROUP_PADDING_H),
            context.dpPx(STYLE_GROUP_PADDING_BOTTOM)
        )
        background = if (isSelected) {
            context.selectedStyleMenuGroupBackground()
        } else {
            context.styleMenuGroupBackground()
        }
        applyPressedFeedback(this)
        setOnClickListener { onClick() }
    }
}

internal fun styleMenuItemButton(
    context: Context,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
): TextView {
    return TextView(context).apply {
        text = if (isSelected) {
            context.getString(R.string.overlay_selected_item, label)
        } else {
            label
        }
        textSize = TEXT_SIZE_10_5
        setTextColor(if (isSelected) COLOR_TEXT_ACCENT else COLOR_TEXT_PRIMARY)
        typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        minHeight = context.dpPx(STYLE_ITEM_MIN_HEIGHT)
        setPadding(
            context.dpPx(STYLE_ITEM_PADDING_H),
            context.dpPx(STYLE_ITEM_PADDING_V),
            context.dpPx(STYLE_ITEM_PADDING_H),
            context.dpPx(STYLE_ITEM_PADDING_V)
        )
        background = if (isSelected) {
            context.compactSelectedMenuButtonBackground()
        } else {
            context.compactMenuButtonBackground()
        }
        applyPressedFeedback(this)
        setOnClickListener { onClick() }
    }
}

internal fun <T> addCompactGrid(
    context: Context,
    parent: LinearLayout,
    items: List<T>,
    columns: Int,
    topMarginDp: Int,
    gapDp: Int = 8,
    viewFactory: (T) -> View
) {
    if (items.isEmpty()) return
    val horizontalGap = context.dpPx(gapDp)
    val verticalGap = context.dpPx(gapDp)
    items.chunked(columns).forEachIndexed { rowIndex, rowItems ->
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        rowItems.forEachIndexed { itemIndex, item ->
            row.addView(
                viewFactory(item),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (itemIndex > 0) {
                        marginStart = horizontalGap
                    }
                }
            )
        }
        repeat(columns - rowItems.size) {
            row.addView(
                View(context),
                LinearLayout.LayoutParams(0, 0, 1f).apply {
                    if (row.childCount > 0) {
                        marginStart = horizontalGap
                    }
                }
            )
        }
        parent.addView(
            row,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (rowIndex == 0) context.dpPx(topMarginDp) else verticalGap
            }
        )
    }
}

