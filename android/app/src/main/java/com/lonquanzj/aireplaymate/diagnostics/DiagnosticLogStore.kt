package com.lonquanzj.aireplaymate.diagnostics

import android.content.Context
import com.lonquanzj.aireplaymate.llm.LlmDebugState
import com.lonquanzj.aireplaymate.llm.LlmDebugPhase
import com.lonquanzj.aireplaymate.ocr.OcrDebugState
import com.lonquanzj.aireplaymate.overlay.OverlayDiagnosticsState
import com.lonquanzj.aireplaymate.overlay.OverlayRunPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DiagnosticLogKind(
    val label: String
) {
    LLM("LLM"),
    OCR("OCR"),
    OVERLAY("悬浮窗")
}

data class DiagnosticLogEntry(
    val id: String,
    val kind: DiagnosticLogKind,
    val title: String,
    val summary: String,
    val hint: String,
    val metadata: String,
    val timestampMillis: Long
)

data class DiagnosticLogState(
    val entries: List<DiagnosticLogEntry> = emptyList(),
    val updatedAtMillis: Long = 0L
)

object DiagnosticLogStore {
    private val _state = MutableStateFlow(DiagnosticLogState())
    val state: StateFlow<DiagnosticLogState> = _state.asStateFlow()

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
        _state.value = DiagnosticLogState(
            entries = loadEntries(context.applicationContext),
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    fun recordLlm(state: LlmDebugState) {
        if (state.updatedAtMillis <= 0L || state.phase !in LLM_TERMINAL_PHASES) return

        record(
            DiagnosticLogEntry(
                id = "llm_${state.updatedAtMillis}_${state.phase.name}",
                kind = DiagnosticLogKind.LLM,
                title = "${state.phase.label} / ${state.failureCategory.label}",
                summary = state.errorSummary ?: "候选数：${state.candidateCount}",
                hint = state.recoveryHint,
                metadata = listOf(
                    "http=${state.httpStatus ?: "N/A"}",
                    "model=${state.model.ifBlank { "N/A" }}",
                    "baseUrl=${state.baseUrl.sanitizedUrl()}",
                    "candidates=${state.candidateCount}"
                ).joinToString(", "),
                timestampMillis = state.updatedAtMillis
            )
        )
    }

    fun recordOcr(state: OcrDebugState) {
        if (state.updatedAtMillis <= 0L) return

        record(
            DiagnosticLogEntry(
                id = "ocr_${state.updatedAtMillis}_${state.lastCategory.name}",
                kind = DiagnosticLogKind.OCR,
                title = state.lastCategory.label,
                summary = state.lastStatus,
                hint = if (state.extractedMessages.isEmpty()) {
                    "OCR 未产出可用消息时，可确认截图授权是否仍有效，并切回微信单聊页后重试。"
                } else {
                    "OCR 已产出候选消息，可继续观察是否需要后续真机样本调参。"
                },
                metadata = listOf(
                    "target=${state.targetApp.ifBlank { "N/A" }}",
                    "messages=${state.extractedMessages.size}",
                    "engine=${state.engineConfigured}"
                ).joinToString(", "),
                timestampMillis = state.updatedAtMillis
            )
        )
    }

    fun recordOverlay(state: OverlayDiagnosticsState) {
        if (state.updatedAtMillis <= 0L || state.phase !in OVERLAY_TERMINAL_PHASES) return

        record(
            DiagnosticLogEntry(
                id = "overlay_${state.updatedAtMillis}_${state.phase.name}",
                kind = DiagnosticLogKind.OVERLAY,
                title = state.phase.label,
                summary = state.lastFailure ?: state.status,
                hint = when {
                    state.lastFailure != null -> "请结合悬浮窗诊断步骤检查当前是否在微信单聊页、无障碍是否连接、输入框是否可见。"
                    state.usedLocalFallback -> "本次已降级到本地候选，可继续检查 LLM 诊断定位模型或网络问题。"
                    else -> "悬浮窗链路已跑通，可重点观察填入效果和候选质量。"
                },
                metadata = listOf(
                    "a11y=${state.accessibilityMessageCount}",
                    "ocr=${state.ocrMessageCount}",
                    "merged=${state.mergedMessageCount}",
                    "candidates=${state.candidateCount}",
                    "source=${state.candidateSource}"
                ).joinToString(", "),
                timestampMillis = state.updatedAtMillis
            )
        )
    }

    fun clear() {
        _state.value = DiagnosticLogState(updatedAtMillis = System.currentTimeMillis())
        appContext?.prefs()?.edit()?.remove(KEY_ENTRIES)?.apply()
    }

    fun buildSnapshot(): String {
        val entries = _state.value.entries
        return buildString {
            appendLine("AiReplayMate Diagnostic Log Snapshot")
            appendLine("count=${entries.size}")
            appendLine()
            if (entries.isEmpty()) {
                appendLine("N/A")
            } else {
                entries.forEachIndexed { index, entry ->
                    appendLine("${index + 1}. ${entry.kind.label} ${entry.title}")
                    appendLine("time=${entry.timestampMillis.toReadableTime()}")
                    appendLine("summary=${entry.summary}")
                    appendLine("hint=${entry.hint}")
                    appendLine("metadata=${entry.metadata}")
                    appendLine()
                }
            }
        }
    }

    private fun record(entry: DiagnosticLogEntry) {
        val context = appContext ?: return
        val current = _state.value.entries
        if (current.firstOrNull()?.id == entry.id) return

        val nextEntries = (listOf(entry) + current)
            .distinctBy { it.id }
            .take(MAX_ENTRIES)
        _state.value = DiagnosticLogState(
            entries = nextEntries,
            updatedAtMillis = System.currentTimeMillis()
        )
        saveEntries(context, nextEntries)
    }

    private fun loadEntries(context: Context): List<DiagnosticLogEntry> {
        val raw = context.prefs().getString(KEY_ENTRIES, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                array.getJSONObject(index).toEntry()
            }
        }.getOrDefault(emptyList())
    }

