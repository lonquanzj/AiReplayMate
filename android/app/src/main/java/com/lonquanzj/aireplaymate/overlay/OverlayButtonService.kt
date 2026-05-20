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
import com.lonquanzj.aireplaymate.prompt.ReplyCandidate
import com.lonquanzj.aireplaymate.session.RealReplySessionPhase
import com.lonquanzj.aireplaymate.session.RealReplySessionRunner
import com.lonquanzj.aireplaymate.settings.AppSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OverlayButtonService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var candidatePanelView: View? = null
    private var progressStatusView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isGeneratingCandidates = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            OverlayServiceStateStore.onMissingPermission()
            Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingButton()
        OverlayServiceStateStore.onRunning(bubbleVisible = overlayView != null)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (!Settings.canDrawOverlays(this)) {
            OverlayServiceStateStore.onMissingPermission()
            stopSelf()
            return START_NOT_STICKY
        }
        if (overlayView == null) {
            showFloatingButton()
        }
        OverlayServiceStateStore.onRunning(bubbleVisible = overlayView != null)
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
        OverlayServiceStateStore.onStopped()
        serviceScope.cancel()
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
                if (isGeneratingCandidates) {
                    Toast.makeText(this@OverlayButtonService, "正在生成候选回复，请稍等", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                serviceScope.launch {
                    isGeneratingCandidates = true
                    updateFloatingButtonLoading(true)
                    try {
                        showCandidatePanel(AccessibilityDebugStore.state.value)
                    } finally {
                        isGeneratingCandidates = false
                        updateFloatingButtonLoading(false)
                    }
                }
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

    private suspend fun showCandidatePanel(debugState: AccessibilityDebugState) {
        OverlayDiagnosticsStore.begin()
        val settings = AppSettingsStore.load(this)
        val result = RealReplySessionRunner(applicationContext).run(
            debugState = debugState,
            settings = settings,
            onPhase = { phase, status ->
                OverlayDiagnosticsStore.onPhase(
                    phase = phase.toOverlayPhase(),
                    status = status
                )
                showProgressPanel(
                    when (phase) {
                        RealReplySessionPhase.BUILDING_CONTEXT -> "正在整理聊天上下文..."
                        RealReplySessionPhase.OCR_FALLBACK -> "正在用 OCR 兜底识别消息..."
                        RealReplySessionPhase.REQUESTING_LLM -> "正在生成候选回复..."
                        RealReplySessionPhase.LOCAL_FALLBACK -> "正在准备本地兜底候选..."
                        RealReplySessionPhase.VALIDATING -> "正在校验微信页面..."
                    }
                )
            },
            onContext = { snapshot ->
                OverlayDiagnosticsStore.onContext(
                    accessibilityMessageCount = snapshot.accessibilityMessageCount,
                    ocrMessageCount = snapshot.ocrMessageCount,
                    mergedMessageCount = snapshot.mergedMessageCount,
                    usedOcr = snapshot.usedOcr
                )
            }
        )

        val sessionResult = result.getOrElse { error ->
            val message = error.message ?: "生成候选失败"
            OverlayDiagnosticsStore.onFailed(message)
            removeCandidatePanel()
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            return
        }
        if (sessionResult.usedLocalFallback) {
            Toast.makeText(
                this,
                "LLM 不可用，已使用本地兜底：${sessionResult.localFallbackReason ?: "未知错误"}",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(this, "候选回复已生成", Toast.LENGTH_SHORT).show()
        }
        val candidates = sessionResult.candidates.toOverlayCandidates()
        OverlayDiagnosticsStore.onCandidates(
            count = candidates.size,
            usedLocalFallback = sessionResult.usedLocalFallback,
            candidateSource = sessionResult.candidateSource
        )
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

        panel.addView(
            TextView(this).apply {
                text = "来源：${sessionResult.candidateSource}"
                textSize = 12f
                setTextColor(Color.rgb(91, 101, 98))
                setPadding(0, dp(4), 0, 0)
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
                setOnClickListener {
                    OverlayDiagnosticsStore.onDone("用户关闭候选面板")
                    removeCandidatePanel()
                }
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
                OverlayDiagnosticsStore.onAutofill(result.message)
                Toast.makeText(this@OverlayButtonService, result.message, Toast.LENGTH_SHORT).show()
                if (result.success) {
                    OverlayDiagnosticsStore.onDone("候选已填入输入框，等待用户手动发送")
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
        progressStatusView = null
    }

    private fun showProgressPanel(status: String) {
        progressStatusView?.let { statusView ->
            statusView.text = status
            return
        }

        removeCandidatePanel()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundedBackground(0xF7FFFFFF.toInt(), dp(18).toFloat())
            elevation = 18f
        }
        panel.addView(
            TextView(this).apply {
                text = "AI 正在工作"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(18, 51, 47))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        val statusView = TextView(this).apply {
            text = status
            textSize = 13f
            setTextColor(Color.rgb(91, 101, 98))
            setPadding(0, dp(8), 0, 0)
        }
        panel.addView(
            statusView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val params = WindowManager.LayoutParams(
            dp(270),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(190)
        }

        progressStatusView = statusView
        candidatePanelView = panel
        windowManager?.addView(panel, params)
    }

    private fun updateFloatingButtonLoading(isLoading: Boolean) {
        val button = overlayView as? TextView ?: return
        button.text = if (isLoading) "..." else "AI"
        button.alpha = if (isLoading) 0.72f else 1f
    }

    private fun List<ReplyCandidate>.toOverlayCandidates(): List<OverlayCandidate> {
        return map { candidate ->
            OverlayCandidate(
                id = candidate.id,
                text = candidate.text
            )
        }
    }

    private fun RealReplySessionPhase.toOverlayPhase(): OverlayRunPhase {
        return when (this) {
            RealReplySessionPhase.VALIDATING -> OverlayRunPhase.VALIDATING
            RealReplySessionPhase.BUILDING_CONTEXT -> OverlayRunPhase.BUILDING_CONTEXT
            RealReplySessionPhase.OCR_FALLBACK -> OverlayRunPhase.OCR_FALLBACK
            RealReplySessionPhase.REQUESTING_LLM -> OverlayRunPhase.REQUESTING_LLM
            RealReplySessionPhase.LOCAL_FALLBACK -> OverlayRunPhase.LOCAL_FALLBACK
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
    }

    private data class OverlayCandidate(
        val id: String,
        val text: String
    )
}
