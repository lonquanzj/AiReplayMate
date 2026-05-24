package com.lonquanzj.aireplaymate.ui

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.lonquanzj.aireplaymate.PermissionSnapshot
import com.lonquanzj.aireplaymate.diagnostics.DiagnosticLogStore
import com.lonquanzj.aireplaymate.llm.LlmDebugStore
import com.lonquanzj.aireplaymate.llm.testLlmConnection
import com.lonquanzj.aireplaymate.ocr.OcrDebugStore
import com.lonquanzj.aireplaymate.overlay.OverlayDiagnosticsStore
import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.settings.ReplyStyleCatalogStore
import com.lonquanzj.aireplaymate.settings.ReplyStyleSettingsStore
import kotlinx.coroutines.launch

private enum class HomeTab(val label: String) {
    MAIN("主界面"),
    STYLE("回复风格"),
    LLM("LLM 设置"),
    ADVANCED("高级调试")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreen(
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
    val settingsTransferActions = rememberSettingsTransferActions(
        appSettings = appSettings,
        replyStyleProfile = replyStyleProfile,
        replyStyleCatalog = replyStyleCatalog,
        saveAppSettings = saveAppSettings,
        onAppSettingsImported = { appSettings = it },
        onReplyStyleProfileImported = { replyStyleProfile = it },
        onReplyStyleCatalogImported = { replyStyleCatalog = it }
    )
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
                        Color(0xFFF7F3FF),
                        Color(0xFFF1ECFF),
                        Color(0xFFFFFCFF)
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    edgePadding = 12.dp,
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        val currentTab = tabPositions[pagerState.currentPage]
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .offset(x = currentTab.left)
                                    .width(currentTab.width)
                                    .height(3.dp)
                                    .background(
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)
                                    )
                            )
                        }
                    },
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    HomeTab.entries.forEachIndexed { index, tab ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = {
                                Text(
                                    text = tab.label,
                                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall
                                )
                            }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .statusBarsPadding()
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
                                onImportSettings = settingsTransferActions.importSettings,
                                onExportSettings = settingsTransferActions.exportSettings
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
