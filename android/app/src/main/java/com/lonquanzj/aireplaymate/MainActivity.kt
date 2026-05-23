package com.lonquanzj.aireplaymate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.Secure
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lonquanzj.aireplaymate.accessibility.ReplyAccessibilityService
import com.lonquanzj.aireplaymate.diagnostics.DiagnosticLogStore
import com.lonquanzj.aireplaymate.llm.LlmDebugStore
import com.lonquanzj.aireplaymate.llm.OpenAiCompatibleLlmGateway
import com.lonquanzj.aireplaymate.ocr.OcrDebugStore
import com.lonquanzj.aireplaymate.overlay.OverlayButtonService
import com.lonquanzj.aireplaymate.overlay.OverlayDiagnosticsStore
import com.lonquanzj.aireplaymate.overlay.OverlayServiceStateStore
import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.LlmRequest
import com.lonquanzj.aireplaymate.settings.AppSettingsStore
import com.lonquanzj.aireplaymate.settings.AppSettingsTransfer
import com.lonquanzj.aireplaymate.settings.AppSettingsValidator
import com.lonquanzj.aireplaymate.settings.ReplyStyleCatalogStore
import com.lonquanzj.aireplaymate.settings.ReplyStyleSettingsStore
import com.lonquanzj.aireplaymate.ui.AboutDialog
import com.lonquanzj.aireplaymate.ui.AdvancedTabContent
import com.lonquanzj.aireplaymate.ui.LlmTabContent
import com.lonquanzj.aireplaymate.ui.MainTabContent
import com.lonquanzj.aireplaymate.ui.StyleTabContent
import com.lonquanzj.aireplaymate.ui.withResolvedCatalog
import com.lonquanzj.aireplaymate.ui.theme.AiReplayMateTheme
import kotlinx.coroutines.launch

internal data class PermissionSnapshot(
    val accessibilityEnabled: Boolean,
    val overlayEnabled: Boolean
)

private enum class HomeTab(val label: String) {
    MAIN("主界面"),
    STYLE("回复风格"),
    LLM("LLM 设置"),
    ADVANCED("高级调试")
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
    val exportSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val payload = AppSettingsTransfer.encode(
                settings = appSettings,
                replyStyleProfile = replyStyleProfile,
                replyStyleCatalog = replyStyleCatalog
            )
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(payload)
            } ?: error("Unable to open export file")
        }.onSuccess {
            Toast.makeText(context, "配置已导出", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            Toast.makeText(context, "导出失败：${error.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
        }
    }
    val importSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            } ?: error("Unable to open import file")
            AppSettingsTransfer.decodeFull(raw).getOrThrow()
        }.onSuccess { importedConfig ->
            appSettings = importedConfig.appSettings
            saveAppSettings(importedConfig.appSettings)
            replyStyleCatalog = importedConfig.replyStyleCatalog
            ReplyStyleCatalogStore.save(context, importedConfig.replyStyleCatalog)
            replyStyleProfile = importedConfig.replyStyleProfile
                .withResolvedCatalog(importedConfig.replyStyleCatalog)
            ReplyStyleSettingsStore.save(context, replyStyleProfile)
            Toast.makeText(context, "配置已导入", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            Toast.makeText(context, "导入失败：${error.message ?: "配置文件无效"}", Toast.LENGTH_LONG).show()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val pagerState = rememberPagerState(pageCount = { HomeTab.entries.size })
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
                        Color(0xFFFFFCFF),
                        Color(0xFFF8F4FF),
                        Color(0xFFF3EEFF)
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.statusBars,
            bottomBar = {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 12.dp,
                    indicator = { tabPositions ->
                        val currentTab = tabPositions[pagerState.currentPage]
                        Box(Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(x = currentTab.left)
                                    .width(currentTab.width)
                                    .height(3.dp)
                                    .background(
                                        color = TabRowDefaults.primaryContentColor,
                                        shape = RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)
                                    )
                            )
                        }
                    }
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
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
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
                                },
                                onImportSettings = {
                                    importSettingsLauncher.launch(arrayOf("application/json", "text/*"))
                                },
                                onExportSettings = {
                                    exportSettingsLauncher.launch("aireplaymate-config.json")
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
                            AdvancedTabContent()
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
