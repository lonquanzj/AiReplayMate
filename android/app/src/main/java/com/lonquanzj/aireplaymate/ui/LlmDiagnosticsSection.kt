package com.lonquanzj.aireplaymate.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lonquanzj.aireplaymate.llm.LlmDebugState

@Composable
internal fun LlmDiagnosticsSection(debugState: LlmDebugState) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("LLM 诊断")

        SoftPanelCard(
            cornerRadius = 18.dp,
            contentPadding = PaddingValues(14.dp)
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

                SoftOutlinedAction(
                    text = "复制 LLM 诊断",
                    onClick = {
                        clipboardManager.setText(
                            AnnotatedString(buildLlmDebugSnapshot(debugState))
                        )
                        Toast.makeText(context, "已复制 LLM 诊断", Toast.LENGTH_SHORT).show()
                    },
                    enabled = debugState.updatedAtMillis > 0L || debugState.history.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                )

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
                            color = SoftSecondaryText
                        )
                    }
                }
        }
    }
}
