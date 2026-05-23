package com.lonquanzj.aireplaymate.overlay

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.view.View
import android.widget.LinearLayout

/**
 * 管理 Overlay 面板与悬浮球的可复用动画生命周期。
 */
internal class OverlayPanelAnimationController(
    private val context: Context
) {
    private var floatingButtonAnimator: AnimatorSet? = null
    private val progressIndicatorAnimators = mutableListOf<Animator>()

    fun startFloatingButtonAnimation(button: View) {
        if (floatingButtonAnimator != null) return
        val scaleX = ObjectAnimator.ofFloat(button, View.SCALE_X, 1f, 1.08f, 1f).apply {
            duration = 1250L
            repeatCount = ObjectAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(button, View.SCALE_Y, 1f, 1.08f, 1f).apply {
            duration = 1250L
            repeatCount = ObjectAnimator.INFINITE
        }
        val alpha = ObjectAnimator.ofFloat(button, View.ALPHA, 0.94f, 0.84f, 0.94f).apply {
            duration = 1250L
            repeatCount = ObjectAnimator.INFINITE
        }
        val lift = ObjectAnimator.ofFloat(button, View.TRANSLATION_Z, 0f, context.dpPx(4).toFloat(), 0f).apply {
            duration = 1250L
            repeatCount = ObjectAnimator.INFINITE
        }
        floatingButtonAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha, lift)
            start()
        }
    }

    fun stopFloatingButtonAnimation() {
        floatingButtonAnimator?.cancel()
        floatingButtonAnimator = null
    }

    // 三点指示器动画：按 index 错峰启动，形成流动感。
    fun startProgressIndicatorAnimation(indicator: LinearLayout) {
        if (progressIndicatorAnimators.isNotEmpty()) return
        repeat(indicator.childCount) { index ->
            val dot = indicator.getChildAt(index)
            val scaleX = ObjectAnimator.ofFloat(dot, View.SCALE_X, 0.8f, 1.28f, 0.8f).apply {
                duration = 900L
                startDelay = index * 130L
                repeatCount = ObjectAnimator.INFINITE
            }
            val scaleY = ObjectAnimator.ofFloat(dot, View.SCALE_Y, 0.8f, 1.28f, 0.8f).apply {
                duration = 900L
                startDelay = index * 130L
                repeatCount = ObjectAnimator.INFINITE
            }
            val alpha = ObjectAnimator.ofFloat(dot, View.ALPHA, 0.35f, 1f, 0.35f).apply {
                duration = 900L
                startDelay = index * 130L
                repeatCount = ObjectAnimator.INFINITE
            }
            progressIndicatorAnimators += listOf(scaleX, scaleY, alpha)
        }
        progressIndicatorAnimators.forEach { it.start() }
    }

    fun stopProgressIndicatorAnimation() {
        progressIndicatorAnimators.forEach { it.cancel() }
        progressIndicatorAnimators.clear()
    }
}