    private fun saveEntries(
        context: Context,
        entries: List<DiagnosticLogEntry>
    ) {
        val array = JSONArray()
        entries.forEach { entry -> array.put(entry.toJson()) }
        context.prefs().edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private fun DiagnosticLogEntry.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("kind", kind.name)
            .put("title", title)
            .put("summary", summary)
            .put("hint", hint)
            .put("metadata", metadata)
            .put("timestampMillis", timestampMillis)
    }

    private fun JSONObject.toEntry(): DiagnosticLogEntry {
        return DiagnosticLogEntry(
            id = optString("id"),
            kind = runCatching {
                DiagnosticLogKind.valueOf(optString("kind"))
            }.getOrDefault(DiagnosticLogKind.LLM),
            title = optString("title"),
            summary = optString("summary"),
            hint = optString("hint"),
            metadata = optString("metadata"),
            timestampMillis = optLong("timestampMillis")
        )
    }

    private fun Context.prefs() = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun String.sanitizedUrl(): String {
        if (isBlank()) return "N/A"
        return runCatching {
            val uri = URI(trim())
            URI(
                uri.scheme,
                null,
                uri.host,
                uri.port,
                uri.path,
                null,
                null
            ).toString()
        }.getOrDefault(take(80))
    }

    private fun Long.toReadableTime(): String {
        if (this <= 0L) return "N/A"
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(this))
    }

    private val LLM_TERMINAL_PHASES = setOf(
        LlmDebugPhase.SKIPPED,
        LlmDebugPhase.PARSED,
        LlmDebugPhase.FAILED
    )
    private val OVERLAY_TERMINAL_PHASES = setOf(
        OverlayRunPhase.CANDIDATE_READY,
        OverlayRunPhase.DONE,
        OverlayRunPhase.FAILED
    )
    private const val PREF_NAME = "diagnostic_log_store"
    private const val KEY_ENTRIES = "entries"
    private const val MAX_ENTRIES = 60
}
