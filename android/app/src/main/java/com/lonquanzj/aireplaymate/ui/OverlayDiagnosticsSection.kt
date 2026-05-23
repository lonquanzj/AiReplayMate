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
import com.lonquanzj.aireplaymate.overlay.OverlayDiagnosticsState

@Composable
internal fun OverlayDiagnosticsSection(debugState: OverlayDiagnosticsState) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("悬浮窗诊断")

        SoftPanelCard(
            cornerRadius = 18.dp,
            contentPadding = PaddingValues(14.dp)
        ) {
                SectionBody("记录上一次点击微信 AI 气泡后的真实链路，方便定位候选面板不出现或填入失败的原因。")

                StatusRow(label = "阶段", value = debugState.phase.label)
                StatusRow(label = "状态", value = debugState.status)
                StatusRow(
                    label = "上下文",
                    value = "A11y ${debugState.accessibilityMessageCount} / OCR ${debugState.ocrMessageCount} / 合并 ${debugState.mergedMessageCount}"
                )
                StatusRow(
                    label = "候选数",
                    value = debugState.candidateCount.toString()
                )
                StatusRow(
                    label = "候选来源",
                    value = debugState.candidateSource
                )
                StatusRow(
                    label = "使用 OCR",
                    value = if (debugState.usedOcr) "是" else "否"
                )
                StatusRow(
                    label = "本地兜底",
                    value = if (debugState.usedLocalFallback) "是" else "否"
                )
                StatusRow(
                    label = "失败原因",
                    value = debugState.lastFailure ?: "暂无"
                )
                StatusRow(
                    label = "更新时间",
                    value = formatTimestamp(debugState.updatedAtMillis)
                )

                SoftOutlinedAction(
                    text = "复制悬浮窗诊断",
                    onClick = {
                        clipboardManager.setText(
                            AnnotatedString(buildOverlayDebugSnapshot(debugState))
                        )
                        Toast.makeText(context, "已复制悬浮窗诊断", Toast.LENGTH_SHORT).show()
                    },
                    enabled = debugState.updatedAtMillis > 0L,
                    modifier = Modifier.fillMaxWidth()
                )

                if (debugState.steps.isNotEmpty()) {
                    Text(
                        text = "最近步骤",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    debugState.steps.takeLast(8).forEach { step ->
                        Text(
                            text = "- $step",
                            style = MaterialTheme.typography.bodySmall,
                            color = SoftSecondaryText
                        )
                    }
                }
        }
    }
}
