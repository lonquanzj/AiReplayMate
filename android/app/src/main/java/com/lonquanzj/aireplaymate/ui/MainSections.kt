package com.lonquanzj.aireplaymate.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugStore
import com.lonquanzj.aireplaymate.context.ConversationType
import com.lonquanzj.aireplaymate.context.DefaultContextBuilder
import com.lonquanzj.aireplaymate.diagnostics.DiagnosticLogStore
import com.lonquanzj.aireplaymate.llm.LlmDebugStore
import com.lonquanzj.aireplaymate.PermissionSnapshot
import com.lonquanzj.aireplaymate.ocr.AndroidScreenCaptureProvider
import com.lonquanzj.aireplaymate.ocr.MlKitChineseOcrEngine
import com.lonquanzj.aireplaymate.ocr.OcrDebugStore
import com.lonquanzj.aireplaymate.ocr.OcrScreenCaptureStore
import com.lonquanzj.aireplaymate.overlay.OverlayDiagnosticsStore
import com.lonquanzj.aireplaymate.overlay.OverlayServiceStateStore
import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalogState
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import com.lonquanzj.aireplaymate.session.ReplyContextPreviewStore
import com.lonquanzj.aireplaymate.settings.AppSettingsValidator
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
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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

        AboutEntry(onClick = onShowAboutDialog)

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
internal fun LlmTabContent(
    appSettings: AppSettings,
    testingLlm: Boolean,
    onSettingsChange: (AppSettings) -> Unit,
    onTestConnection: () -> Unit,
    onImportSettings: () -> Unit,
    onExportSettings: () -> Unit
) {
    val llmDebugState by LlmDebugStore.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LlmSettingsSection(
            settings = appSettings,
            validation = AppSettingsValidator.validate(appSettings),
            isTesting = testingLlm,
            onSettingsChange = onSettingsChange,
            onTestConnection = onTestConnection,
            onImportSettings = onImportSettings,
            onExportSettings = onExportSettings
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
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
