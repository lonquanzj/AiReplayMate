package com.lonquanzj.aireplaymate.overlay

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.lonquanzj.aireplaymate.R
import com.lonquanzj.aireplaymate.accessibility.AccessibilityActionBridge
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugStore
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugState
import com.lonquanzj.aireplaymate.ocr.OcrDebugStore
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import com.lonquanzj.aireplaymate.session.ReplyContextPreviewStore
import com.lonquanzj.aireplaymate.settings.ReplyStyleCatalogStore
import com.lonquanzj.aireplaymate.settings.ReplyStyleSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 悬浮窗服务主入口。
 *
 * 这个类只负责生命周期与流程编排：
 * 1) 管理悬浮按钮与面板的显示/移除。
 * 2) 串起候选生成会话与诊断记录。
 * 3) 把交互、动画、布局、面板构建委托给拆分后的组件。
 *
 * 阅读索引（按主调用链）：
 * 1. onCreate/onStartCommand -> showFloatingButton
 * 2. showFloatingButton -> triggerCandidateGeneration
 * 3. triggerCandidateGeneration -> showCandidatePanel
 * 4. showCandidatePanel -> showStyleMenuPanel/showFailurePanel/showProgressPanel
 * 5. 面板收敛与状态清理 -> removeCandidatePanel
 * 6. 动画与按钮状态 -> updateFloatingButtonLoading/start/stop
 */
