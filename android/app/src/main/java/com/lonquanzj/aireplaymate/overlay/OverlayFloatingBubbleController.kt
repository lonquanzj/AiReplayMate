package com.lonquanzj.aireplaymate.overlay

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import com.lonquanzj.aireplaymate.R
import kotlin.math.abs

/**
 * 管理悬浮气泡交互：拖拽、长按、吸边与 idle 眨眼动画。
 */
internal class OverlayFloatingBubbleController(
    private val context: Context,
    private val mainHandler: Handler,
    private val windowManagerProvider: () -> WindowManager?,
    private val onLongPress: () -> Unit,
    private val isGeneratingCandidates: () -> Boolean
) {
    // 由 ViewFactory 绑定进来，Controller 只负责状态切换与动画。
    var floatingAvatarView: ImageView? = null

    var isDocked: Boolean = false
        private set

    private var dockedSide: DockedSide? = null
    private var dockAnimator: ValueAnimator? = null
    private var floatingIdleAnimator: AnimatorSet? = null
    private var idleBlinkRunnable: Runnable? = null

    fun attachDragHandler(
        view: View,
        params: WindowManager.LayoutParams
    ) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        var longPressTriggered = false
        var longPressRunnable: Runnable? = null

        view.setOnTouchListener { target, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dockAnimator?.cancel()
                    undockFloatingAvatar()
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    longPressTriggered = false
                    longPressRunnable = Runnable {
                        if (!moved) {
                            longPressTriggered = true
                            target.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            onLongPress()
                        }
                    }.also { target.postDelayed(it, LONG_PRESS_TIMEOUT_MS) }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > DRAG_SLOP || abs(dy) > DRAG_SLOP) {
                        moved = true
                        longPressRunnable?.let(target::removeCallbacks)
                        stopFloatingIdleAnimation()
                    }
                    params.x = startX + dx
                    params.y = startY + dy
                    windowManagerProvider()?.updateViewLayout(target, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    longPressRunnable?.let(target::removeCallbacks)
                    if (!moved && !longPressTriggered) {
                        target.performClick()
                    } else if (moved) {
                        dockFloatingButton(target, params)
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let(target::removeCallbacks)
                    true
                }

                else -> false
            }
        }
    }

    fun startFloatingIdleAnimation() {
        if (isGeneratingCandidates() || isDocked || idleBlinkRunnable != null) return
        val avatar = floatingAvatarView ?: return
        val delay = (IDLE_BLINK_MIN_DELAY_MS..IDLE_BLINK_MAX_DELAY_MS).random()
        idleBlinkRunnable = Runnable {
            idleBlinkRunnable = null
            if (!isGeneratingCandidates() && !isDocked && avatar.parent != null) {
                avatar.setImageResource(R.drawable.floating_avatar_wink_right)
                floatingIdleAnimator = AnimatorSet().apply {
                    val blink = ObjectAnimator.ofFloat(avatar, View.SCALE_Y, 1f, 0.96f, 1f).apply {
                        duration = IDLE_BLINK_DURATION_MS
                    }
                    val breatheX = ObjectAnimator.ofFloat(avatar, View.SCALE_X, 1f, 1.025f, 1f).apply {
                        duration = IDLE_BLINK_DURATION_MS
                    }
                    playTogether(blink, breatheX)
                    addListener(
                        object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                avatar.setImageResource(R.drawable.floating_avatar_idle)
                                floatingIdleAnimator = null
                                startFloatingIdleAnimation()
                            }

                            override fun onAnimationCancel(animation: Animator) {
                                avatar.setImageResource(R.drawable.floating_avatar_idle)
                                floatingIdleAnimator = null
                            }
                        }
                    )
                    start()
                }
            }
        }.also { mainHandler.postDelayed(it, delay) }
    }

    fun stopFloatingIdleAnimation() {
        idleBlinkRunnable?.let(mainHandler::removeCallbacks)
        idleBlinkRunnable = null
        floatingIdleAnimator?.cancel()
        floatingIdleAnimator = null
        if (!isDocked) {
            floatingAvatarView?.setImageResource(R.drawable.floating_avatar_idle)
        }
        floatingAvatarView?.scaleX = 1f
        floatingAvatarView?.scaleY = 1f
    }

    fun cancelDockAndIdle() {
        dockAnimator?.cancel()
        dockAnimator = null
        stopFloatingIdleAnimation()
    }

    private fun dockFloatingButton(
        view: View,
        params: WindowManager.LayoutParams
    ) {
        if (isGeneratingCandidates()) return

        // 拖拽结束后吸附到左右边缘，只保留一部分可见宽度。
        stopFloatingIdleAnimation()
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val visibleWidth = context.dpPx(DOCKED_VISIBLE_WIDTH_DP)
        val verticalMargin = context.dpPx(12)
        val centerX = params.x + params.width / 2
        val side = if (centerX < screenWidth / 2) DockedSide.LEFT else DockedSide.RIGHT
        val targetX = when (side) {
            DockedSide.LEFT -> -(params.width - visibleWidth)
            DockedSide.RIGHT -> screenWidth - visibleWidth
        }
        val targetY = params.y.coerceIn(
            verticalMargin,
            (screenHeight - params.height - verticalMargin).coerceAtLeast(verticalMargin)
        )

        dockAnimator?.cancel()
        dockAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DOCK_ANIMATION_DURATION_MS
            interpolator = DecelerateInterpolator()
            val startX = params.x
            val startY = params.y
            var cancelled = false
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                params.x = (startX + (targetX - startX) * fraction).toInt()
                params.y = (startY + (targetY - startY) * fraction).toInt()
                windowManagerProvider()?.updateViewLayout(view, params)
            }
            addListener(
                object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (cancelled) return
                        isDocked = true
                        dockedSide = side
                        floatingAvatarView?.setImageResource(
                            when (side) {
                                DockedSide.LEFT -> R.drawable.floating_avatar_peek_left
                                DockedSide.RIGHT -> R.drawable.floating_avatar_wink_right
                            }
                        )
                        dockAnimator = null
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                        dockAnimator = null
                    }
                }
            )
            start()
        }
    }

    private fun undockFloatingAvatar() {
        if (!isDocked && dockedSide == null) return
        isDocked = false
        dockedSide = null
        floatingAvatarView?.setImageResource(R.drawable.floating_avatar_idle)
        if (!isGeneratingCandidates()) {
            startFloatingIdleAnimation()
        }
    }
}
