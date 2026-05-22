package com.lonquanzj.aireplaymate

import android.app.Activity
import android.content.Intent
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.Secure
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugState
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugStore
import com.lonquanzj.aireplaymate.accessibility.AccessibilityActionBridge
import com.lonquanzj.aireplaymate.accessibility.ReplyAccessibilityService
import com.lonquanzj.aireplaymate.diagnostics.DiagnosticLogState
import com.lonquanzj.aireplaymate.diagnostics.DiagnosticLogStore
import com.lonquanzj.aireplaymate.llm.LlmDebugState
import com.lonquanzj.aireplaymate.llm.LlmDebugStore
import com.lonquanzj.aireplaymate.llm.OpenAiCompatibleLlmGateway
import com.lonquanzj.aireplaymate.ocr.AndroidScreenCaptureProvider
import com.lonquanzj.aireplaymate.ocr.OcrDebugState
import com.lonquanzj.aireplaymate.ocr.OcrDebugStore
import com.lonquanzj.aireplaymate.ocr.OcrCapturePermissionState
import com.lonquanzj.aireplaymate.ocr.OcrCapturePermissionStore
import com.lonquanzj.aireplaymate.ocr.OcrScreenCaptureState
import com.lonquanzj.aireplaymate.ocr.OcrScreenCaptureStore
import com.lonquanzj.aireplaymate.ocr.MlKitChineseOcrEngine
import com.lonquanzj.aireplaymate.overlay.OverlayButtonService
import com.lonquanzj.aireplaymate.overlay.OverlayDiagnosticsState
import com.lonquanzj.aireplaymate.overlay.OverlayDiagnosticsStore
import com.lonquanzj.aireplaymate.overlay.OverlayServiceState
import com.lonquanzj.aireplaymate.overlay.OverlayServiceStateStore
import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.DefaultPromptBuilder
import com.lonquanzj.aireplaymate.prompt.LlmRequest
import com.lonquanzj.aireplaymate.prompt.PolishGoalConfig
import com.lonquanzj.aireplaymate.prompt.ReplyPersonaConfig
import com.lonquanzj.aireplaymate.prompt.ReplyPlaybookConfig
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalog
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalogState
import com.lonquanzj.aireplaymate.prompt.ReplyStyleMode
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import com.lonquanzj.aireplaymate.settings.AppSettingsStore
import com.lonquanzj.aireplaymate.settings.AppSettingsValidation
import com.lonquanzj.aireplaymate.settings.AppSettingsValidator
import com.lonquanzj.aireplaymate.settings.ReplyStyleCatalogStore
import com.lonquanzj.aireplaymate.settings.ReplyStyleSettingsStore
import com.lonquanzj.aireplaymate.session.ReplyContextPreviewStore
import com.lonquanzj.aireplaymate.ui.theme.AiReplayMateTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

internal data class PermissionSnapshot(
    val accessibilityEnabled: Boolean,
    val overlayEnabled: Boolean
)

private enum class HomeTab(val label: String) {
    MAIN("主界面"),
    STYLE("回复风格"),
    ADVANCED("高级调试"),
    LLM("LLM 设置")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DiagnosticLogStore.initialize(this)