class OverlayButtonService : Service() {
    companion object {
        private const val CANDIDATE_PANEL_WIDTH_DP = 284
        private const val STYLE_MENU_PANEL_WIDTH_DP = 280
        private const val FAILURE_PANEL_WIDTH_DP = 300
        private const val FAILURE_PANEL_HEIGHT_DP = 500
        private const val PROGRESS_PANEL_WIDTH_DP = 248
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var floatingButtonView: FrameLayout? = null
    private var candidatePanelView: View? = null
    private var progressStatusView: TextView? = null
    private var progressIndicatorView: LinearLayout? = null
    // 悬浮按钮当前的 WindowLayout 参数，面板锚点定位会依赖它。
    private var layoutParams: WindowManager.LayoutParams? = null
    // 生成中互斥锁：避免并发触发多次候选会话。
    private var isGeneratingCandidates = false
    private lateinit var floatingBubbleController: OverlayFloatingBubbleController
    private lateinit var panelAnimationController: OverlayPanelAnimationController
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // ===== 生命周期 =====

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            OverlayServiceStateStore.onMissingPermission()
            Toast.makeText(this, "请先开启悬浮窗权限", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingBubbleController = OverlayFloatingBubbleController(
            context = this,
            mainHandler = mainHandler,
            windowManagerProvider = { windowManager },
            onLongPress = { showStyleMenuPanel() },
            isGeneratingCandidates = { isGeneratingCandidates }
        )
        panelAnimationController = OverlayPanelAnimationController(this)
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
        // Service 销毁时需要显式移除 WindowManager 挂载的所有 view。
        removeCandidatePanel()
        stopFloatingButtonAnimation()
        overlayView?.let { view ->
            windowManager?.removeView(view)
        }
        overlayView = null
        floatingButtonView = null
        if (::floatingBubbleController.isInitialized) {
            floatingBubbleController.floatingAvatarView = null
        }
        windowManager = null
        OverlayServiceStateStore.onStopped()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ===== 会话入口（悬浮球 -> 候选生成） =====

    private fun showFloatingButton() {
        if (overlayView != null) return

        val floatingButtonBundle = buildFloatingButtonBundle(
            context = this,
            onClick = {
                triggerCandidateGeneration(ReplyStyleSettingsStore.load(this@OverlayButtonService).asDefaultReply())
            },
            onAvatarBound = { avatarView ->
                floatingBubbleController.floatingAvatarView = avatarView
            }
        )
        val button = floatingButtonBundle.button
        val params = floatingButtonBundle.layoutParams

        layoutParams = params
        overlayView = button
        floatingButtonView = button
        floatingBubbleController.attachDragHandler(button, params)
        windowManager?.addView(button, params)
        floatingBubbleController.startFloatingIdleAnimation()
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
                // 优先实时探测页面；失败时回退到最近一次调试快照，保证流程可继续。
                val debugState = AccessibilityActionBridge.tryInspectCurrentWindow()
                    .takeIf { it.success }
                    ?.state
                    ?: AccessibilityDebugStore.state.value
                showCandidatePanel(debugState, styleProfile, draftText)
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
        // 会话阶段统一写入诊断存储，便于首页和故障面板复盘。
        OverlayDiagnosticsStore.begin()
        val outcome = runOverlaySession(
            context = this,
            debugState = debugState,
            styleProfile = styleProfile,
            draftText = draftText,
            onPhase = { phase, status ->
                OverlayDiagnosticsStore.onPhase(
                    phase = phase.toOverlayPhase(),
                    status = status
                )
                showProgressPanel(overlayProgressStatus(phase))
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

        if (outcome is OverlaySessionOutcome.Failure) {
            val message = outcome.message
            OverlayDiagnosticsStore.onFailed(message)
            showFailurePanel(message, debugState)
            return
        }

        val success = (outcome as OverlaySessionOutcome.Success).value
        val settings = success.settings
        val sessionResult = success.sessionResult

        if (sessionResult.usedLocalFallback) {
            val reason = sessionResult.localFallbackReason ?: "未知错误"
            val promptHint = if (
                reason.contains("候选不足") ||
                reason.contains("parse", ignoreCase = true) ||
                reason.contains("JSON", ignoreCase = true)
            ) {
                "Prompt 协议可能不匹配，已使用本地兜底"
            } else {
                "LLM 不可用，已使用本地兜底：$reason"
            }
            Toast.makeText(
                this,
                promptHint,
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(this, "候选回复已生成", Toast.LENGTH_SHORT).show()
        }
        val candidates = sessionResult.candidates.toOverlayCandidates()
        ReplyContextPreviewStore.update(
            conversationTitle = debugState.conversationTitle,
            messages = sessionResult.context.messages
        )
        OverlayDiagnosticsStore.onCandidates(
            count = candidates.size,
            usedLocalFallback = sessionResult.usedLocalFallback,
            candidateSource = sessionResult.candidateSource
        )
        removeCandidatePanel()

        val subtitle = sessionResult.toCandidatePanelSubtitle(settings, styleProfile)
        val panel = buildCandidatePanelView(
            context = this,
            title = styleProfile.candidatePanelLabel,
            subtitle = subtitle,
            modeLabel = "",
            candidates = candidates,
            onRegenerate = {
                OverlayDiagnosticsStore.onDone("用户重新生成候选")
                removeCandidatePanel()
                triggerCandidateGeneration(styleProfile, draftText)
            },
            onClose = {
                OverlayDiagnosticsStore.onDone("用户关闭候选面板")
                removeCandidatePanel()
            },
            onCandidateClick = { candidate ->
                val result = AccessibilityActionBridge.tryAutofill(candidate.text)
                OverlayDiagnosticsStore.onAutofill(result.message)
                Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
                if (result.success) {
                    OverlayDiagnosticsStore.onDone("候选已填入输入框，等待用户手动发送")
                    removeCandidatePanel()
                } else {
                    OverlayDiagnosticsStore.onFailed(result.message)
                }
            }
        )

        candidatePanelView = panel
        val params = OverlayPanelLayoutCalculator.anchoredPanelLayoutParams(
            context = this,
            contentView = panel,
            buttonLayoutParams = layoutParams,
            panelWidth = OverlayPanelLayoutCalculator.panelWidthPx(
                context = this,
                desiredDp = CANDIDATE_PANEL_WIDTH_DP
            )
        )
        windowManager?.addView(panel, params)
        animatePanelIn(panel, dp(8).toFloat())
    }

    // ===== 面板渲染（样式/失败/进度） =====

    private fun showStyleMenuPanel() {
        if (isGeneratingCandidates) {
            Toast.makeText(this, "正在生成候选回复，请稍等", Toast.LENGTH_SHORT).show()
            return
        }

        val current = ReplyStyleSettingsStore.load(this)
        val catalog = ReplyStyleCatalogStore.load(this)
        removeCandidatePanel()

        var styleMenuScrollView: ScrollView? = null
        val builtScrollView = buildStyleMenuPanelView(
            context = this,
            current = current,
            catalog = catalog,
            onClose = { removeCandidatePanel() },
            onProfileChosen = { profile, persistAsDefault, requiresDraft ->
                val draftText = if (requiresDraft) {
                    val readResult = AccessibilityActionBridge.tryReadInputDraft()
                    if (!readResult.success) {
                        Toast.makeText(this, readResult.message, Toast.LENGTH_SHORT).show()
                        return@buildStyleMenuPanelView
                    }
                    readResult.text
                } else {
                    null
                }
                if (persistAsDefault) {
                    ReplyStyleSettingsStore.save(this, profile.asDefaultReply())
                }
                removeCandidatePanel()
                triggerCandidateGeneration(profile, draftText)
            },
            onLayoutRefreshRequested = {
                val attachedScrollView = styleMenuScrollView
                if (attachedScrollView?.parent != null) {
                    // Tab/分组切换后内容高度可能变化，需要刷新布局参数避免裁切。
                    val nextParams = OverlayPanelLayoutCalculator.anchoredPanelLayoutParams(
                        context = this,
                        contentView = attachedScrollView,
                        buttonLayoutParams = layoutParams,
                        panelWidth = OverlayPanelLayoutCalculator.panelWidthPx(
                            context = this,
                            desiredDp = STYLE_MENU_PANEL_WIDTH_DP
                        ),
                        maxPanelHeight = OverlayPanelLayoutCalculator.styleMenuMaxHeightPx(this)
                    )
                    windowManager?.updateViewLayout(attachedScrollView, nextParams)
                }
            }
        )
        styleMenuScrollView = builtScrollView
        val scrollView = builtScrollView
        candidatePanelView = scrollView
        val params = OverlayPanelLayoutCalculator.anchoredPanelLayoutParams(
            context = this,
            contentView = scrollView,
            buttonLayoutParams = layoutParams,
            panelWidth = OverlayPanelLayoutCalculator.panelWidthPx(
                context = this,
                desiredDp = STYLE_MENU_PANEL_WIDTH_DP
            ),
            maxPanelHeight = OverlayPanelLayoutCalculator.styleMenuMaxHeightPx(this)
        )
        windowManager?.addView(scrollView, params)
        animatePanelIn(scrollView, dp(8).toFloat())

    }

    private fun showFailurePanel(
        message: String,
        debugState: AccessibilityDebugState
    ) {
        removeCandidatePanel()
        val ocrDebugState = OcrDebugStore.state.value

        val scrollView = buildFailurePanelView(
            context = this,
            message = message,
            debugState = debugState,
            ocrDebugState = ocrDebugState,
            onClose = {
                OverlayDiagnosticsStore.onDone("用户关闭失败提示")
                removeCandidatePanel()
            },
            onOpenAccessibilitySettings = {
                startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        )
        candidatePanelView = scrollView
        val params = OverlayPanelLayoutCalculator.anchoredPanelLayoutParams(
            context = this,
            contentView = scrollView,
            buttonLayoutParams = layoutParams,
            panelWidth = OverlayPanelLayoutCalculator.panelWidthPx(
                context = this,
                desiredDp = FAILURE_PANEL_WIDTH_DP
            ),
            panelHeight = dp(FAILURE_PANEL_HEIGHT_DP)
        )
        windowManager?.addView(scrollView, params)
    }




    private fun removeCandidatePanel() {
        // 进度点动画绑定在面板上，移除面板前先停掉动画避免泄漏。
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
            // 如果进度面板已存在，只更新文案，避免反复 remove/add 造成闪烁。
            statusView.text = status
            return
        }

        removeCandidatePanel()
        val progressViews = buildProgressPanelView(
            context = this,
            status = status
        )

        progressStatusView = progressViews.statusView
        progressIndicatorView = progressViews.indicator
        candidatePanelView = progressViews.panel
        val params = OverlayPanelLayoutCalculator.anchoredPanelLayoutParams(
            context = this,
            contentView = progressViews.panel,
            buttonLayoutParams = layoutParams,
            panelWidth = dp(PROGRESS_PANEL_WIDTH_DP)
        )
        windowManager?.addView(progressViews.panel, params)
        startProgressIndicatorAnimation(progressViews.indicator)
    }

    // ===== 动画与交互状态 =====

    private fun updateFloatingButtonLoading(isLoading: Boolean) {
        val button = floatingButtonView ?: return
        button.background = floatingButtonBackgroundDrawable()
        if (isLoading) {
            floatingBubbleController.stopFloatingIdleAnimation()
            startFloatingButtonAnimation(button)
        } else {
            stopFloatingButtonAnimation()
            button.alpha = 0.94f
            button.scaleX = 1f
            button.scaleY = 1f
            button.translationZ = 0f
            if (!floatingBubbleController.isDocked) {
                floatingBubbleController.startFloatingIdleAnimation()
            }
        }
    }

    private fun startFloatingButtonAnimation(button: View) {
        if (::panelAnimationController.isInitialized) {
            panelAnimationController.startFloatingButtonAnimation(button)
        }
    }

    private fun stopFloatingButtonAnimation() {
        if (::panelAnimationController.isInitialized) {
            panelAnimationController.stopFloatingButtonAnimation()
        }
        if (::floatingBubbleController.isInitialized) {
            floatingBubbleController.cancelDockAndIdle()
        }
    }

    private fun startProgressIndicatorAnimation(indicator: LinearLayout) {
        if (::panelAnimationController.isInitialized) {
            panelAnimationController.startProgressIndicatorAnimation(indicator)
        }
    }

    private fun stopProgressIndicatorAnimation() {
        if (::panelAnimationController.isInitialized) {
            panelAnimationController.stopProgressIndicatorAnimation()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

}
