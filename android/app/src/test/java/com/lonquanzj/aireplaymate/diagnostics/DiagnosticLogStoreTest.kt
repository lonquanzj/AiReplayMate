package com.lonquanzj.aireplaymate.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lonquanzj.aireplaymate.llm.LlmDebugPhase
import com.lonquanzj.aireplaymate.llm.LlmDebugState
import com.lonquanzj.aireplaymate.llm.LlmFailureCategory
import com.lonquanzj.aireplaymate.ocr.OcrAttemptCategory
import com.lonquanzj.aireplaymate.ocr.OcrDebugState
import com.lonquanzj.aireplaymate.overlay.OverlayDiagnosticsState
import com.lonquanzj.aireplaymate.overlay.OverlayRunPhase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiagnosticLogStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        DiagnosticLogStore.initialize(context)
        DiagnosticLogStore.clear()
    }

    @After
    fun tearDown() {
        DiagnosticLogStore.clear()
    }

    @Test
    fun records_only_terminal_states_and_sanitizes_llm_base_url() {
        DiagnosticLogStore.recordLlm(
            LlmDebugState(
                phase = LlmDebugPhase.REQUESTING,
                updatedAtMillis = 100L
            )
        )
        DiagnosticLogStore.recordLlm(
            LlmDebugState(
                phase = LlmDebugPhase.FAILED,
                failureCategory = LlmFailureCategory.HTTP,
                baseUrl = "https://user:secret@example.test/v1/chat/completions?token=secret",
                model = "gpt-test",
                httpStatus = 401,
                errorSummary = "HTTP 401",
                recoveryHint = "check key",
                updatedAtMillis = 101L
            )
        )

        val entries = DiagnosticLogStore.state.value.entries

        assertEquals(1, entries.size)
        assertEquals(DiagnosticLogKind.LLM, entries.single().kind)
        assertTrue(entries.single().metadata.contains("https://example.test/v1/chat/completions"))
        assertFalse(entries.single().metadata.contains("secret"))
    }

    @Test
    fun deduplicates_entries_persists_them_and_keeps_latest_60() {
        repeat(65) { index ->
            DiagnosticLogStore.recordOcr(
                OcrDebugState(
                    engineConfigured = true,
                    lastCategory = OcrAttemptCategory.SUCCESS,
                    lastStatus = "status $index",
                    targetApp = "wechat",
                    updatedAtMillis = 1_000L + index
                )
            )
        }
        val duplicate = OverlayDiagnosticsState(
            phase = OverlayRunPhase.FAILED,
            status = "failed",
            lastFailure = "failed",
            updatedAtMillis = 9_999L
        )
        DiagnosticLogStore.recordOverlay(duplicate)
        DiagnosticLogStore.recordOverlay(duplicate)

        DiagnosticLogStore.initialize(context)
        val entries = DiagnosticLogStore.state.value.entries

        assertEquals(60, entries.size)
        assertEquals(DiagnosticLogKind.OVERLAY, entries.first().kind)
        assertEquals(1, entries.count { it.id == "overlay_9999_FAILED" })
        assertFalse(entries.any { it.summary == "status 0" })
    }

    private companion object {
        const val PREF_NAME = "diagnostic_log_store"
    }
}
