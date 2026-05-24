package com.lonquanzj.aireplaymate.overlay

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
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
    private var autoDockRunnable: Runnable? = null
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
                    autoDockRunnable?.let(mainHandler::removeCallbacks)
                    autoDockRunnable = null

                    // 只要按下，立即恢复不透明度和位置，确保交互灵敏
                    val screenWidth = context.resources.displayMetrics.widthPixels
                    val overSnap = context.dpPx(4)
                    val isFaded = target.alpha < 0.9f
                    val isHidden = params.x < -overSnap || params.x > screenWidth - params.width + overSnap

                    if (isDocked || isFaded || isHidden) {
                        params.x = if (params.x + params.width / 2 < screenWidth / 2) {
                            -overSnap
                        } else {
                            screenWidth - params.width + overSnap
                        }
                        target.alpha = 0.94f
                        windowManagerProvider()?.updateViewLayout(target, params)
                    }

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
                        // 点击唤回后，也需要开启闲置贴边倒计时
                        dockFloatingButton(target, params, isPushed = false)
                    } else if (moved) {
                        dockFloatingButton(target, params, isPushed = true)
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

    fun dockFloatingButton(
        view: View,
        params: WindowManager.LayoutParams,
        isPushed: Boolean = false
    ) {
        if (isGeneratingCandidates()) return
        autoDockRunnable?.let(mainHandler::removeCallbacks)
        autoDockRunnable = null
        stopFloatingIdleAnimation()

        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels
        val verticalMargin = context.dpPx(12)
        val centerX = params.x + params.width / 2
        val side = if (centerX < screenWidth / 2) DockedSide.LEFT else DockedSide.RIGHT

        val targetY = params.y.coerceIn(
            verticalMargin,
            (screenHeight - params.height - verticalMargin).coerceAtLeast(verticalMargin)
        )

        // 检测是否“主动推到边缘”：只有在拖拽(isPushed=true)且位置靠近边缘时才成立
        val pushedToEdge = isPushed && (when (side) {
            DockedSide.LEFT -> params.x <= context.dpPx(8)
            DockedSide.RIGHT -> params.x >= screenWidth - params.width - context.dpPx(8)
        })

        if (pushedToEdge) {
            // 直接执行贴边（半隐藏 + 变淡）
            performFinalDock(view, params, side, targetY)
        } else {
            // 先吸附到边缘（全显），吸附动画结束后开启 2.5s 计时
            // 使用 OvershootInterpolator 产生回弹感
            val overSnap = context.dpPx(4)
            val snapX = when (side) {
                DockedSide.LEFT -> -overSnap
                DockedSide.RIGHT -> screenWidth - params.width + overSnap
            }
            animateToInternal(
                view, params, snapX, targetY,
                targetAlpha = 0.94f,
                interpolator = OvershootInterpolator(1.2f)
            ) {
                autoDockRunnable = Runnable {
                    performFinalDock(view, params, side, targetY)
                }.also { mainHandler.postDelayed(it, AUTO_DOCK_DELAY_MS) }
            }
        }
    }

    private fun performFinalDock(
        view: View,
        params: WindowManager.LayoutParams,
        side: DockedSide,
        targetY: Int
    ) {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val visibleWidth = context.dpPx(DOCKED_VISIBLE_WIDTH_DP)
        val hiddenX = when (side) {
            DockedSide.LEFT -> -(params.width - visibleWidth)
            DockedSide.RIGHT -> screenWidth - visibleWidth
        }
        // 贴边时不仅移动位置，还将透明度降至 0.4
        animateToInternal(view, params, hiddenX, targetY, targetAlpha = 0.4f) {
            isDocked = true
            dockedSide = side
            floatingAvatarView?.setImageResource(
                when (side) {
                    DockedSide.LEFT -> R.drawable.floating_avatar_peek_left
                    DockedSide.RIGHT -> R.drawable.floating_avatar_wink_right
                }
            )
        }
    }

    private fun animateToInternal(
        view: View,
        params: WindowManager.LayoutParams,
        targetX: Int,
        targetY: Int,
        targetAlpha: Float? = null,
        interpolator: TimeInterpolator = DecelerateInterpolator(),
        onEnd: () -> Unit
    ) {
        dockAnimator?.cancel()
        val startAlpha = view.alpha
        dockAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DOCK_ANIMATION_DURATION_MS
            this.interpolator = interpolator
            val startX = params.x
            val startY = params.y
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                params.x = (startX + (targetX - startX) * fraction).toInt()
                params.y = (startY + (targetY - startY) * fraction).toInt()
                if (targetAlpha != null) {
                    view.alpha = startAlpha + (targetAlpha - startAlpha) * fraction
                }
                windowManagerProvider()?.updateViewLayout(view, params)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd()
                    dockAnimator = null
                }
            })
            start()
        }
    }

    private fun undockFloatingAvatar() {
        if (!isDocked && dockedSide == null) return
        isDocked = false
        dockedSide = null
        floatingAvatarView?.setImageResource(R.drawable.floating_avatar_idle)
        floatingAvatarView?.alpha = 1.0f
        if (!isGeneratingCandidates()) {
            startFloatingIdleAnimation()
        }
    }
}
