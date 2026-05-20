package com.lonquanzj.aireplaymate.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lonquanzj.aireplaymate.accessibility.AccessibilityDebugState
import com.lonquanzj.aireplaymate.accessibility.ChatMessage
import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.chatMessage
import com.lonquanzj.aireplaymate.context.ChatContext
import com.lonquanzj.aireplaymate.context.ContextBuilder
import com.lonquanzj.aireplaymate.context.DefaultContextBuilder
import com.lonquanzj.aireplaymate.llm.LlmGateway
import com.lonquanzj.aireplaymate.ocr.OcrAttemptCategory
import com.lonquanzj.aireplaymate.ocr.OcrAttemptResult
import com.lonquanzj.aireplaymate.ocr.OcrEngine
import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.LlmRequest
import com.lonquanzj.aireplaymate.prompt.PromptBuilder
import com.lonquanzj.aireplaymate.prompt.ReplyCandidate
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalog
import com.lonquanzj.aireplaymate.prompt.ReplyStyleMode
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RealReplySessionRunnerTest {
    private val appContext: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun run_fails_when_accessibility_service_is_not_connected() = runTest {
        val result = newRunner().run(
            debugState = debugState(serviceConnected = false),
            settings = AppSettings(apiKey = "sk", model = "gpt", baseUrl = "https://api.openai.com")
        )

        assertTrue(result.isFailure)
        assertEquals("无障碍服务未连接", result.exceptionOrNull()?.message)
    }

    @Test
    fun run_fails_when_not_in_wechat_or_not_chat_page() = runTest {
        val notWechat = newRunner().run(
            debugState = debugState(isWechatPackage = false),
            settings = AppSettings(apiKey = "sk", model = "gpt", baseUrl = "https://api.openai.com")
        )
        val notChatPage = newRunner().run(
            debugState = debugState(looksLikeChatPage = false),
            settings = AppSettings(apiKey = "sk", model = "gpt", baseUrl = "https://api.openai.com")
        )

        assertTrue(notWechat.isFailure)
        assertEquals("当前不在微信页面", notWechat.exceptionOrNull()?.message)
        assertTrue(notChatPage.isFailure)
        assertEquals("当前不像微信单聊页", notChatPage.exceptionOrNull()?.message)
    }

    @Test
    fun run_forQuickReply_with_enough_context_skips_ocr_and_uses_llm_candidates() = runTest {
        val ocrEngine = FakeOcrEngine(successMessages = listOf(chatMessage("o1", content = "OCR 消息")))
        val llmGateway = FakeLlmGateway(
            Result.success(
                listOf(
                    ReplyCandidate(id = "1", text = "可以呀", rank = 1),
                    ReplyCandidate(id = "2", text = "晚点聊", rank = 2)
                )
            )
        )
        val phases = mutableListOf<RealReplySessionPhase>()
        val snapshots = mutableListOf<RealReplySessionContextSnapshot>()

        val result = newRunner(
            ocrEngine = ocrEngine,
            llmGateway = llmGateway
        ).run(
            debugState = debugState(
                extractedMessages = listOf(
                    chatMessage("m1", role = ChatRole.FRIEND, content = "今晚有空吗？")
                )
            ),
            settings = AppSettings(apiKey = "sk", model = "gpt", baseUrl = "https://api.openai.com"),
            onPhase = { phase, _ -> phases += phase },
            onContext = { snapshots += it }
        )

        assertTrue(result.isSuccess)
        val session = result.getOrNull()
        assertNotNull(session)
        assertEquals(0, ocrEngine.calls)
        assertFalse(session!!.usedOcr)
        assertFalse(session.usedLocalFallback)
        assertEquals("LLM · 细腻暖男", session.candidateSource)
        assertEquals(listOf("细腻暖男", "细腻暖男"), session.candidates.map { it.tone })
        assertEquals(
            listOf(
                RealReplySessionPhase.VALIDATING,
                RealReplySessionPhase.BUILDING_CONTEXT,
                RealReplySessionPhase.REQUESTING_LLM
            ),
            phases
        )
        assertEquals(1, snapshots.size)
    }

    @Test
    fun run_forQuickReply_with_insufficient_context_uses_ocr_then_llm() = runTest {
        val ocrEngine = FakeOcrEngine(
            successMessages = listOf(
                chatMessage("o1", role = ChatRole.FRIEND, content = "我到了", source = com.lonquanzj.aireplaymate.accessibility.MessageSource.OCR)
            )
        )
        val llmGateway = FakeLlmGateway(
            Result.success(listOf(ReplyCandidate(id = "1", text = "我马上来", rank = 1)))
        )
        val phases = mutableListOf<RealReplySessionPhase>()
        val snapshots = mutableListOf<RealReplySessionContextSnapshot>()

        val result = newRunner(
            ocrEngine = ocrEngine,
            llmGateway = llmGateway
        ).run(
            debugState = debugState(
                extractedMessages = listOf(
                    chatMessage("m1", role = ChatRole.ME, content = "我刚下班")
                )
            ),
            settings = AppSettings(apiKey = "sk", model = "gpt", baseUrl = "https://api.openai.com"),
            onPhase = { phase, _ -> phases += phase },
            onContext = { snapshots += it }
        )

        assertTrue(result.isSuccess)
        val session = result.getOrNull()!!
        assertEquals(1, ocrEngine.calls)
        assertTrue(session.usedOcr)
        assertTrue(session.candidateSource.contains("含 OCR 上下文"))
        assertEquals(
            listOf(
                RealReplySessionPhase.VALIDATING,
                RealReplySessionPhase.BUILDING_CONTEXT,
                RealReplySessionPhase.OCR_FALLBACK,
                RealReplySessionPhase.REQUESTING_LLM
            ),
            phases
        )
        assertEquals(2, snapshots.size)
        assertEquals(0, snapshots.first().ocrMessageCount)
        assertEquals(1, snapshots.last().ocrMessageCount)
    }

    @Test
    fun run_fails_when_ocr_still_cannot_build_enough_context() = runTest {
        val ocrEngine = FakeOcrEngine(successMessages = emptyList(), successMessage = "OCR 未识别到有效聊天内容")

        val result = newRunner(ocrEngine = ocrEngine).run(
            debugState = debugState(
                extractedMessages = listOf(chatMessage("m1", role = ChatRole.ME, content = "我在路上"))
            ),
            settings = AppSettings(apiKey = "sk", model = "gpt", baseUrl = "https://api.openai.com")
        )

        assertTrue(result.isFailure)
        assertEquals("上下文不足，且 OCR 未识别到有效聊天内容", result.exceptionOrNull()?.message)
    }

    @Test
    fun run_forPlaybook_and_polish_skips_ocr_even_when_context_is_insufficient() = runTest {
        val styles = listOf(
            ReplyStyleProfile(
                mode = ReplyStyleMode.PLAYBOOK,
                playbookScene = ReplyStyleCatalog.sceneFromId("good_morning")
            ),
            ReplyStyleProfile(mode = ReplyStyleMode.POLISH)
        )

        styles.forEach { style ->
            val ocrEngine = FakeOcrEngine(successMessages = listOf(chatMessage("o1", content = "不会被用到")))
            val llmGateway = FakeLlmGateway(
                Result.success(listOf(ReplyCandidate(id = "1", text = "候选", rank = 1)))
            )

            val result = newRunner(
                ocrEngine = ocrEngine,
                llmGateway = llmGateway
            ).run(
                debugState = debugState(extractedMessages = emptyList()),
                settings = AppSettings(apiKey = "sk", model = "gpt", baseUrl = "https://api.openai.com"),
                styleProfile = style,
                draftText = "原始草稿"
            )

            assertTrue(result.isSuccess)
            assertEquals(0, ocrEngine.calls)
        }
    }

    @Test
    fun run_falls_back_to_local_candidates_when_llm_fails() = runTest {
        val llmGateway = FakeLlmGateway(Result.failure(IllegalStateException("网络异常")))
        val phases = mutableListOf<RealReplySessionPhase>()

        val result = newRunner(llmGateway = llmGateway).run(
            debugState = debugState(
                extractedMessages = listOf(
                    chatMessage("m1", role = ChatRole.FRIEND, content = "你到哪了")
                )
            ),
            settings = AppSettings(apiKey = "sk", model = "gpt", baseUrl = "https://api.openai.com"),
            onPhase = { phase, _ -> phases += phase }
        )

        assertTrue(result.isSuccess)
        val session = result.getOrNull()!!
        assertTrue(session.usedLocalFallback)
        assertEquals("网络异常", session.localFallbackReason)
        assertTrue(session.candidateSource.startsWith("本地兜底"))
        assertTrue(session.candidates.isNotEmpty())
        assertEquals(
            listOf(
                RealReplySessionPhase.VALIDATING,
                RealReplySessionPhase.BUILDING_CONTEXT,
                RealReplySessionPhase.REQUESTING_LLM,
                RealReplySessionPhase.LOCAL_FALLBACK
            ),
            phases
        )
    }

    private fun newRunner(
        contextBuilder: ContextBuilder = DefaultContextBuilder,
        promptBuilder: PromptBuilder = StaticPromptBuilder,
        ocrEngine: FakeOcrEngine = FakeOcrEngine(successMessages = emptyList()),
        llmGateway: FakeLlmGateway = FakeLlmGateway(
            Result.success(listOf(ReplyCandidate(id = "1", text = "默认候选", rank = 1)))
        )
    ): RealReplySessionRunner {
        return RealReplySessionRunner(
            context = appContext,
            contextBuilder = contextBuilder,
            promptBuilder = promptBuilder,
            ocrEngineFactory = { ocrEngine },
            llmGatewayFactory = { llmGateway }
        )
    }

    private fun debugState(
        serviceConnected: Boolean = true,
        isWechatPackage: Boolean = true,
        looksLikeChatPage: Boolean = true,
        extractedMessages: List<ChatMessage> = emptyList()
    ): AccessibilityDebugState {
        return AccessibilityDebugState(
            serviceConnected = serviceConnected,
            isWechatPackage = isWechatPackage,
            looksLikeChatPage = looksLikeChatPage,
            extractedMessages = extractedMessages
        )
    }

    private object StaticPromptBuilder : PromptBuilder {
        override fun build(
            context: ChatContext,
            settings: AppSettings,
            styleProfile: ReplyStyleProfile,
            draftText: String?
        ): LlmRequest {
            return LlmRequest(
                systemPrompt = "sys",
                userPrompt = "user:${context.messages.size}:${draftText.orEmpty()}",
                temperature = 0.7f,
                maxTokens = 200,
                candidateCount = settings.candidateCount.coerceAtLeast(1)
            )
        }
    }

    private class FakeOcrEngine(
        private val successMessages: List<ChatMessage>,
        private val successMessage: String = "OCR 成功"
    ) : OcrEngine {
        var calls: Int = 0
            private set

        override suspend fun recognizeChatMessages(
            targetApp: String,
            reason: String
        ): OcrAttemptResult {
            calls += 1
            return OcrAttemptResult(
                success = successMessages.isNotEmpty(),
                category = if (successMessages.isNotEmpty()) {
                    OcrAttemptCategory.SUCCESS
                } else {
                    OcrAttemptCategory.NO_TEXT
                },
                message = successMessage,
                messages = successMessages
            )
        }
    }

    private class FakeLlmGateway(
        private val nextResult: Result<List<ReplyCandidate>>
    ) : LlmGateway {
        val requests = mutableListOf<LlmRequest>()

        override suspend fun generateReplies(request: LlmRequest): Result<List<ReplyCandidate>> {
            requests += request
            return nextResult
        }
    }
}
