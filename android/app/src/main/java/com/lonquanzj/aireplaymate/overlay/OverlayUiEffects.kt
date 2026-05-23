package com.lonquanzj.aireplaymate.overlay

import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * 面板入场过渡：淡入 + 轻微上浮回弹。
 */
internal fun animatePanelIn(view: View, liftPx: Float) {
    view.alpha = 0f
    view.scaleX = 0.96f
    view.scaleY = 0.96f
    view.translationY = liftPx
    view.animate()
        .alpha(1f)
        .scaleX(1f)
        .scaleY(1f)
        .translationY(0f)
        .setDuration(190L)
        .setInterpolator(DecelerateInterpolator())
        .start()
}

/**
 * 统一按压反馈。
 *
 * 注意：该方法会设置 OnTouchListener，调用方不应再覆盖 touch listener。
 */
internal fun applyPressedFeedback(view: View) {
    view.setOnTouchListener { target, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                target.animate()
                    .scaleX(0.98f)
                    .scaleY(0.98f)
                    .alpha(0.86f)
                    .setDuration(70L)
                    .start()
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                target.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(110L)
                    .start()
            }
        }
        false
    }
}
