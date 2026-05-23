package com.lonquanzj.aireplaymate.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.lonquanzj.aireplaymate.R

/**
 * 悬浮按钮构建结果：按钮 view + 对应 window layout 参数。
 */
internal data class OverlayFloatingButtonBundle(
    val button: FrameLayout,
    val layoutParams: WindowManager.LayoutParams
)

/**
 * 构建悬浮按钮及默认布局。
 */
internal fun buildFloatingButtonBundle(
    context: Context,
    onClick: () -> Unit,
    onAvatarBound: (ImageView) -> Unit
): OverlayFloatingButtonBundle {
    val button = FrameLayout(context).apply {
        background = floatingButtonBackgroundDrawable()
        alpha = 0.94f
        elevation = 12f
        setOnClickListener { onClick() }
    }
    button.addView(createFloatingButtonIcon(context, onAvatarBound))

    val params = WindowManager.LayoutParams(
        context.dpPx(FLOATING_BUTTON_SIZE_DP),
        context.dpPx(FLOATING_BUTTON_SIZE_DP),
        overlayWindowType(),
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = context.dpPx(16)
        y = context.dpPx(220)
    }

    return OverlayFloatingButtonBundle(
        button = button,
        layoutParams = params
    )
}

internal fun floatingButtonBackgroundDrawable(): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.TRANSPARENT)
    }
}

// 头像层作为按钮内容，具体资源切换由 controller 负责。
private fun createFloatingButtonIcon(
    context: Context,
    onAvatarBound: (ImageView) -> Unit
): View {
    return FrameLayout(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setPadding(context.dpPx(3), context.dpPx(3), context.dpPx(3), context.dpPx(3))
        addView(
            ImageView(context).apply {
                setImageResource(R.drawable.floating_avatar_idle)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                onAvatarBound(this)
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )
    }
}