        setContent {
            AiReplayMateTheme {
                MainScreen(
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenOverlaySettings = {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    },
                    onStartOverlayService = {
                        if (Settings.canDrawOverlays(this)) {
                            OverlayServiceStateStore.onStartRequested()
                            startService(Intent(this, OverlayButtonService::class.java))
                        } else {
                            OverlayServiceStateStore.onMissingPermission()
                            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                        }
                    },
                    onStopOverlayService = {
                        OverlayServiceStateStore.onStopped("已请求停止 AI 气泡")
                        stopService(Intent(this, OverlayButtonService::class.java))
                    },
                    loadPermissionSnapshot = { readPermissionSnapshot(this) },
                    loadAppSettings = { AppSettingsStore.load(this) },
                    saveAppSettings = { AppSettingsStore.save(this, it) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onStartOverlayService: () -> Unit,
    onStopOverlayService: () -> Unit,
    loadPermissionSnapshot: () -> PermissionSnapshot,
    loadAppSettings: () -> AppSettings,
    saveAppSettings: (AppSettings) -> Unit
) {
    var permissionSnapshot by remember { mutableStateOf(loadPermissionSnapshot()) }
    var appSettings by remember { mutableStateOf(loadAppSettings()) }
    var testingLlm by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var replyStyleProfile by remember(context) {
        mutableStateOf(ReplyStyleSettingsStore.load(context))
    }
    var replyStyleCatalog by remember(context) {
        mutableStateOf(ReplyStyleCatalogStore.load(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val pagerState = rememberPagerState(pageCount = { HomeTab.entries.size })
    val mediaProjectionManager = remember(context) {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    DisposableEffect(lifecycleOwner, loadPermissionSnapshot) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionSnapshot = loadPermissionSnapshot()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        LlmDebugStore.state.collect { state ->
            DiagnosticLogStore.recordLlm(state)
        }
    }

    LaunchedEffect(Unit) {
        OcrDebugStore.state.collect { state ->
            DiagnosticLogStore.recordOcr(state)
        }
    }

    LaunchedEffect(Unit) {
        OverlayDiagnosticsStore.state.collect { state ->
            DiagnosticLogStore.recordOverlay(state)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF7F3FF),
                        Color(0xFFF1ECFF),
                        Color(0xFFFFFCFF)
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.statusBars
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 12.dp
                ) {
                    HomeTab.entries.forEachIndexed { index, tab ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = { Text(tab.label) }
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    key = { it }
                ) { page ->
                    when (HomeTab.entries[page]) {
                        HomeTab.MAIN -> {
                            MainTabContent(
                                permissionSnapshot = permissionSnapshot,
                                appSettings = appSettings,
                                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                                onOpenOverlaySettings = onOpenOverlaySettings,
                                onStartOverlayService = onStartOverlayService,
                                onStopOverlayService = onStopOverlayService,
                                onShowAboutDialog = { showAboutDialog = true }
                            )
                        }

                        HomeTab.LLM -> {
                            LlmTabContent(
                                appSettings = appSettings,
                                testingLlm = testingLlm,
                                onSettingsChange = {
                                    appSettings = it
                                    saveAppSettings(it)
                                },
                                onTestConnection = {
                                    scope.launch {
                                        testingLlm = true
                                        testLlmConnection(appSettings)
                                        testingLlm = false
                                    }
                                }
                            )
                        }

                        HomeTab.STYLE -> {
                            StyleTabContent(
                                appSettings = appSettings,
                                replyStyleProfile = replyStyleProfile,
                                replyStyleCatalog = replyStyleCatalog,
                                onProfileChange = { nextProfile ->
                                    replyStyleProfile = nextProfile
                                    ReplyStyleSettingsStore.save(context, nextProfile)
                                },
                                onCatalogChange = { nextCatalog ->
                                    replyStyleCatalog = nextCatalog
                                    ReplyStyleCatalogStore.save(context, nextCatalog)
                                    replyStyleProfile = replyStyleProfile.withResolvedCatalog(nextCatalog)
                                    ReplyStyleSettingsStore.save(context, replyStyleProfile)
                                },
                                onResetBuiltinCatalog = {
                                    val nextCatalog = ReplyStyleCatalogStore.resetBuiltins(context, replyStyleCatalog)
                                    replyStyleCatalog = nextCatalog
                                    replyStyleProfile = replyStyleProfile.withResolvedCatalog(nextCatalog)
                                    ReplyStyleSettingsStore.save(context, replyStyleProfile)
                                }
                            )
                        }

                        HomeTab.ADVANCED -> {
                            AdvancedTabContent(
                                mediaProjectionManager = mediaProjectionManager
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}


private fun readPermissionSnapshot(context: Context): PermissionSnapshot {
    val enabledServices = Secure.getString(
        context.contentResolver,
        Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()

    val targetService = "${context.packageName}/${ReplyAccessibilityService::class.java.name}"
    val accessibilityEnabled = enabledServices
        .split(':')
        .any { it.equals(targetService, ignoreCase = true) }

    return PermissionSnapshot(
        accessibilityEnabled = accessibilityEnabled,
        overlayEnabled = Settings.canDrawOverlays(context)
    )
}

private suspend fun testLlmConnection(settings: AppSettings) {
    val validation = AppSettingsValidator.validate(settings)
    if (!validation.canRequest) {
        LlmDebugStore.onSkipped(
            baseUrl = settings.baseUrl,
            model = settings.model,
            reason = validation.errors.joinToString("；")
        )
        return
    }

    OpenAiCompatibleLlmGateway(settings).generateReplies(
        LlmRequest(
            systemPrompt = "你是 AiReplayMate 的连接测试助手，只用于确认接口可用。",
            userPrompt = "请严格返回 JSON：{\"candidates\":[{\"text\":\"连接测试成功\"}]}",
            temperature = 0f,
            maxTokens = 80,
            candidateCount = 1
        )
    )
}

internal fun buildAccessibilityDebugSnapshot(
    debugState: AccessibilityDebugState,
    ocrDebugState: OcrDebugState
): String {
    return buildString {
        appendLine("AiReplayMate Accessibility Debug Snapshot")
        appendLine("updatedAt=${formatTimestamp(debugState.updatedAtMillis)}")
        appendLine("serviceConnected=${debugState.serviceConnected}")
        appendLine("lastEvent=${debugState.lastEventName}")
        appendLine("package=${debugState.packageName.ifEmpty { "N/A" }}")
        appendLine("class=${debugState.className.ifEmpty { "N/A" }}")
        appendLine("editableNodeCount=${debugState.editableNodeCount}")
        appendLine("isWechatPackage=${debugState.isWechatPackage}")
        appendLine("looksLikeChatPage=${debugState.looksLikeChatPage}")
        appendLine("conversationTitle=${debugState.conversationTitle ?: "N/A"}")
        appendLine("inputNodeFound=${debugState.inputNodeFound}")
        appendLine("inputNodeHint=${debugState.inputNodeHint ?: "N/A"}")
        appendLine("chatDetectionReason=${debugState.chatDetectionReason.ifEmpty { "N/A" }}")
        appendLine("lastAutofillStatus=${debugState.lastAutofillStatus}")
        appendLine("lastAutofillCategory=${debugState.lastAutofillCategory.label}")
        appendLine("lastAutofillPreview=${debugState.lastAutofillPreview ?: "N/A"}")
        appendLine("ocrCategory=${ocrDebugState.lastCategory.label}")
        appendLine("ocrStatus=${ocrDebugState.lastStatus}")
        appendLine("ocrReason=${ocrDebugState.lastReason.ifBlank { "N/A" }}")
        appendLine()
        appendLine("Autofill Steps:")
        if (debugState.lastAutofillSteps.isEmpty()) {
            appendLine("N/A")
        } else {
            debugState.lastAutofillSteps.forEach(::appendLine)
        }
        appendLine()
        appendLine("Extracted Messages:")
        if (debugState.extractedMessageDebugPreviews.isEmpty()) {
            appendLine("N/A")
        } else {
            debugState.extractedMessageDebugPreviews.forEach(::appendLine)
        }
        appendLine()
        appendLine("Visible Text Sample:")
        if (debugState.visibleTextSample.isEmpty()) {
            appendLine("N/A")
        } else {
            debugState.visibleTextSample.forEachIndexed { index, sample ->
                appendLine("${index + 1}. $sample")
            }
        }
    }
}

internal fun buildOcrDebugSnapshot(
    debugState: OcrDebugState,
    capturePermissionState: OcrCapturePermissionState,
    screenCaptureState: OcrScreenCaptureState
): String {
    return buildString {
        appendLine("AiReplayMate OCR Debug Snapshot")
        appendLine("updatedAt=${formatTimestamp(debugState.updatedAtMillis)}")
        appendLine("capturePermission=${capturePermissionState.status.label}")
        appendLine("captureReady=${capturePermissionState.isReady}")
        appendLine("captureUpdatedAt=${formatTimestamp(capturePermissionState.updatedAtMillis)}")
        appendLine("screenCaptureStatus=${screenCaptureState.status.label}")
        appendLine("screenCaptureMessage=${screenCaptureState.message}")
        appendLine("screenCaptureSize=${screenCaptureState.sizeLabel}")
        appendLine("screenCaptureRowStride=${screenCaptureState.rowStride ?: "N/A"}")
        appendLine("screenCapturePixelStride=${screenCaptureState.pixelStride ?: "N/A"}")
        appendLine("screenCaptureUpdatedAt=${formatTimestamp(screenCaptureState.updatedAtMillis)}")
        appendLine("engineConfigured=${debugState.engineConfigured}")
        appendLine("category=${debugState.lastCategory.label}")
        appendLine("status=${debugState.lastStatus}")
        appendLine("reason=${debugState.lastReason.ifBlank { "N/A" }}")
        appendLine("targetApp=${debugState.targetApp.ifBlank { "N/A" }}")
        appendLine()
        appendLine("OCR Steps:")
        if (debugState.steps.isEmpty()) {
            appendLine("N/A")
        } else {
            debugState.steps.forEach(::appendLine)
        }
        appendLine()
        appendLine("Screen Capture Steps:")
        if (screenCaptureState.steps.isEmpty()) {
            appendLine("N/A")
        } else {
            screenCaptureState.steps.forEach(::appendLine)
        }
        appendLine()
        appendLine("OCR Messages:")
        if (debugState.extractedMessagePreviews.isEmpty()) {
            appendLine("N/A")
        } else {
            debugState.extractedMessagePreviews.forEach(::appendLine)
        }
    }
}

internal fun buildLlmDebugSnapshot(debugState: LlmDebugState): String {
    return buildString {
        appendLine("AiReplayMate LLM Debug Snapshot")
        appendLine("updatedAt=${formatTimestamp(debugState.updatedAtMillis)}")
        appendLine("phase=${debugState.phase.label}")
        appendLine("category=${debugState.failureCategory.label}")
        appendLine("baseUrl=${debugState.baseUrl.ifBlank { "N/A" }}")
        appendLine("model=${debugState.model.ifBlank { "N/A" }}")
        appendLine("httpStatus=${debugState.httpStatus ?: "N/A"}")
        appendLine("candidateCount=${debugState.candidateCount}")
        appendLine("errorSummary=${debugState.errorSummary ?: "N/A"}")
        appendLine("recoveryHint=${debugState.recoveryHint}")
        appendLine("requestPreview=${debugState.requestPreview ?: "N/A"}")
        appendLine("responsePreview=${debugState.responsePreview ?: "N/A"}")
        appendLine()
        appendLine("LLM History:")
        if (debugState.history.isEmpty()) {
            appendLine("N/A")
        } else {
            debugState.history.forEachIndexed { index, entry ->
                appendLine(
                    "${index + 1}. ${formatTimestamp(entry.timestampMillis)} " +
                        "${entry.phase.label}/${entry.category.label} " +
                        "http=${entry.httpStatus ?: "N/A"} " +
                        "candidates=${entry.candidateCount} " +
                        "model=${entry.model.ifBlank { "N/A" }} " +
                        "baseUrl=${entry.baseUrl.ifBlank { "N/A" }} " +
                        "summary=${entry.summary} " +
                        "recoveryHint=${entry.recoveryHint}"
                )
            }
        }
    }
}

internal fun buildOverlayDebugSnapshot(debugState: OverlayDiagnosticsState): String {
    return buildString {
        appendLine("AiReplayMate Overlay Debug Snapshot")
        appendLine("updatedAt=${formatTimestamp(debugState.updatedAtMillis)}")
        appendLine("phase=${debugState.phase.label}")
        appendLine("status=${debugState.status}")
        appendLine("accessibilityMessageCount=${debugState.accessibilityMessageCount}")
        appendLine("ocrMessageCount=${debugState.ocrMessageCount}")
        appendLine("mergedMessageCount=${debugState.mergedMessageCount}")
        appendLine("candidateCount=${debugState.candidateCount}")
        appendLine("candidateSource=${debugState.candidateSource}")
        appendLine("usedOcr=${debugState.usedOcr}")
        appendLine("usedLocalFallback=${debugState.usedLocalFallback}")
        appendLine("lastFailure=${debugState.lastFailure ?: "N/A"}")
        appendLine()
        appendLine("Overlay Steps:")
        if (debugState.steps.isEmpty()) {
            appendLine("N/A")
        } else {
            debugState.steps.forEach(::appendLine)
        }
    }
}

internal fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "暂无"
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
