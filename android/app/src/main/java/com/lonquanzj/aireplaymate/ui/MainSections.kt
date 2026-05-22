package com.lonquanzj.aireplaymate

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.Secure
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
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
import com.lonquanzj.aireplaymate.accessibility.ChatMessage
import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.accessibility.ReplyAccessibilityService
import com.lonquanzj.aireplaymate.context.ChatContext
import com.lonquanzj.aireplaymate.context.ConversationType
import com.lonquanzj.aireplaymate.context.DefaultContextBuilder
import com.lonquanzj.aireplaymate.diagnostics.DiagnosticLogState
import com.lonquanzj.aireplaymate.diagnostics.DiagnosticLogStore
import com.lonquanzj.aireplaymate.llm.LlmDebugState
import com.lonquanzj.aireplaymate.llm.LlmDebugStore
import com.lonquanzj.aireplaymate.llm.OpenAiCompatibleLlmGateway
import com.lonquanzj.aireplaymate.ocr.AndroidScreenCaptureProvider
import com.lonquanzj.aireplaymate.ocr.OcrDebugState
import com.lonquanzj.aireplaymate.ocr.OcrDebugStore
import com.lonquanzj.aireplaymate.ocr.OcrScreenCaptureState
import com.lonquanzj.aireplaymate.ocr.OcrScreenCaptureStore
import com.lonquanzj.aireplaymate.ocr.MlKitChineseOcrEngine
import com.lonquanzj.aireplaymate.overlay.OverlayButtonService
import com.lonquanzj.aireplaymate.overlay.OverlayDiagnosticsState
import com.lonquanzj.aireplaymate.overlay.OverlayDiagnosticsStore
import com.lonquanzj.aireplaymate.overlay.OverlayServiceState
import com.lonquanzj.aireplaymate.overlay.OverlayServiceStateStore
import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.ContextSendPolicy
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
import kotlin.math.roundToInt
import kotlinx.coroutines.launch


