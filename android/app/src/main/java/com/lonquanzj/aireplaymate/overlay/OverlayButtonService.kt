package com.lonquanzj.aireplaymate.overlay

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.lonquanzj.aireplaymate.accessibility.AccessibilityActionBridge
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugStore
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugState
import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.context.ConversationType
import com.lonquanzj.aireplaymate.context.DefaultContextBuilder

class OverlayButtonService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var candidatePanelView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingButton()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (overlayView == null && Settings.canDrawOverlays(this)) {
            showFloatingButton()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeCandidatePanel()
        overlayView?.let { view ->
            windowManager?.removeView(view)
        }
        overlayView = null
        windowManager = null
        super.onDestroy()
    }

    private fun showFloatingButton() {
        if (overlayView != null) return

        val button = TextView(this).apply {
            text = "AI"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xDD12332F.toInt())
            elevation = 12f
            setOnClickListener {
                showCandidatePanel(AccessibilityDebugStore.state.value)
            }
        }

        val params = WindowManager.LayoutParams(
            132,
            132,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 320
        }

        layoutParams = params
        overlayView = button
        attachDragHandler(button, params)
        windowManager?.addView(button, params)
    }

    private fun attachDragHandler(
        view: View,
        params: WindowManager.LayoutParams
    ) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        view.setOnTouchListener { target, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > DRAG_SLOP || kotlin.math.abs(dy) > DRAG_SLOP) {
                        moved = true
                    }
                    params.x = startX + dx
                    params.y = startY + dy
                    windowManager?.updateViewLayout(target, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        target.performClick()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun showCandidatePanel(debugState: AccessibilityDebugState) {
        val blocker = validateTarget(debugState)
        if (blocker != null) {
            Toast.makeText(this, blocker, Toast.LENGTH_SHORT).show()
            return
        }

        val context = DefaultContextBuilder.build(
            accessibilityMessages = debugState.extractedMessages,
            targetApp = WECHAT_TARGET_APP,
            conversationType = ConversationType.SINGLE_CHAT
        )
        if (!context.enoughForReply) {
            Toast.makeText(this, "上下文不足，先在微信单聊页停留一秒再试", Toast.LENGTH_SHORT).show()
            return
        }

        val candidates = generateCandidates(context.messages.lastOrNull { it.role == ChatRole.FRIEND }?.content)
        removeCandidatePanel()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(0xF7FFFFFF.toInt(), dp(18).toFloat())
            elevation = 18f
        }

        panel.addView(
            TextView(this).apply {
                text = "候选回复"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(18, 51, 47))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        candidates.forEach { candidate ->
            panel.addView(candidateView(candidate))
        }

        panel.addView(
            TextView(this).apply {
                text = "关闭"
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(Color.rgb(91, 101, 98))
                setPadding(0, dp(10), 0, 0)
                setOnClickListener { removeCandidatePanel() }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val params = WindowManager.LayoutParams(
            dp(330),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(190)
        }

        candidatePanelView = panel
        windowManager?.addView(panel, params)
    }

    private fun candidateView(candidate: OverlayCandidate): View {
        return TextView(this).apply {
            text = candidate.text
            textSize = 15f
            setTextColor(Color.rgb(30, 27, 22))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundedBackground(0xFFF8F2E8.toInt(), dp(14).toFloat())
            setOnClickListener {
                val result = AccessibilityActionBridge.tryAutofill(candidate.text)
                Toast.makeText(this@OverlayButtonService, result.message, Toast.LENGTH_SHORT).show()
                if (result.success) {
                    removeCandidatePanel()
                }
            }
        }.also { view ->
            val marginTop = dp(10)
            view.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = marginTop
            }
        }
    }

    private fun removeCandidatePanel() {
        candidatePanelView?.let { view ->
            windowManager?.removeView(view)
        }
        candidatePanelView = null
    }

    private fun validateTarget(debugState: AccessibilityDebugState): String? {
        if (!debugState.serviceConnected) {
            return "无障碍服务未连接"
        }
        if (!debugState.isWechatPackage) {
            return "当前不在微信页面"
        }
        if (!debugState.looksLikeChatPage) {
            return "当前不像微信单聊页"
        }
        if (!debugState.inputNodeFound) {
            return "未找到微信输入框"
        }
        return null
    }

    private fun generateCandidates(lastFriendMessage: String?): List<OverlayCandidate> {
        val message = lastFriendMessage.orEmpty()
        val texts = when {
            message.contains("吗") || message.contains("?") || message.contains("？") -> listOf(
                "可以的，我这边确认后马上回复你。",
                "我先看一下，稍后给你明确答复。",
                "没问题，我处理完第一时间告诉你。"
            )

            message.contains("时间") || message.contains("几点") -> listOf(
                "我这边晚点确认时间后同步你。",
                "可以，我先看下安排，稍后告诉你具体时间。",
                "收到，我确认好时间马上回复。"
            )

            else -> listOf(
                "收到，我这边先确认一下，稍后回复你。",
                "好的，我看完后马上同步给你。",
                "没问题，我这边处理完第一时间说。"
            )
        }

        return texts.mapIndexed { index, text ->
            OverlayCandidate(id = "local_$index", text = text)
        }
    }

    private fun roundedBackground(
        color: Int,
        radius: Float
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private companion object {
        const val DRAG_SLOP = 8
        const val WECHAT_TARGET_APP = "wechat"
    }

    private data class OverlayCandidate(
        val id: String,
        val text: String
    )
}
