package com.lonquanzj.aireplaymate

import android.app.Activity
import android.content.Intent
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.Secure
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugState
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugStore
import com.lonquanzj.aireplaymate.accessibility.AccessibilityActionBridge
import com.lonquanzj.aireplaymate.accessibility.ChatMessage
import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.accessibility.ReplyAccessibilityService
import com.lonquanzj.aireplaymate.diagnostics.DiagnosticLogState
import com.lonquanzj.aireplaymate.diagnostics.DiagnosticLogStore
import com.lonquanzj.aireplaymate.demo.DemoAuthor
import com.lonquanzj.aireplaymate.demo.DemoMessage
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
import com.lonquanzj.aireplaymate.overlay.OverlayTriggerStore
import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.LlmRequest
import com.lonquanzj.aireplaymate.prompt.PolishGoal
import com.lonquanzj.aireplaymate.prompt.ReplyPersona
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalog
import com.lonquanzj.aireplaymate.prompt.ReplyStyleMode
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import com.lonquanzj.aireplaymate.settings.AppSettingsStore
import com.lonquanzj.aireplaymate.settings.AppSettingsValidation
import com.lonquanzj.aireplaymate.settings.AppSettingsValidator
import com.lonquanzj.aireplaymate.settings.ReplyStyleSettingsStore
import com.lonquanzj.aireplaymate.session.DemoSessionManager
import com.lonquanzj.aireplaymate.session.ReplyContextPreviewStore
import com.lonquanzj.aireplaymate.session.SessionState
import com.lonquanzj.aireplaymate.ui.theme.AiReplayMateTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private data class PermissionSnapshot(
    val accessibilityEnabled: Boolean,
    val overlayEnabled: Boolean
)

