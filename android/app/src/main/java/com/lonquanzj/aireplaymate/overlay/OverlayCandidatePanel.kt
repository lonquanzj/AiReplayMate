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

/**
 * 候选回复面板 UI 构建器。
 *
 * 只负责 View 结构和样式，不负责业务动作（由回调注入）。
 */
internal fun buildCandidatePanelView(
    context: Context,
    title: String,
    subtitle: String,
    modeLabel: String,
    candidates: List<OverlayCandidate>,
    onRegenerate: () -> Unit,
    onClose: () -> Unit,
    onCandidateClick: (OverlayCandidate) -> Unit
): LinearLayout {
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            context.dpPx(CANDIDATE_PANEL_PADDING_H),
            context.dpPx(CANDIDATE_PANEL_PADDING_TOP),
            context.dpPx(CANDIDATE_PANEL_PADDING_H),
            context.dpPx(CANDIDATE_PANEL_PADDING_BOTTOM)
        )
        background = context.candidatePanelBackground()
        elevation = PANEL_ELEVATION

        addView(
            candidatePanelHeaderView(
                context = context,
                title = title,
                modeLabel = modeLabel,
                subtitle = subtitle,
                onAction = onRegenerate,
                onClose = onClose
            ),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        addCandidateStack(
            context = context,
            parent = this,
            items = candidates,
            topMarginDp = CANDIDATE_STACK_TOP_MARGIN,
            onCandidateClick = onCandidateClick
        )
    }
}

// 头部包含标题、副标题以及刷新/关闭两个动作按钮。
private fun candidatePanelHeaderView(
    context: Context,
    title: String,
    modeLabel: String,
    subtitle: String,
    onAction: () -> Unit,
    onClose: () -> Unit
): LinearLayout {
    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(
                            TextView(context).apply {
                                text = title
                                textSize = TEXT_SIZE_14
                                typeface = Typeface.DEFAULT_BOLD
                                setTextColor(COLOR_TEXT_PRIMARY)
                                maxLines = 1
                                ellipsize = TextUtils.TruncateAt.END
                            },
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        )
                        if (modeLabel.isNotBlank()) {
                            addView(
                                TextView(context).apply {
                                    text = modeLabel
                                    textSize = TEXT_SIZE_11
                                    setTextColor(COLOR_TEXT_SECONDARY)
                                    maxLines = 1
                                    ellipsize = TextUtils.TruncateAt.END
                                },
                                LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    marginStart = context.dpPx(CANDIDATE_MODE_LABEL_MARGIN_START)
                                }
                            )
                        }
                    }
                )
                addView(
                    TextView(context).apply {
                        text = subtitle
                        textSize = TEXT_SIZE_11
                        setTextColor(COLOR_TEXT_SECONDARY)
                        setPadding(0, context.dpPx(CANDIDATE_SUBTITLE_TOP_PADDING), 0, 0)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }
                )
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        addView(
            TextView(context).apply {
                text = context.getString(R.string.overlay_icon_refresh)
                textSize = TEXT_SIZE_14
                gravity = Gravity.CENTER
                setTextColor(COLOR_TEXT_ACCENT)
                minWidth = context.dpPx(CANDIDATE_ACTION_ICON_MIN_WIDTH)
                minHeight = context.dpPx(CANDIDATE_ACTION_ICON_MIN_HEIGHT)
                background = context.candidatePanelIconButtonBackground()
                applyPressedFeedback(this)
                setOnClickListener { onAction() }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = context.dpPx(CANDIDATE_ACTION_ICON_MARGIN_START)
            }
        )
        addView(
            TextView(context).apply {
                text = context.getString(R.string.overlay_icon_close)
                textSize = TEXT_SIZE_16
                gravity = Gravity.CENTER
                setTextColor(COLOR_TEXT_ACCENT)
                minWidth = context.dpPx(CANDIDATE_ACTION_ICON_MIN_WIDTH)
                minHeight = context.dpPx(CANDIDATE_ACTION_ICON_MIN_HEIGHT)
                background = context.candidatePanelIconButtonBackground()
                applyPressedFeedback(this)
                setOnClickListener { onClose() }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = context.dpPx(CANDIDATE_CLOSE_ICON_MARGIN_START)
            }
        )
    }
}

private fun addCandidateStack(
    context: Context,
    parent: LinearLayout,
    items: List<OverlayCandidate>,
    topMarginDp: Int,
    onCandidateClick: (OverlayCandidate) -> Unit
) {
    if (items.isEmpty()) return
    items.forEachIndexed { index, candidate ->
        parent.addView(
            candidateItemView(context, candidate, onCandidateClick),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (index == 0) context.dpPx(topMarginDp) else context.dpPx(CANDIDATE_STACK_ITEM_GAP)
            }
        )
    }
}

// 单条候选卡片，点击行为透传给上层。
private fun candidateItemView(
    context: Context,
    candidate: OverlayCandidate,
    onCandidateClick: (OverlayCandidate) -> Unit
): View {
    return TextView(context).apply {
        text = candidate.text
        textSize = TEXT_SIZE_14
        setTextColor(COLOR_TEXT_PRIMARY)
        minLines = 2
        setPadding(
            context.dpPx(CANDIDATE_ITEM_PADDING),
            context.dpPx(CANDIDATE_ITEM_PADDING),
            context.dpPx(CANDIDATE_ITEM_PADDING),
            context.dpPx(CANDIDATE_ITEM_PADDING)
        )
        background = context.candidateReplyBackground()
        applyPressedFeedback(this)
        setOnClickListener { onCandidateClick(candidate) }
    }
}

