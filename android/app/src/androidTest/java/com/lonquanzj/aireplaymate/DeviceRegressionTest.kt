package com.lonquanzj.aireplaymate
import java.net.SocketTimeoutException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lonquanzj.aireplaymate.accessibility.ChatMessage
import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.accessibility.MessageSource
import com.lonquanzj.aireplaymate.context.ChatContext
import com.lonquanzj.aireplaymate.context.ConversationType
import com.lonquanzj.aireplaymate.diagnostics.DiagnosticLogKind
import com.lonquanzj.aireplaymate.diagnostics.DiagnosticLogStore
import com.lonquanzj.aireplaymate.llm.LlmDebugStore
import com.lonquanzj.aireplaymate.llm.LlmDebugPhase
import com.lonquanzj.aireplaymate.llm.LlmDebugState
import com.lonquanzj.aireplaymate.llm.LlmFailureCategory
import com.lonquanzj.aireplaymate.ocr.OcrAttemptCategory
import com.lonquanzj.aireplaymate.ocr.OcrAttemptResult
import com.lonquanzj.aireplaymate.ocr.OcrDebugState
import com.lonquanzj.aireplaymate.ocr.OcrDebugStore
import com.lonquanzj.aireplaymate.overlay.OverlayDiagnosticsStore
import com.lonquanzj.aireplaymate.overlay.OverlayDiagnosticsState
import com.lonquanzj.aireplaymate.overlay.OverlayRunPhase
import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.ContextSendPolicy
import com.lonquanzj.aireplaymate.prompt.DefaultPromptBuilder
import com.lonquanzj.aireplaymate.prompt.PolishGoal
import com.lonquanzj.aireplaymate.prompt.ReplyCandidate
import com.lonquanzj.aireplaymate.prompt.ReplyPersona
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalog
import com.lonquanzj.aireplaymate.prompt.ReplyStyleMode
import com.lonquanzj.aireplaymate.session.LocalFallbackReplyGenerator
import com.lonquanzj.aireplaymate.settings.AppSettingsStore
import com.lonquanzj.aireplaymate.settings.ReplyStyleSettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceRegressionTest {
    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DiagnosticLogStore.initialize(context)
        DiagnosticLogStore.clear()
        LlmDebugStore.reset()
        OverlayDiagnosticsStore.reset()
        OcrDebugStore.reset()
    }

    @After
    fun tearDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DiagnosticLogStore.initialize(context)
        DiagnosticLogStore.clear()
        LlmDebugStore.reset()
        OverlayDiagnosticsStore.reset()
        OcrDebugStore.reset()
    }

    @Test(timeout = 15_000)
    fun device_baseline_persistence_and_entrypoint_are_healthy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val originalSettings = AppSettingsStore.load(context)
        val originalProfile = ReplyStyleSettingsStore.load(context)

        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            assertNotNull("Launch intent should exist for installed app", launchIntent)
            assertEquals(MainActivity::class.java.name, launchIntent?.component?.className)

            val updatedSettings = AppSettings(
                apiKey = " device-test-key ",
                baseUrl = " https://example.com/v1/chat/completions?apiKey=secret ",
                model = " device-test-model ",
                contextSendPolicy = ContextSendPolicy.LATEST_FRIEND_MESSAGE
            )
            AppSettingsStore.save(context, updatedSettings)

            val reloadedSettings = AppSettingsStore.load(context)
            assertEquals("device-test-key", reloadedSettings.apiKey)
            assertEquals("https://example.com/v1/chat/completions?apiKey=secret", reloadedSettings.baseUrl)
            assertEquals("device-test-model", reloadedSettings.model)
            assertEquals(ContextSendPolicy.LATEST_FRIEND_MESSAGE, reloadedSettings.contextSendPolicy)

            val updatedProfile = ReplyStyleCatalog.profile(
                modeId = ReplyStyleMode.PLAYBOOK.id,
                personaId = ReplyPersona.DOMINANT_CEO.id,
                sceneId = "good_night",
                polishGoalId = PolishGoal.FLIRTY.id
            )
            ReplyStyleSettingsStore.save(context, updatedProfile)

            val reloadedProfile = ReplyStyleSettingsStore.load(context)
            assertEquals(ReplyStyleMode.QUICK_REPLY, reloadedProfile.mode)
            assertEquals(updatedProfile.personaConfig.id, reloadedProfile.personaConfig.id)
            assertEquals(updatedProfile.playbookConfig.id, reloadedProfile.playbookConfig.id)
            assertEquals(updatedProfile.polishGoalConfig.id, reloadedProfile.polishGoalConfig.id)

            DiagnosticLogStore.recordLlm(
                LlmDebugState(
                    phase = LlmDebugPhase.PARSED,
                    baseUrl = reloadedSettings.baseUrl,
                    model = reloadedSettings.model,
                    candidateCount = 2,
                    failureCategory = LlmFailureCategory.NONE,
                    recoveryHint = "device regression success",
                    updatedAtMillis = System.currentTimeMillis()
                )
            )

            val snapshot = DiagnosticLogStore.buildSnapshot()
            assertTrue(snapshot.contains("AiReplayMate Diagnostic Log Snapshot"))
            assertTrue(snapshot.contains("count=1"))
            assertTrue(snapshot.contains("device-test-model"))
            assertTrue(snapshot.contains("https://example.com/v1/chat/completions"))
            assertFalse(snapshot.contains("apiKey=secret"))
        } finally {
            AppSettingsStore.save(context, originalSettings)
            ReplyStyleSettingsStore.save(context, originalProfile)
            DiagnosticLogStore.clear()
        }
    }

    @Test(timeout = 15_000)
    fun prompt_builder_respects_policy_sanitization_and_mode_rules() {
        val context = ChatContext(
            messages = listOf(
                ChatMessage(
                    id = "1",
                    role = ChatRole.ME,
                    content = "刚忙完",
                    timestamp = 1L,
                    source = MessageSource.ACCESSIBILITY,
                    confidence = 0.98f
                ),
                ChatMessage(
                    id = "2",
                    role = ChatRole.FRIEND,
                    content = "今晚要不要一起吃饭？\u0007",
                    timestamp = 2L,
                    source = MessageSource.ACCESSIBILITY,
                    confidence = 0.98f
                ),
                ChatMessage(
                    id = "3",
                    role = ChatRole.ME,
                    content = "我看下安排",
                    timestamp = 3L,
                    source = MessageSource.ACCESSIBILITY,
                    confidence = 0.95f
                ),
                ChatMessage(
                    id = "4",
                    role = ChatRole.FRIEND,
                    content = "别放我鸽子呀",
                    timestamp = 4L,
                    source = MessageSource.MERGED,
                    confidence = 0.88f
                )
            ),
            targetApp = "com.tencent.mm",
            conversationType = ConversationType.SINGLE_CHAT,
            collectedAt = 5L
        )

        val fullContextRequest = DefaultPromptBuilder.build(
            context = context,
            settings = AppSettings(
                candidateCount = 0,
                temperature = 9f,
                maxTokens = 40,
                contextSendPolicy = ContextSendPolicy.FULL_CONTEXT
            ),
            styleProfile = ReplyStyleCatalog.profile(
                modeId = ReplyStyleMode.QUICK_REPLY.id,
                personaId = ReplyPersona.WARM_GENTLE.id,
                sceneId = ReplyStyleCatalog.defaultScene.sceneId,
                polishGoalId = PolishGoal.default.id
            )
        )

        assertEquals(1, fullContextRequest.candidateCount)
        assertEquals(2f, fullContextRequest.temperature, 0.0001f)
        assertEquals(120, fullContextRequest.maxTokens)
        assertTrue(fullContextRequest.userPrompt.contains("聊天上下文："))
        assertTrue(fullContextRequest.userPrompt.contains("我: 刚忙完"))
        assertTrue(fullContextRequest.userPrompt.contains("对方: 今晚要不要一起吃饭？"))
        assertFalse(fullContextRequest.userPrompt.contains("\u0007"))

        val latestFriendOnlyRequest = DefaultPromptBuilder.build(
            context = context,
            settings = AppSettings(
                candidateCount = 2,
                contextSendPolicy = ContextSendPolicy.LATEST_FRIEND_MESSAGE
            ),
            styleProfile = ReplyStyleCatalog.profile(
                modeId = ReplyStyleMode.QUICK_REPLY.id,
                personaId = ReplyPersona.DOMINANT_CEO.id,
                sceneId = ReplyStyleCatalog.defaultScene.sceneId,
                polishGoalId = PolishGoal.default.id
            )
        )

        assertTrue(latestFriendOnlyRequest.userPrompt.contains("最近一条对方消息："))
        assertTrue(latestFriendOnlyRequest.userPrompt.contains("别放我鸽子呀"))
        assertFalse(latestFriendOnlyRequest.userPrompt.contains("聊天上下文："))
        assertFalse(latestFriendOnlyRequest.userPrompt.contains("我: 刚忙完"))

        val polishRequest = DefaultPromptBuilder.build(
            context = context,
            settings = AppSettings(candidateCount = 3),
            styleProfile = ReplyStyleCatalog.profile(
                modeId = ReplyStyleMode.POLISH.id,
                personaId = ReplyPersona.WARM_GENTLE.id,
                sceneId = ReplyStyleCatalog.defaultScene.sceneId,
                polishGoalId = PolishGoal.FLIRTY.id
            ),
            draftText = "那你定地方，我下班过去"
        )

        assertTrue(polishRequest.systemPrompt.contains("当前模式：润色表达。"))
        assertTrue(polishRequest.userPrompt.contains("润色目标：更暧昧"))
        assertTrue(polishRequest.userPrompt.contains("不新增事实、不替用户承诺现实动作"))
        assertTrue(polishRequest.userPrompt.contains("草稿："))
        assertTrue(polishRequest.userPrompt.contains("那你定地方，我下班过去"))
    }

    @Test(timeout = 15_000)
    fun local_fallback_generator_produces_ranked_persona_aware_candidates() {
        val inviteContext = ChatContext(
            messages = listOf(
                ChatMessage(
                    id = "invite-1",
                    role = ChatRole.FRIEND,
                    content = "今晚一起吃饭吗？",
                    timestamp = 1L,
                    source = MessageSource.ACCESSIBILITY,
                    confidence = 0.99f
                )
            ),
            targetApp = "com.tencent.mm",
            conversationType = ConversationType.SINGLE_CHAT,
            collectedAt = 2L
        )

        val dominantInviteCandidates = LocalFallbackReplyGenerator.generate(
            context = inviteContext,
            styleProfile = ReplyStyleCatalog.profile(
                modeId = ReplyStyleMode.QUICK_REPLY.id,
                personaId = ReplyPersona.DOMINANT_CEO.id,
                sceneId = ReplyStyleCatalog.defaultScene.sceneId,
                polishGoalId = PolishGoal.default.id
            ),
            seed = "device-regression-local-fallback"
        )

        assertLocalFallbackCandidates(dominantInviteCandidates, expectedShortLabel = "霸道总裁")
        assertTrue(dominantInviteCandidates[0].text.startsWith("可以"))
        assertFalse(dominantInviteCandidates[0].text.startsWith("可以啊"))
        assertTrue(dominantInviteCandidates[0].id.startsWith("local_"))

        val playbookCandidates = LocalFallbackReplyGenerator.generate(
            context = inviteContext,
            styleProfile = ReplyStyleCatalog.profile(
                modeId = ReplyStyleMode.PLAYBOOK.id,
                personaId = ReplyPersona.WARM_GENTLE.id,
                sceneId = "good_night",
                polishGoalId = PolishGoal.default.id
            ),
            seed = "device-regression-playbook"
        )

        assertLocalFallbackCandidates(playbookCandidates, expectedShortLabel = "晚安")
        assertTrue(playbookCandidates.any { it.text.contains("晚安") })

        val polishCandidates = LocalFallbackReplyGenerator.generate(
            context = inviteContext,
            styleProfile = ReplyStyleCatalog.profile(
                modeId = ReplyStyleMode.POLISH.id,
                personaId = ReplyPersona.WARM_GENTLE.id,
                sceneId = ReplyStyleCatalog.defaultScene.sceneId,
                polishGoalId = PolishGoal.FLIRTY.id
            ),
            draftText = "那我忙完去找你",
            seed = "device-regression-polish"
        )

        assertLocalFallbackCandidates(polishCandidates, expectedShortLabel = "更暧昧")
        assertTrue(polishCandidates.all { it.text.contains("那我忙完去找你") })
        assertTrue(polishCandidates.any { it.text.contains("心动") || it.text.contains("犯规") })
    }

    @Test(timeout = 15_000)
    fun llm_debug_store_tracks_state_transitions_and_hints() {
        assertEquals(LlmDebugPhase.IDLE, LlmDebugStore.state.value.phase)
        val initialHistorySize = LlmDebugStore.state.value.history.size

        LlmDebugStore.onSkipped(
            baseUrl = "https://example.com/v1",
            model = "gpt-device-test",
            reason = "Missing API Key"
        )

        val skippedState = LlmDebugStore.state.value
        assertEquals(LlmDebugPhase.SKIPPED, skippedState.phase)
        assertEquals(LlmFailureCategory.CONFIG, skippedState.failureCategory)
        assertEquals("Missing API Key", skippedState.errorSummary)
        assertTrue(skippedState.recoveryHint.contains("API Key"))
        assertEquals(LlmDebugPhase.SKIPPED, skippedState.history.first().phase)

        LlmDebugStore.onRequestStarted(
            baseUrl = "https://example.com/v1",
            model = "gpt-device-test",
            requestPreview = "  hello\n   world  "
        )

        val requestingState = LlmDebugStore.state.value
        assertEquals(LlmDebugPhase.REQUESTING, requestingState.phase)
        assertEquals("hello world", requestingState.requestPreview)
        assertEquals(skippedState.history.size, requestingState.history.size)

        LlmDebugStore.onHttpReturned(
            status = 429,
            responseText = "  too\n many\t requests  "
        )

        val httpState = LlmDebugStore.state.value
        assertEquals(LlmDebugPhase.HTTP_RETURNED, httpState.phase)
        assertEquals(429, httpState.httpStatus)
        assertEquals("too many requests", httpState.responsePreview)
        assertTrue(httpState.recoveryHint.contains("429") || httpState.recoveryHint.contains("频率"))

        LlmDebugStore.onFailed(SocketTimeoutException("timed out"))

        val failedState = LlmDebugStore.state.value
        assertEquals(LlmDebugPhase.FAILED, failedState.phase)
        assertEquals(LlmFailureCategory.NETWORK, failedState.failureCategory)
        assertEquals("timed out", failedState.errorSummary)
        assertTrue(failedState.recoveryHint.contains("网络") || failedState.recoveryHint.contains("代理"))
        assertEquals(LlmDebugPhase.FAILED, failedState.history.first().phase)

        LlmDebugStore.onParsed(
            candidateCount = 2,
            contentPreview = "  {\n \"candidates\": []\n }  "
        )

        val parsedState = LlmDebugStore.state.value
        assertEquals(LlmDebugPhase.PARSED, parsedState.phase)
        assertEquals(LlmFailureCategory.NONE, parsedState.failureCategory)
        assertEquals(2, parsedState.candidateCount)
        assertEquals("{ \"candidates\": [] }", parsedState.responsePreview)
        assertTrue(parsedState.recoveryHint.contains("连接和解析正常"))
        assertEquals(LlmDebugPhase.PARSED, parsedState.history.first().phase)
        assertTrue(parsedState.history.size >= initialHistorySize.coerceAtMost(8))
        assertTrue(parsedState.history.size <= 8)
    }

    @Test(timeout = 15_000)
    fun overlay_diagnostics_store_tracks_flow_and_failure_state() {
        assertEquals(OverlayRunPhase.IDLE, OverlayDiagnosticsStore.state.value.phase)
        OverlayDiagnosticsStore.begin()

        val beginState = OverlayDiagnosticsStore.state.value
        assertEquals(OverlayRunPhase.VALIDATING, beginState.phase)
        assertTrue(beginState.status.contains("开始校验当前页面"))
        assertEquals(1, beginState.steps.size)
        assertTrue(beginState.steps.first().contains("点击 AI 气泡"))

        OverlayDiagnosticsStore.onContext(
            accessibilityMessageCount = 2,
            ocrMessageCount = 1,
            mergedMessageCount = 3,
            usedOcr = true
        )

        val contextState = OverlayDiagnosticsStore.state.value
        assertEquals(OverlayRunPhase.BUILDING_CONTEXT, contextState.phase)
        assertEquals(2, contextState.accessibilityMessageCount)
        assertEquals(1, contextState.ocrMessageCount)
        assertEquals(3, contextState.mergedMessageCount)
        assertTrue(contextState.usedOcr)
        assertTrue(contextState.status.contains("Accessibility 2 条"))
        assertTrue(contextState.steps.last().contains("merged=3"))

        OverlayDiagnosticsStore.onCandidates(
            count = 3,
            usedLocalFallback = true,
            candidateSource = "本地兜底"
        )

        val candidateState = OverlayDiagnosticsStore.state.value
        assertEquals(OverlayRunPhase.CANDIDATE_READY, candidateState.phase)
        assertEquals(3, candidateState.candidateCount)
        assertEquals("本地兜底", candidateState.candidateSource)
        assertTrue(candidateState.usedLocalFallback)
        assertNull(candidateState.lastFailure)
        assertTrue(candidateState.status.contains("本地兜底候选"))

        OverlayDiagnosticsStore.onAutofill("已尝试填入输入框")
        val autofillState = OverlayDiagnosticsStore.state.value
        assertEquals(OverlayRunPhase.AUTOFILLING, autofillState.phase)
        assertEquals("已尝试填入输入框", autofillState.status)
        assertTrue(autofillState.steps.last().contains("填入"))

        OverlayDiagnosticsStore.onDone("候选已展示完成")
        val doneState = OverlayDiagnosticsStore.state.value
        assertEquals(OverlayRunPhase.DONE, doneState.phase)
        assertEquals("候选已展示完成", doneState.status)
        assertTrue(doneState.steps.last().contains("完成"))

        OverlayDiagnosticsStore.onFailed("输入框不可见")
        val failedState = OverlayDiagnosticsStore.state.value
        assertEquals(OverlayRunPhase.FAILED, failedState.phase)
        assertEquals("输入框不可见", failedState.status)
        assertEquals("输入框不可见", failedState.lastFailure)
        assertTrue(failedState.steps.last().contains("失败"))
        assertTrue(failedState.steps.size in 1..16)
    }

    @Test(timeout = 15_000)
    fun diagnostic_log_store_persists_sanitizes_and_deduplicates_entries() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        try {
            val llmTimestamp = 1_717_171_000_000L
            val llmState = LlmDebugState(
                phase = LlmDebugPhase.PARSED,
                baseUrl = "https://example.com/v1/chat/completions?apiKey=secret&token=123",
                model = "gpt-diagnostic-test",
                candidateCount = 2,
                failureCategory = LlmFailureCategory.NONE,
                recoveryHint = "连接和解析正常，可以回到微信单聊页使用 AI 气泡。",
                updatedAtMillis = llmTimestamp
            )
            DiagnosticLogStore.recordLlm(llmState)
            DiagnosticLogStore.recordLlm(llmState)

            DiagnosticLogStore.recordOcr(
                OcrDebugState(
                    engineConfigured = true,
                    lastCategory = OcrAttemptCategory.SUCCESS,
                    lastStatus = "识别到 1 条消息",
                    targetApp = "com.tencent.mm",
                    extractedMessages = listOf(
                        ChatMessage(
                            id = "ocr-1",
                            role = ChatRole.FRIEND,
                            content = "今天几点见？",
                            timestamp = 2L,
                            source = MessageSource.OCR,
                            confidence = 0.81f
                        )
                    ),
                    updatedAtMillis = llmTimestamp + 1_000L
                )
            )

            DiagnosticLogStore.recordOverlay(
                OverlayDiagnosticsState(
                    phase = OverlayRunPhase.FAILED,
                    status = "本次失败",
                    accessibilityMessageCount = 2,
                    ocrMessageCount = 1,
                    mergedMessageCount = 3,
                    candidateCount = 0,
                    candidateSource = "暂无",
                    usedLocalFallback = false,
                    lastFailure = "输入框不可见",
                    updatedAtMillis = llmTimestamp + 2_000L
                )
            )

            val inMemoryState = DiagnosticLogStore.state.value
            assertEquals(3, inMemoryState.entries.size)
            assertEquals(DiagnosticLogKind.OVERLAY, inMemoryState.entries[0].kind)
            assertEquals(DiagnosticLogKind.OCR, inMemoryState.entries[1].kind)
            assertEquals(DiagnosticLogKind.LLM, inMemoryState.entries[2].kind)

            DiagnosticLogStore.initialize(context)
            val reloadedState = DiagnosticLogStore.state.value
            assertEquals(3, reloadedState.entries.size)

            val snapshot = DiagnosticLogStore.buildSnapshot()
            assertTrue(snapshot.contains("AiReplayMate Diagnostic Log Snapshot"))
            assertTrue(snapshot.contains("count=3"))
            assertTrue(snapshot.contains("LLM 解析成功 / 无"))
            assertTrue(snapshot.contains("OCR 成功"))
            assertTrue(snapshot.contains("悬浮窗 失败"))
            assertTrue(snapshot.contains("baseUrl=https://example.com/v1/chat/completions"))
            assertFalse(snapshot.contains("apiKey=secret"))
            assertTrue(snapshot.contains("messages=1"))
            assertTrue(snapshot.contains("summary=输入框不可见"))
        } finally {
            DiagnosticLogStore.clear()
        }
    }

    @Test(timeout = 15_000)
    fun ocr_debug_store_tracks_attempt_result_and_message_previews() {
        assertEquals(OcrAttemptCategory.NONE, OcrDebugStore.state.value.lastCategory)
        val successMessages = listOf(
            ChatMessage(
                id = "ocr-preview-1",
                role = ChatRole.FRIEND,
                content = "今天几点见？",
                timestamp = 1L,
                source = MessageSource.OCR,
                confidence = 0.81f
            ),
            ChatMessage(
                id = "ocr-preview-2",
                role = ChatRole.ME,
                content = "八点以后都可以",
                timestamp = 2L,
                source = MessageSource.OCR,
                confidence = 0.93f
            )
        )

        OcrDebugStore.onAttempt(
            targetApp = "com.tencent.mm",
            reason = "Accessibility 上下文不足",
            result = OcrAttemptResult(
                success = true,
                category = OcrAttemptCategory.SUCCESS,
                message = "识别到 2 条消息",
                messages = successMessages,
                steps = listOf("截图成功", "OCR 成功"),
                engineConfigured = true
            )
        )

        val successState = OcrDebugStore.state.value
        assertTrue(successState.engineConfigured)
        assertEquals(OcrAttemptCategory.SUCCESS, successState.lastCategory)
        assertEquals("识别到 2 条消息", successState.lastStatus)
        assertEquals("Accessibility 上下文不足", successState.lastReason)
        assertEquals("com.tencent.mm", successState.targetApp)
        assertEquals(2, successState.extractedMessages.size)
        assertEquals(listOf("截图成功", "OCR 成功"), successState.steps)
        assertTrue(successState.updatedAtMillis > 0L)
        assertEquals(2, successState.extractedMessagePreviews.size)
        assertTrue(successState.extractedMessagePreviews[0].contains("FRIEND 81% 今天几点见？"))
        assertTrue(successState.extractedMessagePreviews[1].contains("ME 93% 八点以后都可以"))

        OcrDebugStore.onAttempt(
            targetApp = "com.tencent.mm",
            reason = "截图不可用",
            result = OcrAttemptResult(
                success = false,
                category = OcrAttemptCategory.CAPTURE_UNAVAILABLE,
                message = "当前无法截图",
                steps = listOf("MediaProjection 未授权"),
                engineConfigured = true
            )
        )

        val failureState = OcrDebugStore.state.value
        assertEquals(OcrAttemptCategory.CAPTURE_UNAVAILABLE, failureState.lastCategory)
        assertEquals("当前无法截图", failureState.lastStatus)
        assertEquals("截图不可用", failureState.lastReason)
        assertTrue(failureState.engineConfigured)
        assertTrue(failureState.extractedMessages.isEmpty())
        assertTrue(failureState.extractedMessagePreviews.isEmpty())
        assertEquals(listOf("MediaProjection 未授权"), failureState.steps)
    }

    private fun assertLocalFallbackCandidates(
        candidates: List<ReplyCandidate>,
        expectedShortLabel: String
    ) {
        assertEquals(3, candidates.size)
        assertEquals(1, candidates[0].rank)
        assertEquals(2, candidates[1].rank)
        assertEquals(3, candidates[2].rank)
        assertTrue(candidates.all { it.id.isNotBlank() })
        assertTrue(candidates.all { it.text.isNotBlank() })
        assertTrue(candidates.all { it.sourceModel == null })
        assertTrue(candidates.all { (it.tone ?: "").contains(expectedShortLabel) })
    }
}