private enum class HomeTab(val label: String) {
    MAIN("主界面"),
    SETTINGS("设置"),
    PERMISSIONS("权限"),
    DEBUG("调试")
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
    val sessionManager = remember { DemoSessionManager() }
    val sessionState by sessionManager.state.collectAsState()
    var selectedTab by remember { mutableStateOf(HomeTab.MAIN) }
    var permissionSnapshot by remember { mutableStateOf(loadPermissionSnapshot()) }
    var appSettings by remember { mutableStateOf(loadAppSettings()) }
    var testingLlm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var replyStyleProfile by remember(context) {
        mutableStateOf(ReplyStyleSettingsStore.load(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val debugState by AccessibilityDebugStore.state.collectAsState()
    val llmDebugState by LlmDebugStore.state.collectAsState()
    val ocrDebugState by OcrDebugStore.state.collectAsState()
    val ocrCapturePermissionState by OcrCapturePermissionStore.state.collectAsState()
    val ocrScreenCaptureState by OcrScreenCaptureStore.state.collectAsState()
    val overlayDiagnosticsState by OverlayDiagnosticsStore.state.collectAsState()
    val overlayServiceState by OverlayServiceStateStore.state.collectAsState()
    val diagnosticLogState by DiagnosticLogStore.state.collectAsState()
    val overlayTrigger by OverlayTriggerStore.request.collectAsState()
    val previewContextState by ReplyContextPreviewStore.state.collectAsState()
    var testingScreenCapture by remember { mutableStateOf(false) }
    var testingOcrRecognition by remember { mutableStateOf(false) }
    val mainTabScrollState = rememberScrollState()
    val permissionsTabScrollState = rememberScrollState()
    val debugTabScrollState = rememberScrollState()
    val mediaProjectionManager = remember(context) {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    val screenCapturePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        OcrCapturePermissionStore.onPermissionResult(
            resultCode = result.resultCode,
            data = result.data
        )
        Toast.makeText(
            context,
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                "屏幕截图授权已就绪"
            } else {
                "屏幕截图授权未完成"
            },
            Toast.LENGTH_SHORT
        ).show()
    }

    val previewMessages = remember(
        sessionState.extractedMessages,
        previewContextState.messages,
        debugState.extractedMessages
    ) {
        when {
            sessionState.extractedMessages.isNotEmpty() -> sessionState.extractedMessages
            previewContextState.messages.isNotEmpty() -> previewContextState.messages.toPreviewMessages()
            else -> debugState.extractedMessages.toPreviewMessages()
        }
    }
    val previewConversationTitle = previewContextState.conversationTitle ?: debugState.conversationTitle

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

    LaunchedEffect(overlayTrigger?.id) {
        val request = overlayTrigger ?: return@LaunchedEffect
        sessionManager.run(debugState = request.debugState, styleProfile = replyStyleProfile.asDefaultReply())
        OverlayTriggerStore.consume(request.id)
    }

    LaunchedEffect(llmDebugState.updatedAtMillis) {
        DiagnosticLogStore.recordLlm(llmDebugState)
    }

    LaunchedEffect(ocrDebugState.updatedAtMillis) {
        DiagnosticLogStore.recordOcr(ocrDebugState)
    }

    LaunchedEffect(overlayDiagnosticsState.updatedAtMillis) {
        DiagnosticLogStore.recordOverlay(overlayDiagnosticsState)
    }

    if (sessionState.showCandidateSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        BackHandler {
            sessionManager.cancelCandidateSelection("用户关闭候选面板，本次生成已取消")
        }

        ModalBottomSheet(
            onDismissRequest = {
                sessionManager.cancelCandidateSelection("候选面板已收起，等待再次触发")
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "候选回复",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "这一步对应真实产品里的 BottomSheet。用户选择后才会执行填入，不会自动发送。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                sessionState.candidates.forEach { candidate ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    sessionManager.selectCandidate(candidate)
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = candidate.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "风格：${candidate.tone}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            sessionManager.regenerateCandidates(replyStyleProfile.asDefaultReply())
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("重新生成")
                    }

                    Button(
                        onClick = {
                            sessionManager.cancelCandidateSelection("本次生成已结束，可重新触发")
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("稍后再选")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF3E7D3),
                        Color(0xFFF7F1E6),
                        Color(0xFFFFFCF6)
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("AiReplayMate")
                            Text(
                                text = "基于当前微信上下文生成候选回复",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                TabRow(selectedTabIndex = selectedTab.ordinal) {
                    HomeTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.label) }
                        )
                    }
                }

                when (selectedTab) {
                    HomeTab.MAIN -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .verticalScroll(mainTabScrollState),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            HeroCard(
                                currentStage = sessionState.currentState,
                                stageDetail = sessionState.statusNote ?: sessionState.currentState.detail,
                                isRunning = sessionState.isRunning,
                                conversationTitle = debugState.conversationTitle,
                                serviceConnected = debugState.serviceConnected,
                                onRun = {
                                    if (!sessionState.isRunning) {
                                        scope.launch {
                                            sessionManager.run(debugState, replyStyleProfile.asDefaultReply())
                                        }
                                    }
                                },
                                onReset = {
                                    if (!sessionState.isRunning) {
                                        sessionManager.reset()
                                    }
                                }
                            )

                            SessionStageSection(
                                currentStage = sessionState.currentState,
                                progressStage = sessionState.progressState
                            )

                            ConversationPreviewSection(
                                conversationTitle = previewConversationTitle,
                                messages = previewMessages
                            )

                            ReplyStyleSection(
                                profile = replyStyleProfile,
                                onProfileChange = { nextProfile ->
                                    replyStyleProfile = nextProfile
                                    ReplyStyleSettingsStore.save(context, nextProfile)
                                }
                            )

                            ReplyDraftSection(
                                replyDraft = sessionState.replyDraft,
                                onReplyDraftChange = sessionManager::updateReplyDraft,
                                onTryRealAutofill = {
                                    val result = AccessibilityActionBridge.tryAutofill(sessionState.replyDraft)
                                    sessionManager.noteRealAutofillResult(result.message)
                                },
                                canTryRealAutofill = sessionState.replyDraft.isNotBlank()
                            )

                            ActivityLogSection(entries = sessionState.activityLog)

                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    HomeTab.SETTINGS -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .verticalScroll(permissionsTabScrollState),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LlmSettingsSection(
                                settings = appSettings,
                                validation = AppSettingsValidator.validate(appSettings),
                                isTesting = testingLlm,
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

                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    HomeTab.PERMISSIONS -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .verticalScroll(permissionsTabScrollState),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            PermissionStatusSection(
                                permissionSnapshot = permissionSnapshot,
                                overlayServiceState = overlayServiceState,
                                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                                onOpenOverlaySettings = onOpenOverlaySettings,
                                onStartOverlayService = onStartOverlayService,
                                onStopOverlayService = onStopOverlayService
                            )

                            OcrFallbackSection(
                                debugState = ocrDebugState,
                                capturePermissionState = ocrCapturePermissionState,
                                screenCaptureState = ocrScreenCaptureState,
                                isTestingScreenCapture = testingScreenCapture,
                                isTestingOcrRecognition = testingOcrRecognition,
                                onRequestCapturePermission = {
                                    screenCapturePermissionLauncher.launch(
                                        mediaProjectionManager.createScreenCaptureIntent()
                                    )
                                },
                                onTestScreenCapture = {
                                    scope.launch {
                                        testingScreenCapture = true
                                        val result = AndroidScreenCaptureProvider(context.applicationContext)
                                            .captureOnce(ocrCapturePermissionState)
                                        testingScreenCapture = false
                                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onTestOcrRecognition = {
                                    scope.launch {
                                        testingOcrRecognition = true
                                        val result = MlKitChineseOcrEngine(context.applicationContext)
                                            .recognizeChatMessages(
                                                targetApp = "wechat",
                                                reason = "首页手动测试 OCR 识别"
                                            )
                                        testingOcrRecognition = false
                                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }

                    HomeTab.DEBUG -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .verticalScroll(debugTabScrollState),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OverlayDiagnosticsSection(debugState = overlayDiagnosticsState)

                            LlmDiagnosticsSection(debugState = llmDebugState)

                            RealAccessibilitySection(
                                debugState = debugState,
                                ocrDebugState = ocrDebugState
                            )

                            DiagnosticLogSection(logState = diagnosticLogState)

                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    currentStage: SessionState,
    stageDetail: String,
    isRunning: Boolean,
    conversationTitle: String?,
    serviceConnected: Boolean,
    onRun: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12332F)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0x33FFFFFF)
            ) {
                Text(
                    text = "正式版",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Text(
                text = conversationTitle ?: "准备生成微信回复",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (serviceConnected) {
                    "基于当前聊天上下文生成候选回复，用户确认后才会填入，不会自动发送。"
                } else {
                    "进入微信单聊页并保持无障碍服务可用后，即可基于真实上下文生成候选回复。"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFDCEEEB)
            )

            if (isRunning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFF5C87A),
                    trackColor = Color(0x33FFFFFF)
                )
            }

            Text(
                text = "当前阶段：${currentStage.title}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = stageDetail,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFBFE4DE)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onRun,
                    enabled = !isRunning,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF2B35D),
                        contentColor = Color(0xFF2D1700)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (currentStage == SessionState.IDLE) {
                            "开始生成"
                        } else {
                            "重新生成"
                        }
                    )
                }

                OutlinedButton(
                    onClick = onReset,
                    enabled = !isRunning,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("清空状态")
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusSection(
    permissionSnapshot: PermissionSnapshot,
    overlayServiceState: OverlayServiceState,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onStartOverlayService: () -> Unit,
    onStopOverlayService: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "权限状态",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PermissionCard(
                title = "无障碍服务",
                enabled = permissionSnapshot.accessibilityEnabled,
                description = if (permissionSnapshot.accessibilityEnabled) {
                    "已开启，可从真实微信聊天页触发生成。"
                } else {
                    "未开启时无法读取聊天上下文，也不能触发真实链路。"
                },
                buttonText = "去开启",
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth()
            )

            PermissionCard(
                title = "悬浮窗权限",
                enabled = permissionSnapshot.overlayEnabled,
                description = overlayPermissionDescription(
                    permissionSnapshot = permissionSnapshot,
                    overlayServiceState = overlayServiceState
                ),
                statusRows = listOf(
                    "服务状态" to overlayServiceState.status.label,
                    "气泡视图" to if (overlayServiceState.bubbleVisible) "已挂载" else "未挂载",
                    "更新时间" to formatTimestamp(overlayServiceState.updatedAtMillis)
                ),
                buttonText = if (permissionSnapshot.overlayEnabled) "启动/刷新气泡" else "去设置",
                onClick = if (permissionSnapshot.overlayEnabled) {
                    onStartOverlayService
                } else {
                    onOpenOverlaySettings
                },
                secondaryButtonText = if (permissionSnapshot.overlayEnabled) "停止气泡" else null,
                onSecondaryClick = onStopOverlayService,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun overlayPermissionDescription(
    permissionSnapshot: PermissionSnapshot,
    overlayServiceState: OverlayServiceState
): String {
    if (!permissionSnapshot.overlayEnabled) {
        return "需要授权后才能在微信上方显示触发入口。"
    }

    return when {
        overlayServiceState.bubbleVisible -> "已授权，AI 小气泡正在显示。若微信里看不到，可点“启动/刷新气泡”重新挂载。"
        overlayServiceState.updatedAtMillis > 0L -> "${overlayServiceState.message}。若没有小气泡，点“启动/刷新气泡”恢复。"
        else -> "已授权，但还未启动 AI 小气泡。点“启动/刷新气泡”后切回微信单聊页使用。"
    }
}

@Composable
private fun PermissionCard(
    title: String,
    enabled: Boolean,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
    statusRows: List<Pair<String, String>> = emptyList(),
    secondaryButtonText: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ) {
                Text(
                    text = if (enabled) "已开启" else "待处理",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            statusRows.forEach { (label, value) ->
                StatusRow(label = label, value = value)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onClick,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(buttonText)
                }

                if (secondaryButtonText != null && onSecondaryClick != null) {
                    OutlinedButton(
                        onClick = onSecondaryClick,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(secondaryButtonText)
                    }
                }
            }
        }
    }
}

@Composable
private fun LlmSettingsSection(
    settings: AppSettings,
    validation: AppSettingsValidation,
    isTesting: Boolean,
    onSettingsChange: (AppSettings) -> Unit,
    onTestConnection: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "LLM 设置",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = llmSettingsHint(settings, validation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = settings.apiKey,
                    onValueChange = { onSettingsChange(settings.copy(apiKey = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                OutlinedTextField(
                    value = settings.baseUrl,
                    onValueChange = { onSettingsChange(settings.copy(baseUrl = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    label = { Text("Base URL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                OutlinedTextField(
                    value = settings.model,
                    onValueChange = { onSettingsChange(settings.copy(model = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    label = { Text("Model") }
                )

                validation.errors.forEach { error ->
                    Text(
                        text = "错误：$error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                validation.warnings.forEach { warning ->
                    Text(
                        text = "提醒：$warning",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Button(
                    onClick = onTestConnection,
                    enabled = validation.canRequest && !isTesting,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isTesting) "测试中..." else "测试连接")
                }
            }
        }
    }
}

private fun llmSettingsHint(
    settings: AppSettings,
    validation: AppSettingsValidation
): String {
    return when {
        validation.errors.isNotEmpty() -> "请先修正配置错误；未配置可用 LLM 时，微信悬浮面板会使用本地兜底候选。"
        settings.apiKey.isBlank() -> "未配置 API Key 时，微信悬浮面板会使用本地兜底候选。"
        else -> "配置看起来可用；可先点测试连接，再到微信单聊页使用悬浮面板。"
    }
}

@Composable
private fun ReplyStyleSection(
    profile: ReplyStyleProfile,
    onProfileChange: (ReplyStyleProfile) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "LLM 回复风格",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "默认回复只使用角色配置；话术宝典和润色表达是长按气泡里的单次功能，不会覆盖短按默认回复。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                StatusRow(label = "默认回复", value = profile.asDefaultReply().displayLabel)

                Text(
                    text = "角色",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                ChoiceButtonGrid(
                    items = ReplyPersona.entries.map { it.id to it.label },
                    selectedId = profile.persona.id,
                    onSelect = { personaId ->
                        onProfileChange(profile.copy(persona = ReplyPersona.fromId(personaId)))
                    }
                )

                Text(
                    text = "话术宝典",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                ChoiceButtonGrid(
                    items = ReplyStyleCatalog.scenes.map { it.sceneId to "${it.categoryLabel} · ${it.sceneLabel}" },
                    selectedId = profile.playbookScene?.sceneId ?: ReplyStyleCatalog.defaultScene.sceneId,
                    onSelect = { sceneId ->
                        onProfileChange(
                            profile.copy(
                                playbookScene = ReplyStyleCatalog.sceneFromId(sceneId)
                            )
                        )
                    }
                )

                Text(
                    text = "润色表达",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                ChoiceButtonGrid(
                    items = PolishGoal.entries.map { it.id to it.label },
                    selectedId = profile.polishGoal.id,
                    onSelect = { goalId ->
                        onProfileChange(
                            profile.copy(
                                polishGoal = PolishGoal.fromId(goalId)
                            )
                        )
                    }
                )

                Text(
                    text = styleExample(profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChoiceButtonGrid(
    items: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { (id, label) ->
                    StyleChoiceButton(
                        label = label,
                        selected = id == selectedId,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(id) }
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StyleChoiceButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(label)
        }
    }
}

private fun styleExample(profile: ReplyStyleProfile): String {
    return "短按气泡：按“${profile.persona.label}”生成默认回复；长按气泡：话术宝典按“${profile.playbookScene?.sceneLabel ?: "话术宝典"}”单次出文案，润色会读取输入框草稿并回写候选。"
}

private fun ReplyStyleProfile.asDefaultReply(): ReplyStyleProfile {
    return copy(mode = ReplyStyleMode.QUICK_REPLY)
}

@Composable
private fun OverlayDiagnosticsSection(debugState: OverlayDiagnosticsState) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "悬浮窗诊断",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "记录上一次点击微信 AI 气泡后的真实链路，方便定位候选面板不出现或填入失败的原因。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                StatusRow(label = "阶段", value = debugState.phase.label)
                StatusRow(label = "状态", value = debugState.status)
                StatusRow(
                    label = "上下文",
                    value = "A11y ${debugState.accessibilityMessageCount} / OCR ${debugState.ocrMessageCount} / 合并 ${debugState.mergedMessageCount}"
                )
                StatusRow(
                    label = "候选数",
                    value = debugState.candidateCount.toString()
                )
                StatusRow(
                    label = "候选来源",
                    value = debugState.candidateSource
                )
                StatusRow(
                    label = "使用 OCR",
                    value = if (debugState.usedOcr) "是" else "否"
                )
                StatusRow(
                    label = "本地兜底",
                    value = if (debugState.usedLocalFallback) "是" else "否"
                )
                StatusRow(
                    label = "失败原因",
                    value = debugState.lastFailure ?: "暂无"
                )
                StatusRow(
                    label = "更新时间",
                    value = formatTimestamp(debugState.updatedAtMillis)
                )

                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(
                            AnnotatedString(buildOverlayDebugSnapshot(debugState))
                        )
                        Toast.makeText(context, "已复制悬浮窗诊断", Toast.LENGTH_SHORT).show()
                    },
                    enabled = debugState.updatedAtMillis > 0L,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("复制悬浮窗诊断")
                }

                if (debugState.steps.isNotEmpty()) {
                    Text(
                        text = "最近步骤",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    debugState.steps.takeLast(8).forEach { step ->
                        Text(
                            text = "- $step",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticLogSection(logState: DiagnosticLogState) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "诊断日志",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "保存最近的 LLM、OCR、悬浮窗摘要日志；不保存截图、不保存 API Key。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusRow(label = "记录数", value = logState.entries.size.toString())
                StatusRow(label = "更新时间", value = formatTimestamp(logState.updatedAtMillis))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(
                                AnnotatedString(DiagnosticLogStore.buildSnapshot())
                            )
                            Toast.makeText(context, "已复制诊断日志", Toast.LENGTH_SHORT).show()
                        },
                        enabled = logState.entries.isNotEmpty(),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("复制日志")
                    }

                    OutlinedButton(
                        onClick = {
                            DiagnosticLogStore.clear()
                            Toast.makeText(context, "已清空诊断日志", Toast.LENGTH_SHORT).show()
                        },
                        enabled = logState.entries.isNotEmpty(),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("清空")
                    }
                }

                if (logState.entries.isNotEmpty()) {
                    Text(
                        text = "最近记录",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    logState.entries.take(5).forEach { entry ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "${formatTimestamp(entry.timestampMillis)} ${entry.kind.label}/${entry.title}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "摘要：${entry.summary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "建议：${entry.hint}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "元数据：${entry.metadata}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LlmDiagnosticsSection(debugState: LlmDebugState) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "LLM 诊断",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusRow(label = "阶段", value = debugState.phase.label)
                StatusRow(label = "分类", value = debugState.failureCategory.label)
                StatusRow(label = "接口", value = debugState.baseUrl.ifBlank { "暂无" })
                StatusRow(label = "模型", value = debugState.model.ifBlank { "暂无" })
                StatusRow(
                    label = "HTTP",
                    value = debugState.httpStatus?.toString() ?: "暂无"
                )
                StatusRow(
                    label = "候选数",
                    value = if (debugState.candidateCount > 0) {
                        debugState.candidateCount.toString()
                    } else {
                        "暂无"
                    }
                )
                StatusRow(
                    label = "错误",
                    value = debugState.errorSummary ?: "暂无"
                )
                StatusRow(
                    label = "建议",
                    value = debugState.recoveryHint
                )
                StatusRow(
                    label = "返回预览",
                    value = debugState.responsePreview ?: "暂无"
                )
                StatusRow(
                    label = "更新时间",
                    value = formatTimestamp(debugState.updatedAtMillis)
                )

                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(
                            AnnotatedString(buildLlmDebugSnapshot(debugState))
                        )
                        Toast.makeText(context, "已复制 LLM 诊断", Toast.LENGTH_SHORT).show()
                    },
                    enabled = debugState.updatedAtMillis > 0L || debugState.history.isNotEmpty(),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("复制 LLM 诊断")
                }

                if (debugState.history.isNotEmpty()) {
                    Text(
                        text = "最近请求",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    debugState.history.take(5).forEach { entry ->
                        Text(
                            text = "${formatTimestamp(entry.timestampMillis)} ${entry.phase.label}/${entry.category.label}: ${entry.summary}；建议：${entry.recoveryHint}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RealAccessibilitySection(
    debugState: AccessibilityDebugState,
    ocrDebugState: OcrDebugState
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "真实无障碍状态",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusRow(
                    label = "服务连接",
                    value = if (debugState.serviceConnected) "已连接" else "未连接"
                )
                StatusRow(
                    label = "最后事件",
                    value = debugState.lastEventName
                )
                StatusRow(
                    label = "当前包名",
                    value = debugState.packageName.ifEmpty { "暂无" }
                )
                StatusRow(
                    label = "当前页面类",
                    value = debugState.className.ifEmpty { "暂无" }
                )
                StatusRow(
                    label = "可编辑节点数",
                    value = debugState.editableNodeCount.toString()
                )
                StatusRow(
                    label = "微信包识别",
                    value = if (debugState.isWechatPackage) "是" else "否"
                )
                StatusRow(
                    label = "聊天页初判",
                    value = if (debugState.looksLikeChatPage) "像微信聊天页" else "暂未判定为聊天页"
                )
                StatusRow(
                    label = "标题识别",
                    value = debugState.conversationTitle ?: "暂无"
                )
                StatusRow(
                    label = "输入框定位",
                    value = if (debugState.inputNodeFound) {
                        debugState.inputNodeHint ?: "已找到输入框"
                    } else {
                        "未找到"
                    }
                )
                StatusRow(
                    label = "判定说明",
                    value = debugState.chatDetectionReason.ifEmpty { "暂无" }
                )
                StatusRow(
                    label = "真实填入",
                    value = debugState.lastAutofillStatus
                )
                StatusRow(
                    label = "填入分类",
                    value = debugState.lastAutofillCategory.label
                )
                StatusRow(
                    label = "填入文本预览",
                    value = debugState.lastAutofillPreview ?: "暂无"
                )
                StatusRow(
                    label = "最后更新时间",
                    value = formatTimestamp(debugState.updatedAtMillis)
                )

                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(
                            AnnotatedString(
                                buildAccessibilityDebugSnapshot(
                                    debugState = debugState,
                                    ocrDebugState = ocrDebugState
                                )
                            )
                        )
                        Toast.makeText(context, "已复制调试样本", Toast.LENGTH_SHORT).show()
                    },
                    enabled = debugState.updatedAtMillis > 0L,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("复制调试样本")
                }

                if (debugState.lastAutofillSteps.isNotEmpty()) {
                    Text(
                        text = "填入步骤",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    debugState.lastAutofillSteps.forEach { step ->
                        Text(
                            text = "- $step",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (debugState.extractedMessageDebugPreviews.isNotEmpty()) {
                    Text(
                        text = "消息提取调试",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "格式：序号 角色 置信度 [left,top,right,bottom] 文本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    debugState.extractedMessageDebugPreviews.takeLast(8).forEach { message ->
                        Text(
                            text = "- $message",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (debugState.visibleTextSample.isNotEmpty()) {
                    Text(
                        text = "可见文本采样",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    debugState.visibleTextSample.take(5).forEach { sample ->
                        Text(
                            text = "- $sample",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "开启无障碍服务后，切到微信或其他 App，这里会开始显示真实事件和文本采样。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun OcrFallbackSection(
    debugState: OcrDebugState,
    capturePermissionState: OcrCapturePermissionState,
    screenCaptureState: OcrScreenCaptureState,
    isTestingScreenCapture: Boolean,
    isTestingOcrRecognition: Boolean,
    onRequestCapturePermission: () -> Unit,
    onTestScreenCapture: () -> Unit,
    onTestOcrRecognition: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "OCR 兜底",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (capturePermissionState.isReady) {
                        "屏幕截图授权已准备好；下一步接入 OCR 引擎后，就能从截图中提取聊天文本。"
                    } else {
                        "当前已接入 OCR 兜底入口和诊断；先授权屏幕截图，后续再接真实识别引擎。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                StatusRow(
                    label = "截图授权",
                    value = capturePermissionState.status.label
                )
                StatusRow(
                    label = "授权时间",
                    value = formatTimestamp(capturePermissionState.updatedAtMillis)
                )
                StatusRow(
                    label = "OCR 引擎",
                    value = if (debugState.engineConfigured) "已配置" else "未配置"
                )
                StatusRow(
                    label = "截图数据流",
                    value = screenCaptureState.status.label
                )
                StatusRow(
                    label = "截图尺寸",
                    value = screenCaptureState.sizeLabel
                )
                StatusRow(
                    label = "截图状态",
                    value = screenCaptureState.message
                )
                StatusRow(
                    label = "最近分类",
                    value = debugState.lastCategory.label
                )
                StatusRow(
                    label = "最近状态",
                    value = debugState.lastStatus
                )
                StatusRow(
                    label = "触发原因",
                    value = debugState.lastReason.ifBlank { "暂无" }
                )
                StatusRow(
                    label = "目标应用",
                    value = debugState.targetApp.ifBlank { "暂无" }
                )
                StatusRow(
                    label = "识别消息数",
                    value = debugState.extractedMessages.size.toString()
                )
                StatusRow(
                    label = "更新时间",
                    value = formatTimestamp(debugState.updatedAtMillis)
                )

                Button(
                    onClick = onRequestCapturePermission,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (capturePermissionState.isReady) "重新授权屏幕截图" else "授权屏幕截图")
                }

                OutlinedButton(
                    onClick = onTestScreenCapture,
                    enabled = capturePermissionState.isReady &&
                        !isTestingScreenCapture &&
                        !isTestingOcrRecognition,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isTestingScreenCapture) "取图中..." else "测试截图数据流")
                }

                OutlinedButton(
                    onClick = onTestOcrRecognition,
                    enabled = capturePermissionState.isReady &&
                        !isTestingScreenCapture &&
                        !isTestingOcrRecognition,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isTestingOcrRecognition) "识别中..." else "测试 OCR 识别")
                }

                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(
                            AnnotatedString(
                                buildOcrDebugSnapshot(
                                    debugState = debugState,
                                    capturePermissionState = capturePermissionState,
                                    screenCaptureState = screenCaptureState
                                )
                            )
                        )
                        Toast.makeText(context, "已复制 OCR 诊断", Toast.LENGTH_SHORT).show()
                    },
                    enabled = debugState.updatedAtMillis > 0L ||
                        capturePermissionState.updatedAtMillis > 0L ||
                        screenCaptureState.updatedAtMillis > 0L,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("复制 OCR 诊断")
                }

                if (debugState.steps.isNotEmpty()) {
                    Text(
                        text = "OCR 步骤",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    debugState.steps.forEach { step ->
                        Text(
                            text = "- $step",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (screenCaptureState.steps.isNotEmpty()) {
                    Text(
                        text = "截图步骤",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    screenCaptureState.steps.forEach { step ->
                        Text(
                            text = "- $step",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (debugState.extractedMessagePreviews.isNotEmpty()) {
                    Text(
                        text = "OCR 消息",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    debugState.extractedMessagePreviews.takeLast(8).forEach { message ->
                        Text(
                            text = "- $message",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.width(96.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SessionStageSection(
    currentStage: SessionState,
    progressStage: SessionState
) {
    val steps = listOf(
        SessionState.VALIDATING_TARGET,
        SessionState.COLLECTING_ACCESSIBILITY,
        SessionState.COLLECTING_OCR,
        SessionState.BUILDING_CONTEXT,
        SessionState.REQUESTING_LLM,
        SessionState.CANDIDATE_READY,
        SessionState.AUTOFILLING,
        SessionState.DONE
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "主链路状态",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                steps.forEachIndexed { index, step ->
                    val stateText = when {
                        step == currentStage -> "进行中"
                        progressStage.progress >= step.progress -> "已完成"
                        else -> "待执行"
                    }

                    val toneColor = when (stateText) {
                        "进行中" -> MaterialTheme.colorScheme.secondary
                        "已完成" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.outline
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(toneColor)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = step.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = step.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = stateText,
                            style = MaterialTheme.typography.labelLarge,
                            color = toneColor
                        )
                    }

                    if (index != steps.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationPreviewSection(
    conversationTitle: String?,
    messages: List<DemoMessage>
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "聊天预览",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F2E8))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = conversationTitle ?: "当前聊天上下文",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (messages.isEmpty()) {
                        "进入微信单聊页后，这里会展示最近提取到的聊天上下文。"
                    } else {
                        "当前会把这段上下文送去生成 3 条候选回复。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (messages.isEmpty()) {
                    Text(
                        text = "还没有提取到聊天消息。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    messages.forEach { message ->
                        MessageBubble(message = message)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: DemoMessage) {
    val isMine = message.author == DemoAuthor.ME
    val bubbleColor = when (message.author) {
        DemoAuthor.ME -> Color(0xFFD2F3E5)
        DemoAuthor.FRIEND -> Color.White
        DemoAuthor.SYSTEM -> Color(0xFFE9E2D7)
    }
    val textColor = Color(0xFF1E1B16)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = bubbleColor)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                color = textColor,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ReplyDraftSection(
    replyDraft: String,
    onReplyDraftChange: (String) -> Unit,
    onTryRealAutofill: () -> Unit,
    canTryRealAutofill: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "模拟输入框",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            value = replyDraft,
            onValueChange = onReplyDraftChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            minLines = 4,
            label = { Text("用户最终可编辑后再发送") },
            placeholder = { Text("选择候选后，文本会自动填到这里") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onTryRealAutofill,
                enabled = canTryRealAutofill,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("尝试真实填入")
            }

            OutlinedButton(
                onClick = { onReplyDraftChange("") },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("清空草稿")
            }
        }

        Text(
            text = "这个按钮会调用无障碍服务，尝试把当前草稿直接写进真实输入框。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActivityLogSection(entries: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "运行日志",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (entries.isEmpty()) {
                    Text(
                        text = "还没有开始生成。点击上面的按钮后，这里会按阶段记录主链路进度。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    entries.takeLast(6).forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = item,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
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

private fun buildAccessibilityDebugSnapshot(
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

private fun buildOcrDebugSnapshot(
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

private fun buildLlmDebugSnapshot(debugState: LlmDebugState): String {
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

private fun buildOverlayDebugSnapshot(debugState: OverlayDiagnosticsState): String {
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

private fun List<ChatMessage>.toPreviewMessages(): List<DemoMessage> {
    return takeLast(8).map { message ->
        DemoMessage(
            author = when (message.role) {
                ChatRole.ME -> DemoAuthor.ME
                ChatRole.FRIEND -> DemoAuthor.FRIEND
                ChatRole.SYSTEM -> DemoAuthor.SYSTEM
                ChatRole.UNKNOWN -> DemoAuthor.FRIEND
            },
            content = message.content
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "暂无"
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
