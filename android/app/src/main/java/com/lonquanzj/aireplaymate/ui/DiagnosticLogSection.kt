package com.lonquanzj.aireplaymate.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lonquanzj.aireplaymate.diagnostics.DiagnosticLogState
import com.lonquanzj.aireplaymate.diagnostics.DiagnosticLogStore

@Composable
internal fun DiagnosticLogSection(logState: DiagnosticLogState) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("诊断日志")

        SoftPanelCard(
            cornerRadius = 18.dp,
            contentPadding = PaddingValues(14.dp)
        ) {
                SectionBody("保存最近的 LLM、OCR、悬浮窗摘要日志；不保存截图、不保存 API Key。")
                StatusRow(label = "记录数", value = logState.entries.size.toString())
                StatusRow(label = "更新时间", value = formatTimestamp(logState.updatedAtMillis))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SoftOutlinedAction(
                        text = "复制日志",
                        onClick = {
                            clipboardManager.setText(
                                AnnotatedString(DiagnosticLogStore.buildSnapshot())
                            )
                            Toast.makeText(context, "已复制诊断日志", Toast.LENGTH_SHORT).show()
                        },
                        enabled = logState.entries.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    )

                    SoftOutlinedAction(
                        text = "清空",
                        onClick = {
                            DiagnosticLogStore.clear()
                            Toast.makeText(context, "已清空诊断日志", Toast.LENGTH_SHORT).show()
                        },
                        enabled = logState.entries.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    )
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
                                color = SoftPrimaryText
                            )
                            Text(
                                text = "摘要：${entry.summary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SoftSecondaryText
                            )
                            Text(
                                text = "建议：${entry.hint}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SoftSecondaryText
                            )
                            Text(
                                text = "元数据：${entry.metadata}",
                                style = MaterialTheme.typography.bodySmall,
                                color = SoftSecondaryText
                            )
                        }
                    }
                }
        }
    }
}