@Composable
internal fun MainTabContent(
    permissionSnapshot: PermissionSnapshot,
    appSettings: AppSettings,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onStartOverlayService: () -> Unit,
    onStopOverlayService: () -> Unit,
    onShowAboutDialog: () -> Unit
) {
    val overlayServiceState by OverlayServiceStateStore.state.collectAsState()
    val diagnosticLogState by DiagnosticLogStore.state.collectAsState()
    val debugState by AccessibilityDebugStore.state.collectAsState()
    val previewContextState by ReplyContextPreviewStore.state.collectAsState()

    val previewMessages = remember(
        previewContextState.messages,
        debugState.extractedMessages
    ) {
        when {
            previewContextState.messages.isNotEmpty() -> previewContextState.messages
            else -> debugState.extractedMessages
        }
    }
    val previewConversationTitle = previewContextState.conversationTitle ?: debugState.conversationTitle

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DailyHomeHeader(
            permissionSnapshot = permissionSnapshot,
            overlayServiceState = overlayServiceState,
            llmValidation = AppSettingsValidator.validate(appSettings),
            latestLogTitle = diagnosticLogState.entries.firstOrNull()?.title
        )

        PermissionStatusSection(
            permissionSnapshot = permissionSnapshot,
            overlayServiceState = overlayServiceState,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            onOpenOverlaySettings = onOpenOverlaySettings,
            onStartOverlayService = onStartOverlayService,
            onStopOverlayService = onStopOverlayService
        )

        ConversationPreviewSection(
            conversationTitle = previewConversationTitle,
            messages = previewMessages
        )

        DiagnosticLogSection(logState = diagnosticLogState)

        AboutEntry(onClick = onShowAboutDialog)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
internal fun LlmTabContent(
    appSettings: AppSettings,
    testingLlm: Boolean,
    onSettingsChange: (AppSettings) -> Unit,
    onTestConnection: () -> Unit
) {
    val llmDebugState by LlmDebugStore.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LlmSettingsSection(
            settings = appSettings,
            validation = AppSettingsValidator.validate(appSettings),
            isTesting = testingLlm,
            onSettingsChange = onSettingsChange,
            onTestConnection = onTestConnection
        )

        LlmDiagnosticsSection(debugState = llmDebugState)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
internal fun StyleTabContent(
    appSettings: AppSettings,
    replyStyleProfile: ReplyStyleProfile,
    replyStyleCatalog: ReplyStyleCatalogState,
    onProfileChange: (ReplyStyleProfile) -> Unit,
    onCatalogChange: (ReplyStyleCatalogState) -> Unit,
    onResetBuiltinCatalog: () -> Unit
) {
    val debugState by AccessibilityDebugStore.state.collectAsState()
    val previewContextState by ReplyContextPreviewStore.state.collectAsState()
    val promptPreviewContext = remember(
        previewContextState.messages,
        debugState.extractedMessages
    ) {
        when {
            previewContextState.messages.isNotEmpty() -> realPromptPreviewContext(previewContextState.messages)
            else -> DefaultContextBuilder.build(
                accessibilityMessages = debugState.extractedMessages,
                targetApp = "wechat",
                conversationType = ConversationType.SINGLE_CHAT
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ReplyStyleSection(
            appSettings = appSettings,
            promptPreviewContext = promptPreviewContext,
            profile = replyStyleProfile,
            catalog = replyStyleCatalog,
            onProfileChange = onProfileChange,
            onCatalogChange = onCatalogChange,
            onResetBuiltinCatalog = onResetBuiltinCatalog,
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
internal fun AdvancedTabContent() {
    val debugState by AccessibilityDebugStore.state.collectAsState()
    val ocrDebugState by OcrDebugStore.state.collectAsState()
    val ocrScreenCaptureState by OcrScreenCaptureStore.state.collectAsState()
    val overlayDiagnosticsState by OverlayDiagnosticsStore.state.collectAsState()
    val diagnosticLogState by DiagnosticLogStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var testingScreenCapture by remember { mutableStateOf(false) }
    var testingOcrRecognition by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OcrFallbackSection(
            debugState = ocrDebugState,
            screenCaptureState = ocrScreenCaptureState,
            isTestingScreenCapture = testingScreenCapture,
            isTestingOcrRecognition = testingOcrRecognition,
            onTestScreenCapture = {
                scope.launch {
                    testingScreenCapture = true
                    val result = AndroidScreenCaptureProvider(context.applicationContext)
                        .captureOnce()
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
                            reason = "高级调试手动测试 OCR 识别"
                        )
                    testingOcrRecognition = false
                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                }
            }
        )

        OverlayDiagnosticsSection(debugState = overlayDiagnosticsState)

        RealAccessibilitySection(
            debugState = debugState,
            ocrDebugState = ocrDebugState
        )

        DiagnosticLogSection(logState = diagnosticLogState)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
internal fun AboutEntry(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "项目说明与使用边界",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "查看",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
internal fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("关于 AiChat")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("AiReplayMate 基于当前微信上下文生成候选回复。")
                Text("当前版本面向微信单聊辅助回复：生成候选、由你选择、只填入输入框，不自动发送。")
                Text("如果 Accessibility 上下文不足，会按设置尝试 OCR 兜底；诊断信息默认只保留摘要，避免记录完整聊天原文。")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}

@Composable
private fun DailyHomeHeader(
    permissionSnapshot: PermissionSnapshot,
    overlayServiceState: OverlayServiceState,
    llmValidation: AppSettingsValidation,
    latestLogTitle: String?
) {
    val permissionReady = permissionSnapshot.accessibilityEnabled && permissionSnapshot.overlayEnabled
    val llmReady = llmValidation.canRequest

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "日常入口",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "先确认权限、气泡和模型配置，再去微信单聊里点气泡生成候选。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DailyStatusItem(
                    label = "权限",
                    value = if (permissionReady) "已就绪" else "待处理",
                    modifier = Modifier.weight(1f)
                )
                DailyStatusItem(
                    label = "气泡",
                    value = overlayServiceState.status.label,
                    modifier = Modifier.weight(1f)
                )
                DailyStatusItem(
                    label = "LLM",
                    value = if (llmReady) "可测试" else "需配置",
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "最近诊断：${latestLogTitle ?: "暂无"}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DailyStatusItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
            )
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

                LlmParameterSlider(
                    label = "Temperature",
                    valueText = "%.1f".format(settings.temperature),
                    value = settings.temperature.coerceIn(0f, 2f),
                    valueRange = 0f..2f,
                    steps = 19,
                    onValueChange = { value ->
                        onSettingsChange(settings.copy(temperature = (value * 10).roundToInt() / 10f))
                    }
                )

                LlmParameterSlider(
                    label = "Max tokens",
                    valueText = settings.maxTokens.toString(),
                    value = settings.maxTokens.coerceIn(120, 2000).toFloat(),
                    valueRange = 120f..2000f,
                    onValueChange = { value ->
                        val maxTokens = (value / 10).roundToInt() * 10
                        onSettingsChange(settings.copy(maxTokens = maxTokens.coerceIn(120, 2000)))
                    }
                )

                ContextSendPolicySelector(
                    selected = settings.contextSendPolicy,
                    onSelected = { onSettingsChange(settings.copy(contextSendPolicy = it)) }
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

@Composable
private fun LlmParameterSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    steps: Int = 0
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ContextSendPolicySelector(
    selected: ContextSendPolicy,
    onSelected: (ContextSendPolicy) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "发送给 LLM 的上下文",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ContextSendPolicyButton(
                text = "全部上下文",
                selected = selected == ContextSendPolicy.FULL_CONTEXT,
                onClick = { onSelected(ContextSendPolicy.FULL_CONTEXT) },
                modifier = Modifier.weight(1f)
            )
            ContextSendPolicyButton(
                text = "仅最近对方消息",
                selected = selected == ContextSendPolicy.LATEST_FRIEND_MESSAGE,
                onClick = { onSelected(ContextSendPolicy.LATEST_FRIEND_MESSAGE) },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = when (selected) {
                ContextSendPolicy.FULL_CONTEXT -> "生成时会发送最多 20 条最近聊天记录。"
                ContextSendPolicy.LATEST_FRIEND_MESSAGE -> "生成时只发送最近一条对方消息。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ContextSendPolicyButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    appSettings: AppSettings,
    promptPreviewContext: ChatContext,
    profile: ReplyStyleProfile,
    catalog: ReplyStyleCatalogState,
    onProfileChange: (ReplyStyleProfile) -> Unit,
    onCatalogChange: (ReplyStyleCatalogState) -> Unit,
    onResetBuiltinCatalog: () -> Unit
) {
    var previewRequest by remember { mutableStateOf<LlmRequest?>(null) }
    var editingItem by remember { mutableStateOf<StyleEditTarget?>(null) }
    var isPersonaEditMode by remember { mutableStateOf(false) }
    var isPlaybookEditMode by remember { mutableStateOf(false) }
    var isPolishEditMode by remember { mutableStateOf(false) }

    previewRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { previewRequest = null },
            confirmButton = {
                TextButton(onClick = { previewRequest = null }) {
                    Text("关闭")
                }
            },
            title = { Text("Prompt 预览") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("System Prompt", fontWeight = FontWeight.SemiBold)
                    Text(request.systemPrompt, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Text("User Prompt", fontWeight = FontWeight.SemiBold)
                    Text(request.userPrompt, style = MaterialTheme.typography.bodySmall)
                }
            }
        )
    }

    editingItem?.let { target ->
        StyleItemEditorDialog(
            target = target,
            onDismiss = { editingItem = null },
            onSave = { savedTarget ->
                onCatalogChange(catalog.saveStyleTarget(savedTarget))
                onProfileChange(profile.withStyleTarget(savedTarget))
                editingItem = null
            },
            onDelete = { deleteTarget ->
                onCatalogChange(catalog.deleteStyleTarget(deleteTarget))
                onProfileChange(profile.withResolvedCatalog(catalog.deleteStyleTarget(deleteTarget)))
                editingItem = null
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "LLM 回复风格",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "选择默认角色、话术和润色目标；每一类都可以编辑提示词，也可以新增自己的条目。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                StatusRow(label = "默认回复", value = profile.asDefaultReply().displayLabel)

                HorizontalDivider()
                StyleCategoryHeader(
                    title = "角色",
                    isEditMode = isPersonaEditMode,
                    onEditClick = { isPersonaEditMode = !isPersonaEditMode },
                    onPreviewClick = {
                        previewRequest = DefaultPromptBuilder.build(
                            context = promptPreviewContext,
                            settings = appSettings,
                            styleProfile = profile.asDefaultReply().withResolvedCatalog(catalog)
                        )
                    }
                )
                ChoiceButtonGrid(
                    items = catalog.personas.map { it.id to it.label },
                    selectedId = profile.personaConfig.id,
                    isEditMode = isPersonaEditMode,
                    onAdd = {
                        editingItem = StyleEditTarget.Persona(newCustomPersonaConfig())
                    },
                    onSelect = { personaId ->
                        val persona = catalog.resolvePersona(personaId)
                        if (isPersonaEditMode) {
                            editingItem = StyleEditTarget.Persona(persona)
                        } else {
                            onProfileChange(
                                profile.copy(personaConfig = persona)
                                    .withResolvedCatalog(catalog)
                            )
                        }
                    }
                )

                HorizontalDivider()
                StyleCategoryHeader(
                    title = "话术宝典",
                    isEditMode = isPlaybookEditMode,
                    onEditClick = { isPlaybookEditMode = !isPlaybookEditMode },
                    onPreviewClick = {
                        previewRequest = DefaultPromptBuilder.build(
                            context = promptPreviewContext,
                            settings = appSettings,
                            styleProfile = profile.copy(mode = ReplyStyleMode.PLAYBOOK)
                                .withResolvedCatalog(catalog)
                        )
                    }
                )
                PlaybookChoiceGroups(
                    playbooks = catalog.playbooks,
                    selectedId = profile.playbookConfig.id,
                    isEditMode = isPlaybookEditMode,
                    onAdd = { categoryLabel ->
                        editingItem = StyleEditTarget.Playbook(newCustomPlaybookConfig(categoryLabel))
                    },
                    onSelect = { playbookId ->
                        val playbook = catalog.resolvePlaybook(playbookId)
                        if (isPlaybookEditMode) {
                            editingItem = StyleEditTarget.Playbook(playbook)
                        } else {
                            onProfileChange(
                                profile.copy(playbookConfig = playbook)
                                    .withResolvedCatalog(catalog)
                            )
                        }
                    }
                )

                HorizontalDivider()
                StyleCategoryHeader(
                    title = "润色表达",
                    isEditMode = isPolishEditMode,
                    onEditClick = { isPolishEditMode = !isPolishEditMode },
                    onPreviewClick = {
                        previewRequest = DefaultPromptBuilder.build(
                            context = promptPreviewContext,
                            settings = appSettings,
                            styleProfile = profile.copy(mode = ReplyStyleMode.POLISH)
                                .withResolvedCatalog(catalog)
                        )
                    }
                )
                ChoiceButtonGrid(
                    items = catalog.polishGoals.map { it.id to it.label },
                    selectedId = profile.polishGoalConfig.id,
                    isEditMode = isPolishEditMode,
                    onAdd = {
                        editingItem = StyleEditTarget.Polish(newCustomPolishGoalConfig())
                    },
                    onSelect = { goalId ->
                        val goal = catalog.resolvePolishGoal(goalId)
                        if (isPolishEditMode) {
                            editingItem = StyleEditTarget.Polish(goal)
                        } else {
                            onProfileChange(
                                profile.copy(polishGoalConfig = goal)
                                    .withResolvedCatalog(catalog)
                            )
                        }
                    }
                )

                Text(
                    text = styleExample(profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextButton(onClick = onResetBuiltinCatalog) {
                    Text("恢复内置项默认")
                }
            }
        }
    }
}

@Composable
private fun StyleCategoryHeader(
    title: String,
    isEditMode: Boolean,
    onEditClick: () -> Unit,
    onPreviewClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        StyleSmallActionButton(
            text = if (isEditMode) "完成" else "编辑",
            onClick = onEditClick
        )
        StyleSmallActionButton(
            text = "Prompt 预览",
            onClick = onPreviewClick
        )
    }
}

@Composable
private fun StyleSmallActionButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

private fun realPromptPreviewContext(messages: List<ChatMessage>): ChatContext {
    return ChatContext(
        messages = messages,
        targetApp = "wechat",
        conversationType = ConversationType.SINGLE_CHAT,
        collectedAt = System.currentTimeMillis()
    )
}

private sealed class StyleEditTarget {
    data class Persona(val item: ReplyPersonaConfig) : StyleEditTarget()
    data class Playbook(val item: ReplyPlaybookConfig) : StyleEditTarget()
    data class Polish(val item: PolishGoalConfig) : StyleEditTarget()
}

@Composable
private fun StyleItemEditorDialog(
    target: StyleEditTarget,
    onDismiss: () -> Unit,
    onSave: (StyleEditTarget) -> Unit,
    onDelete: (StyleEditTarget) -> Unit
) {
    var label by remember(target) { mutableStateOf(target.label) }
    var category by remember(target) { mutableStateOf(target.categoryLabel.orEmpty()) }
    var identityPrompt by remember(target) { mutableStateOf(target.identityPrompt) }
    var promptGuide by remember(target) { mutableStateOf(target.promptGuide) }
    val canDelete = !target.isBuiltin

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.dialogTitle) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("名称") },
                    shape = RoundedCornerShape(16.dp)
                )
                if (target is StyleEditTarget.Playbook) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("分类") },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
                OutlinedTextField(
                    value = identityPrompt,
                    onValueChange = { identityPrompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("身份/定位") },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                OutlinedTextField(
                    value = promptGuide,
                    onValueChange = { promptGuide = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    label = { Text("提示词") },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                if (!canDelete) {
                    Text(
                        text = "内置项可编辑，但不能删除。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canDelete) {
                    TextButton(onClick = { onDelete(target) }) {
                        Text("删除")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        target.withValues(
                            label = label.ifBlank { target.label },
                            categoryLabel = category.ifBlank { "自定义话术" },
                            identityPrompt = identityPrompt.ifBlank { target.identityPrompt },
                            promptGuide = promptGuide.ifBlank { target.promptGuide }
                        )
                    )
                }
            ) {
                Text("保存")
            }
        }
    )
}

private val StyleEditTarget.label: String
    get() = when (this) {
        is StyleEditTarget.Persona -> item.label
        is StyleEditTarget.Playbook -> item.label
        is StyleEditTarget.Polish -> item.label
    }

private val StyleEditTarget.categoryLabel: String?
    get() = when (this) {
        is StyleEditTarget.Playbook -> item.categoryLabel
        else -> null
    }

private val StyleEditTarget.identityPrompt: String
    get() = when (this) {
        is StyleEditTarget.Persona -> item.identityPrompt
        is StyleEditTarget.Playbook -> item.identityPrompt
        is StyleEditTarget.Polish -> item.identityPrompt
    }

private val StyleEditTarget.promptGuide: String
    get() = when (this) {
        is StyleEditTarget.Persona -> item.promptGuide
        is StyleEditTarget.Playbook -> item.promptGuide
        is StyleEditTarget.Polish -> item.promptGuide
    }

private val StyleEditTarget.isBuiltin: Boolean
    get() = when (this) {
        is StyleEditTarget.Persona -> item.isBuiltin
        is StyleEditTarget.Playbook -> item.isBuiltin
        is StyleEditTarget.Polish -> item.isBuiltin
    }

private val StyleEditTarget.dialogTitle: String
    get() = when (this) {
        is StyleEditTarget.Persona -> "编辑角色"
        is StyleEditTarget.Playbook -> "编辑话术宝典"
        is StyleEditTarget.Polish -> "编辑润色表达"
    }

private fun StyleEditTarget.withValues(
    label: String,
    categoryLabel: String,
    identityPrompt: String,
    promptGuide: String
): StyleEditTarget {
    return when (this) {
        is StyleEditTarget.Persona -> copy(
            item = item.copy(
                label = label,
                identityPrompt = identityPrompt,
                promptGuide = promptGuide
            )
        )

        is StyleEditTarget.Playbook -> copy(
            item = item.copy(
                categoryLabel = categoryLabel,
                label = label,
                identityPrompt = identityPrompt,
                promptGuide = promptGuide
            )
        )

        is StyleEditTarget.Polish -> copy(
            item = item.copy(
                label = label,
                identityPrompt = identityPrompt,
                promptGuide = promptGuide
            )
        )
    }
}

private fun ReplyStyleCatalogState.saveStyleTarget(target: StyleEditTarget): ReplyStyleCatalogState {
    return when (target) {
        is StyleEditTarget.Persona -> copy(personas = personas.upsert(target.item, ReplyPersonaConfig::id))
        is StyleEditTarget.Playbook -> copy(playbooks = playbooks.upsert(target.item, ReplyPlaybookConfig::id))
        is StyleEditTarget.Polish -> copy(polishGoals = polishGoals.upsert(target.item, PolishGoalConfig::id))
    }
}

private fun ReplyStyleCatalogState.deleteStyleTarget(target: StyleEditTarget): ReplyStyleCatalogState {
    if (target.isBuiltin) return this
    return when (target) {
        is StyleEditTarget.Persona -> copy(personas = personas.filterNot { it.id == target.item.id })
        is StyleEditTarget.Playbook -> copy(playbooks = playbooks.filterNot { it.id == target.item.id })
        is StyleEditTarget.Polish -> copy(polishGoals = polishGoals.filterNot { it.id == target.item.id })
    }
}

private fun ReplyStyleProfile.withStyleTarget(target: StyleEditTarget): ReplyStyleProfile {
    return when (target) {
        is StyleEditTarget.Persona -> copy(
            persona = ReplyStyleCatalog.personaFromConfig(target.item),
            personaConfig = target.item
        )

        is StyleEditTarget.Playbook -> copy(
            playbookScene = ReplyStyleCatalog.sceneFromConfig(target.item),
            playbookConfig = target.item
        )

        is StyleEditTarget.Polish -> copy(
            polishGoal = ReplyStyleCatalog.polishGoalFromConfig(target.item),
            polishGoalConfig = target.item
        )
    }
}

internal fun ReplyStyleProfile.withResolvedCatalog(catalog: ReplyStyleCatalogState): ReplyStyleProfile {
    val resolvedPersona = catalog.resolvePersona(personaConfig.id)
    val resolvedPlaybook = catalog.resolvePlaybook(playbookConfig.id)
    val resolvedPolish = catalog.resolvePolishGoal(polishGoalConfig.id)
    return copy(
        persona = ReplyStyleCatalog.personaFromConfig(resolvedPersona),
        playbookScene = ReplyStyleCatalog.sceneFromConfig(resolvedPlaybook),
        polishGoal = ReplyStyleCatalog.polishGoalFromConfig(resolvedPolish),
        personaConfig = resolvedPersona,
        playbookConfig = resolvedPlaybook,
        polishGoalConfig = resolvedPolish
    )
}

private fun <T> List<T>.upsert(
    item: T,
    idOf: (T) -> String
): List<T> {
    val exists = any { idOf(it) == idOf(item) }
    return if (exists) {
        map { current -> if (idOf(current) == idOf(item)) item else current }
    } else {
        this + item
    }
}

private fun newCustomPersonaConfig(): ReplyPersonaConfig {
    return ReplyPersonaConfig(
        id = ReplyStyleCatalogStore.newCustomId("persona"),
        label = "自定义角色",
        identityPrompt = "你正在模仿一个自定义微信回复身份。",
        promptGuide = "按用户填写的风格自然回复。",
        isBuiltin = false
    )
}

private fun newCustomPlaybookConfig(categoryLabel: String): ReplyPlaybookConfig {
    val category = categoryLabel.ifBlank { "自定义话术" }
    return ReplyPlaybookConfig(
        id = ReplyStyleCatalogStore.newCustomId("playbook"),
        categoryLabel = category,
        label = "自定义场景",
        identityPrompt = "你正在生成一个自定义聊天场景的话术。",
        promptGuide = "按用户填写的场景目标生成可直接发送的话术。",
        isBuiltin = false
    )
}

private fun newCustomPolishGoalConfig(): PolishGoalConfig {
    return PolishGoalConfig(
        id = ReplyStyleCatalogStore.newCustomId("polish"),
        label = "自定义润色",
        identityPrompt = "你正在按自定义目标润色用户草稿。",
        promptGuide = "按用户填写的润色目标改写表达。",
        isBuiltin = false
    )
}

@Composable
private fun ChoiceButtonGrid(
    items: List<Pair<String, String>>,
    selectedId: String,
    isEditMode: Boolean,
    onAdd: (() -> Unit)? = null,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val gridItems = items.map { StyleGridItem.Choice(it.first, it.second) } +
            if (isEditMode && onAdd != null) listOf(StyleGridItem.Add) else emptyList()
        gridItems.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowItems.forEach { item ->
                    when (item) {
                        StyleGridItem.Add -> StyleAddButton(
                            modifier = Modifier.weight(1f),
                            onClick = { onAdd?.invoke() }
                        )

                        is StyleGridItem.Choice -> StyleChoiceButton(
                            label = item.label,
                            selected = item.id == selectedId,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(item.id) }
                        )
                    }
                }
                repeat(4 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PlaybookChoiceGroups(
    playbooks: List<ReplyPlaybookConfig>,
    selectedId: String,
    isEditMode: Boolean,
    onAdd: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        playbooks.groupBy { it.categoryLabel }.forEach { (category, groupItems) ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                ChoiceButtonGrid(
                    items = groupItems.map { it.id to it.label },
                    selectedId = selectedId,
                    isEditMode = isEditMode,
                    onAdd = { onAdd(category) },
                    onSelect = onSelect
                )
            }
        }
    }
}

private sealed class StyleGridItem {
    data class Choice(val id: String, val label: String) : StyleGridItem()
    object Add : StyleGridItem()
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
            shape = RoundedCornerShape(14.dp),
            contentPadding = styleButtonContentPadding()
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
            contentPadding = styleButtonContentPadding()
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StyleAddButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        contentPadding = styleButtonContentPadding(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Text("新增", maxLines = 1)
    }
}

private fun styleButtonContentPadding(): PaddingValues {
    return PaddingValues(horizontal = 6.dp, vertical = 8.dp)
}

private fun styleExample(profile: ReplyStyleProfile): String {
    return "短按气泡：按“${profile.personaConfig.label}”生成默认回复；长按气泡：话术宝典按“${profile.playbookConfig.label}”单次出文案，润色按“${profile.polishGoalConfig.label}”读取输入框草稿并回写候选。"
}

internal fun ReplyStyleProfile.asDefaultReply(): ReplyStyleProfile {
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
                    label = "发送预览",
                    value = debugState.requestPreview ?: "暂无"
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
    screenCaptureState: OcrScreenCaptureState,
    isTestingScreenCapture: Boolean,
    isTestingOcrRecognition: Boolean,
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
                    text = "OCR 兜底使用无障碍服务截图，不再请求投屏授权。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                StatusRow(
                    label = "截图方式",
                    value = "无障碍服务"
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
                    label = "帧质量",
                    value = screenCaptureState.frameStats.ifBlank { "暂无" }
                )
                StatusRow(
                    label = "调试截图",
                    value = screenCaptureState.debugImagePath.ifBlank { "暂无" }
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
                    label = "过滤原因",
                    value = if (debugState.filterSummaries.isEmpty()) {
                        "暂无"
                    } else {
                        debugState.filterSummaries.take(3).joinToString("；") {
                            "${it.reason.label} ${it.count}"
                        }
                    }
                )
                StatusRow(
                    label = "更新时间",
                    value = formatTimestamp(debugState.updatedAtMillis)
                )

                OutlinedButton(
                    onClick = onTestScreenCapture,
                    enabled = !isTestingScreenCapture &&
                        !isTestingOcrRecognition,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isTestingScreenCapture) "取图中..." else "测试无障碍截图")
                }

                OutlinedButton(
                    onClick = onTestOcrRecognition,
                    enabled = !isTestingScreenCapture &&
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
                                    screenCaptureState = screenCaptureState
                                )
                            )
                        )
                        Toast.makeText(context, "已复制 OCR 诊断", Toast.LENGTH_SHORT).show()
                    },
                    enabled = debugState.updatedAtMillis > 0L ||
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

                if (debugState.filterSummaryPreviews.isNotEmpty()) {
                    Text(
                        text = "过滤原因",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    debugState.filterSummaryPreviews.take(8).forEach { summary ->
                        Text(
                            text = "- $summary",
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
private fun ConversationPreviewSection(
    conversationTitle: String?,
    messages: List<ChatMessage>
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "聊天预览",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
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
private fun MessageBubble(message: ChatMessage) {
    val isMine = message.role == ChatRole.ME
    val bubbleColor = when (message.role) {
        ChatRole.ME -> MaterialTheme.colorScheme.secondaryContainer
        ChatRole.FRIEND -> MaterialTheme.colorScheme.surface
        ChatRole.SYSTEM -> MaterialTheme.colorScheme.primaryContainer
        ChatRole.UNKNOWN -> MaterialTheme.colorScheme.surface
    }
    val textColor = MaterialTheme.colorScheme.onSurface

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
