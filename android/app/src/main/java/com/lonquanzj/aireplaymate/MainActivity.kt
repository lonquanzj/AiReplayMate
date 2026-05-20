package com.lonquanzj.aireplaymate

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.Secure
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugState
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugStore
import com.lonquanzj.aireplaymate.accessibility.AccessibilityActionBridge
import com.lonquanzj.aireplaymate.accessibility.ReplyAccessibilityService
import com.lonquanzj.aireplaymate.demo.DemoAuthor
import com.lonquanzj.aireplaymate.demo.DemoCandidate
import com.lonquanzj.aireplaymate.demo.DemoMessage
import com.lonquanzj.aireplaymate.demo.DemoScenario
import com.lonquanzj.aireplaymate.demo.DemoStage
import com.lonquanzj.aireplaymate.demo.demoScenarios
import com.lonquanzj.aireplaymate.ui.theme.AiReplayMateTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class PermissionSnapshot(
    val accessibilityEnabled: Boolean,
    val overlayEnabled: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AiReplayMateTheme {
                MainScreen(
                    onOpenAccessibilitySettings = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenOverlaySettings = {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    },
                    loadPermissionSnapshot = { readPermissionSnapshot(this) }
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
    loadPermissionSnapshot: () -> PermissionSnapshot
) {
    val scenarios = remember { demoScenarios() }
    var selectedScenarioId by remember { mutableStateOf(scenarios.first().id) }
    var currentStage by remember { mutableStateOf(DemoStage.IDLE) }
    var replyDraft by remember { mutableStateOf("") }
    var extractedMessages by remember { mutableStateOf(scenarios.first().messages) }
    var candidates by remember { mutableStateOf(emptyList<DemoCandidate>()) }
    var showCandidateSheet by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }
    var generationRound by remember { mutableIntStateOf(0) }
    var permissionSnapshot by remember { mutableStateOf(loadPermissionSnapshot()) }
    val activityLog = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val debugState by AccessibilityDebugStore.state.collectAsState()

    val selectedScenario = remember(selectedScenarioId, scenarios) {
        scenarios.first { it.id == selectedScenarioId }
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

    LaunchedEffect(selectedScenarioId) {
        currentStage = DemoStage.IDLE
        replyDraft = ""
        extractedMessages = selectedScenario.messages
        candidates = emptyList()
        showCandidateSheet = false
        generationRound = 0
        activityLog.clear()
        activityLog += "已切换到 ${selectedScenario.title} 场景"
    }

    if (showCandidateSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        BackHandler {
            showCandidateSheet = false
            currentStage = DemoStage.IDLE
            activityLog += "用户关闭候选面板，本次演示已取消"
        }

        ModalBottomSheet(
            onDismissRequest = {
                showCandidateSheet = false
                currentStage = DemoStage.IDLE
                activityLog += "候选面板已收起，等待再次触发"
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

                candidates.forEach { candidate ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showCandidateSheet = false
                                scope.launch {
                                    currentStage = DemoStage.AUTOFILLING
                                    activityLog += "用户选择了 ${candidate.tone} 风格候选"
                                    delay(350)
                                    replyDraft = candidate.text
                                    currentStage = DemoStage.DONE
                                    activityLog += "已把候选填入模拟输入框"
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
                            generationRound += 1
                            candidates = selectedScenario.candidates(generationRound)
                            activityLog += "已重新生成 3 条候选"
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("重新生成")
                    }

                    Button(
                        onClick = {
                            showCandidateSheet = false
                            currentStage = DemoStage.IDLE
                            activityLog += "本次演示已结束，可重新触发"
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
                            Text("AiReplayMate Demo")
                            Text(
                                text = "把产品主链路先演示出来",
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
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroCard(
                    currentStage = currentStage,
                    isRunning = isRunning,
                    selectedScenario = selectedScenario,
                    onRunDemo = {
                        if (!isRunning) {
                            scope.launch {
                                isRunning = true
                                showCandidateSheet = false
                                replyDraft = ""
                                extractedMessages = selectedScenario.messages
                                candidates = emptyList()
                                activityLog.clear()

                                currentStage = DemoStage.VALIDATING_TARGET
                                activityLog += "已识别微信单聊页面"
                                delay(450)

                                currentStage = DemoStage.COLLECTING_ACCESSIBILITY
                                activityLog += "Accessibility 提取到 ${selectedScenario.messages.size} 条消息"
                                delay(700)

                                currentStage = DemoStage.REQUESTING_LLM
                                activityLog += "正在根据最近一条对方消息生成回复候选"
                                delay(900)

                                generationRound += 1
                                candidates = selectedScenario.candidates(generationRound)
                                currentStage = DemoStage.CANDIDATE_READY
                                activityLog += "已返回 3 条候选，等待用户选择"
                                showCandidateSheet = true
                                isRunning = false
                            }
                        }
                    },
                    onReset = {
                        if (!isRunning) {
                            currentStage = DemoStage.IDLE
                            showCandidateSheet = false
                            replyDraft = ""
                            candidates = emptyList()
                            extractedMessages = selectedScenario.messages
                            activityLog.clear()
                            activityLog += "已重置演示状态"
                        }
                    }
                )

                PermissionStatusSection(
                    permissionSnapshot = permissionSnapshot,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenOverlaySettings = onOpenOverlaySettings
                )

                RealAccessibilitySection(debugState = debugState)

                ScenarioSection(
                    scenarios = scenarios,
                    selectedScenarioId = selectedScenarioId,
                    enabled = !isRunning,
                    onSelectScenario = { selectedScenarioId = it }
                )

                SessionStageSection(currentStage = currentStage)

                ConversationPreviewSection(
                    scenario = selectedScenario,
                    messages = extractedMessages
                )

                ReplyDraftSection(
                    replyDraft = replyDraft,
                    onReplyDraftChange = { replyDraft = it },
                    onTryRealAutofill = {
                        val result = AccessibilityActionBridge.tryAutofill(replyDraft)
                        activityLog += "真实填入：${result.message}"
                    },
                    canTryRealAutofill = replyDraft.isNotBlank()
                )

                ActivityLogSection(entries = activityLog)

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun HeroCard(
    currentStage: DemoStage,
    isRunning: Boolean,
    selectedScenario: DemoScenario,
    onRunDemo: () -> Unit,
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
                    text = "Demo Build",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Text(
                text = selectedScenario.title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = selectedScenario.note,
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
                text = currentStage.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFBFE4DE)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onRunDemo,
                    enabled = !isRunning,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF2B35D),
                        contentColor = Color(0xFF2D1700)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (currentStage == DemoStage.IDLE) "运行 Demo" else "再次演示")
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
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit
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
                    "已开启，可继续演示真实入口。"
                } else {
                    "未开启时仍可演示流程，但无法接真实事件。"
                },
                buttonText = "去开启",
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth()
            )

            PermissionCard(
                title = "悬浮窗权限",
                enabled = permissionSnapshot.overlayEnabled,
                description = if (permissionSnapshot.overlayEnabled) {
                    "已授权，后续接 Overlay 会更顺。"
                } else {
                    "Demo 仍可运行，但悬浮按钮能力还未接入。"
                },
                buttonText = "去设置",
                onClick = onOpenOverlaySettings,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    enabled: Boolean,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
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

            OutlinedButton(
                onClick = onClick,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun RealAccessibilitySection(debugState: AccessibilityDebugState) {
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
                    label = "填入文本预览",
                    value = debugState.lastAutofillPreview ?: "暂无"
                )
                StatusRow(
                    label = "最后更新时间",
                    value = formatTimestamp(debugState.updatedAtMillis)
                )

                if (debugState.extractedMessages.isNotEmpty()) {
                    Text(
                        text = "消息提取预览",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    debugState.extractedMessages.takeLast(5).forEach { message ->
                        Text(
                            text = "- $message",
                            style = MaterialTheme.typography.bodyMedium,
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
private fun ScenarioSection(
    scenarios: List<DemoScenario>,
    selectedScenarioId: String,
    enabled: Boolean,
    onSelectScenario: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "演示场景",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        scenarios.forEach { scenario ->
            val selected = scenario.id == selectedScenarioId
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(enabled = enabled) { onSelectScenario(scenario.id) },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(10.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                }
                            )
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = scenario.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = scenario.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = if (selected) "当前" else "切换",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionStageSection(currentStage: DemoStage) {
    val steps = listOf(
        DemoStage.VALIDATING_TARGET,
        DemoStage.COLLECTING_ACCESSIBILITY,
        DemoStage.REQUESTING_LLM,
        DemoStage.CANDIDATE_READY,
        DemoStage.AUTOFILLING,
        DemoStage.DONE
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
                        currentStage.ordinal > step.ordinal -> "已完成"
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
    scenario: DemoScenario,
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
                    text = scenario.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "当前 Demo 会把这段上下文送去生成 3 条候选回复。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                messages.forEach { message ->
                    MessageBubble(message = message)
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
            text = "演示日志",
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
                        text = "还没有触发 Demo。点击上面的按钮后，这里会按阶段记录主链路进度。",
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

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "暂无"
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
