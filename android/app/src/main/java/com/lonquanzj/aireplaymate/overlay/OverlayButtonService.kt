package com.lonquanzj.aireplaymate.overlay

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.lonquanzj.aireplaymate.accessibility.AccessibilityActionBridge
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugStore
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugState
import com.lonquanzj.aireplaymate.prompt.ReplyCandidate
import com.lonquanzj.aireplaymate.prompt.PolishGoal
import com.lonquanzj.aireplaymate.prompt.ReplyPersona
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalog
import com.lonquanzj.aireplaymate.prompt.ReplyStyleMode
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import com.lonquanzj.aireplaymate.session.RealReplySessionPhase
import com.lonquanzj.aireplaymate.session.RealReplySessionRunner
import com.lonquanzj.aireplaymate.settings.AppSettingsStore
import com.lonquanzj.aireplaymate.settings.ReplyStyleSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OverlayButtonService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var floatingButtonView: FrameLayout? = null
    private var candidatePanelView: View? = null
    private var progressStatusView: TextView? = null
    private var progressIndicatorView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isGeneratingCandidates = false
    private var floatingButtonAnimator: AnimatorSet? = null
    private val progressIndicatorAnimators = mutableListOf<Animator>()
    private val mainHandler = Handler(Looper.getMainLooper())
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
        stopFloatingButtonAnimation()
        overlayView?.let { view ->
            windowManager?.removeView(view)
        }
        overlayView = null
        floatingButtonView = null
        windowManager = null
        OverlayServiceStateStore.onStopped()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun showFloatingButton() {
        if (overlayView != null) return

        val button = FrameLayout(this).apply {
            background = floatingButtonBackground(isLoading = false)
            elevation = 18f
            setOnClickListener {
                triggerCandidateGeneration(ReplyStyleSettingsStore.load(this@OverlayButtonService).asDefaultReply())
            }
        }
        button.addView(createFloatingButtonIcon())

        val params = WindowManager.LayoutParams(
            dp(72),
            dp(72),
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(220)
        }

        layoutParams = params
        overlayView = button
        floatingButtonView = button
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
        var longPressTriggered = false
        var longPressRunnable: Runnable? = null

        view.setOnTouchListener { target, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    longPressTriggered = false
                    longPressRunnable = Runnable {
                        if (!moved) {
                            longPressTriggered = true
                            target.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            showStyleMenuPanel()
                        }
                    }.also { target.postDelayed(it, LONG_PRESS_TIMEOUT_MS) }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > DRAG_SLOP || kotlin.math.abs(dy) > DRAG_SLOP) {
                        moved = true
                        longPressRunnable?.let(target::removeCallbacks)
                    }
                    params.x = startX + dx
                    params.y = startY + dy
                    windowManager?.updateViewLayout(target, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    longPressRunnable?.let(target::removeCallbacks)
                    if (!moved && !longPressTriggered) {
                        target.performClick()
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

    private fun triggerCandidateGeneration(
        styleProfile: ReplyStyleProfile,
        draftText: String? = null
    ) {
        if (isGeneratingCandidates) {
            Toast.makeText(this, "正在生成候选回复，请稍等", Toast.LENGTH_SHORT).show()
            return
        }
        serviceScope.launch {
            isGeneratingCandidates = true
            updateFloatingButtonLoading(true)
            try {
                showCandidatePanel(AccessibilityDebugStore.state.value, styleProfile, draftText)
            } finally {
                isGeneratingCandidates = false
                updateFloatingButtonLoading(false)
            }
        }
    }

    private suspend fun showCandidatePanel(
        debugState: AccessibilityDebugState,
        styleProfile: ReplyStyleProfile,
        draftText: String? = null
    ) {
        OverlayDiagnosticsStore.begin()
        val settings = AppSettingsStore.load(this)
        val result = RealReplySessionRunner(applicationContext).run(
            debugState = debugState,
            settings = settings,
            styleProfile = styleProfile,
            draftText = draftText,
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
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = overlayPanelBackground()
            elevation = 22f
        }

        panel.addView(
            TextView(this).apply {
                text = "候选回复 · ${styleProfile.shortLabel}"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
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
                setTextColor(0xCFEAE3FF.toInt())
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
                setTextColor(0xFFD9CBFF.toInt())
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

    private fun showStyleMenuPanel() {
        if (isGeneratingCandidates) {
            Toast.makeText(this, "正在生成候选回复，请稍等", Toast.LENGTH_SHORT).show()
            return
        }

        val current = ReplyStyleSettingsStore.load(this)
        removeCandidatePanel()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = overlayPanelBackground()
            elevation = 22f
        }

        content.addView(menuTitle("选择 AI 回复风格"))
        content.addView(menuHint("短按气泡用默认风格；这里选择后会记住，并立即生成候选。"))

        content.addView(menuSectionLabel("角色"))
        ReplyPersona.entries.forEach { persona ->
            val profile = current.copy(
                mode = ReplyStyleMode.QUICK_REPLY,
                persona = persona
            )
            content.addView(menuButton(persona.label, profile))
        }

        content.addView(menuSectionLabel("话术宝典"))
        ReplyStyleCatalog.scenes.forEach { scene ->
            val profile = current.copy(
                mode = ReplyStyleMode.PLAYBOOK,
                playbookScene = scene
            )
            content.addView(menuButton("${scene.categoryLabel} · ${scene.sceneLabel}", profile, persistAsDefault = false))
        }

        content.addView(menuSectionLabel("润色表达"))
        PolishGoal.entries.forEach { goal ->
            val profile = current.copy(
                mode = ReplyStyleMode.POLISH,
                polishGoal = goal
            )
            content.addView(menuButton(goal.label, profile, persistAsDefault = false, requiresDraft = true))
        }

        content.addView(
            TextView(this).apply {
                text = "关闭"
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(0xFFD9CBFF.toInt())
                setPadding(0, dp(12), 0, 0)
                setOnClickListener { removeCandidatePanel() }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val scrollView = ScrollView(this).apply {
            addView(content)
        }
        val params = WindowManager.LayoutParams(
            dp(330),
            dp(540),
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(150)
        }

        candidatePanelView = scrollView
        windowManager?.addView(scrollView, params)
    }

    private fun menuTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
    }

    private fun menuHint(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(0xCFEAE3FF.toInt())
            setPadding(0, dp(6), 0, dp(4))
        }
    }

    private fun menuSectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFDCA8.toInt())
            setPadding(0, dp(12), 0, dp(2))
        }
    }

    private fun menuButton(
        label: String,
        profile: ReplyStyleProfile,
        persistAsDefault: Boolean = true,
        requiresDraft: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = softPurpleCardBackground()
            setOnClickListener {
                val draftText = if (requiresDraft) {
                    val readResult = AccessibilityActionBridge.tryReadInputDraft()
                    if (!readResult.success) {
                        Toast.makeText(this@OverlayButtonService, readResult.message, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    readResult.text
                } else {
                    null
                }
                if (persistAsDefault) {
                    ReplyStyleSettingsStore.save(this@OverlayButtonService, profile.asDefaultReply())
                }
                removeCandidatePanel()
                triggerCandidateGeneration(profile, draftText)
            }
        }.also { view ->
            view.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
        }
    }

    private fun candidateView(candidate: OverlayCandidate): View {
        return TextView(this).apply {
            text = candidate.text
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = softPurpleCardBackground()
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
        stopProgressIndicatorAnimation()
        candidatePanelView?.let { view ->
            windowManager?.removeView(view)
        }
        candidatePanelView = null
        progressStatusView = null
        progressIndicatorView = null
    }

    private fun showProgressPanel(status: String) {
        progressStatusView?.let { statusView ->
            statusView.text = status
            return
        }

        removeCandidatePanel()
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = overlayPanelBackground()
            elevation = 22f
        }
        panel.addView(
            TextView(this).apply {
                text = "AI 正在工作"
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        val indicator = createProgressIndicator()
        panel.addView(
            indicator,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        )
        val statusView = TextView(this).apply {
            text = status
            textSize = 13f
            setTextColor(0xCFEAE3FF.toInt())
            setPadding(0, dp(10), 0, 0)
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
        progressIndicatorView = indicator
        candidatePanelView = panel
        windowManager?.addView(panel, params)
        startProgressIndicatorAnimation(indicator)
    }

    private fun updateFloatingButtonLoading(isLoading: Boolean) {
        val button = floatingButtonView ?: return
        button.background = floatingButtonBackground(isLoading)
        if (isLoading) {
            startFloatingButtonAnimation(button)
        } else {
            stopFloatingButtonAnimation()
            button.alpha = 1f
            button.scaleX = 1f
            button.scaleY = 1f
            button.translationZ = 0f
        }
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

    private fun floatingButtonBackground(isLoading: Boolean): GradientDrawable {
        val colors = if (isLoading) {
            intArrayOf(0xFFB988FF.toInt(), 0xFF6C3BFF.toInt())
        } else {
            intArrayOf(0xFF9E73FF.toInt(), 0xFF5A2FE0.toInt())
        }
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(2), 0x66FFFFFF)
        }
    }

    private fun createFloatingButtonIcon(): View {
        val icon = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val bubble = FrameLayout(this).apply {
            background = roundedBackground(0xF7FFFFFF.toInt(), dp(12).toFloat())
            elevation = 3f
        }
        icon.addView(
            bubble,
            FrameLayout.LayoutParams(dp(34), dp(26), Gravity.CENTER).apply {
                topMargin = dp(-4)
            }
        )

        val tail = View(this).apply {
            background = roundedBackground(0xF7FFFFFF.toInt(), dp(3).toFloat())
            rotation = 45f
        }
        icon.addView(
            tail,
            FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER).apply {
                topMargin = dp(18)
                marginStart = dp(2)
            }
        )

        repeat(3) { index ->
            bubble.addView(
                View(this).apply {
                    background = roundedBackground(0xFF7C52FF.toInt(), dp(3).toFloat())
                },
                FrameLayout.LayoutParams(dp(6), dp(6), Gravity.CENTER).apply {
                    val spacing = dp(9)
                    when (index) {
                        0 -> marginEnd = spacing * 2
                        2 -> marginStart = spacing * 2
                    }
                }
            )
        }

        val sparkle = FrameLayout(this)
        sparkle.addView(
            View(this).apply {
                background = roundedBackground(0xD9FFFFFF.toInt(), dp(1).toFloat())
            },
            FrameLayout.LayoutParams(dp(10), dp(2), Gravity.CENTER)
        )
        sparkle.addView(
            View(this).apply {
                background = roundedBackground(0xD9FFFFFF.toInt(), dp(1).toFloat())
                rotation = 90f
            },
            FrameLayout.LayoutParams(dp(10), dp(2), Gravity.CENTER)
        )
        icon.addView(
            sparkle,
            FrameLayout.LayoutParams(dp(10), dp(10), Gravity.TOP or Gravity.END).apply {
                topMargin = dp(17)
                marginEnd = dp(16)
            }
        )

        return icon
    }

    private fun overlayPanelBackground(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xF81A133B.toInt(), 0xF328195B.toInt())
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(22).toFloat()
            setStroke(dp(1), 0x40FFFFFF)
        }
    }

    private fun softPurpleCardBackground(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF6D42F4.toInt(), 0xFF4B2AC3.toInt())
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), 0x2EFFFFFF)
        }
    }

    private fun createProgressIndicator(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            repeat(3) { index ->
                addView(
                    View(this@OverlayButtonService).apply {
                        background = roundedBackground(
                            when (index) {
                                1 -> 0xFFD7C7FF.toInt()
                                else -> 0xFF8E63FF.toInt()
                            },
                            dp(5).toFloat()
                        )
                    },
                    LinearLayout.LayoutParams(dp(10), dp(10)).apply {
                        if (index > 0) {
                            marginStart = dp(8)
                        }
                    }
                )
            }
        }
    }

    private fun startFloatingButtonAnimation(button: View) {
        if (floatingButtonAnimator != null) return
        val scaleX = ObjectAnimator.ofFloat(button, View.SCALE_X, 1f, 1.08f, 1f).apply {
            duration = 1150L
            repeatCount = ObjectAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(button, View.SCALE_Y, 1f, 1.08f, 1f).apply {
            duration = 1150L
            repeatCount = ObjectAnimator.INFINITE
        }
        val alpha = ObjectAnimator.ofFloat(button, View.ALPHA, 1f, 0.9f, 1f).apply {
            duration = 1150L
            repeatCount = ObjectAnimator.INFINITE
        }
        val lift = ObjectAnimator.ofFloat(button, View.TRANSLATION_Z, 0f, dp(8).toFloat(), 0f).apply {
            duration = 1150L
            repeatCount = ObjectAnimator.INFINITE
        }
        floatingButtonAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha, lift)
            start()
        }
    }

    private fun stopFloatingButtonAnimation() {
        floatingButtonAnimator?.cancel()
        floatingButtonAnimator = null
    }

    private fun startProgressIndicatorAnimation(indicator: LinearLayout) {
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

    private fun stopProgressIndicatorAnimation() {
        progressIndicatorAnimators.forEach { it.cancel() }
        progressIndicatorAnimators.clear()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun ReplyStyleProfile.asDefaultReply(): ReplyStyleProfile {
        return copy(mode = ReplyStyleMode.QUICK_REPLY)
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
        const val LONG_PRESS_TIMEOUT_MS = 520L
    }

    private data class OverlayCandidate(
        val id: String,
        val text: String
    )
}
