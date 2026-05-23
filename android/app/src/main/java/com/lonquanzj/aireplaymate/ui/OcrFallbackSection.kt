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
import com.lonquanzj.aireplaymate.ocr.OcrDebugState
import com.lonquanzj.aireplaymate.ocr.OcrScreenCaptureState

@Composable
internal fun OcrFallbackSection(
    debugState: OcrDebugState,
    screenCaptureState: OcrScreenCaptureState,
    isTestingScreenCapture: Boolean,
    isTestingOcrRecognition: Boolean,
    onTestScreenCapture: () -> Unit,
    onTestOcrRecognition: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("OCR 兜底")

        SoftPanelCard(
            cornerRadius = 18.dp,
            contentPadding = PaddingValues(14.dp)
        ) {
                SectionBody("OCR 兜底使用无障碍服务截图，不再请求投屏授权。")

                StatusRow(
                    label = "截图方式",
                    value = "无障碍服务"
                )
                StatusRow(
                    label = "OCR 引擎",
                    value = if (debugState.engineConfigured) "已配置" else "未配置"
                )
                StatusRow(
                    label = "截图数据流",
                    value = screenCaptureState.status.label
                )
                StatusRow(
                    label = "截图尺寸",
                    value = screenCaptureState.sizeLabel
                )
                StatusRow(
                    label = "截图状态",
                    value = screenCaptureState.message
                )
                StatusRow(
                    label = "帧质量",
                    value = screenCaptureState.frameStats.ifBlank { "暂无" }
                )
                StatusRow(
                    label = "调试截图",
                    value = screenCaptureState.debugImagePath.ifBlank { "暂无" }
                )
                StatusRow(
                    label = "最近分类",
                    value = debugState.lastCategory.label
                )
                StatusRow(
                    label = "最近状态",
                    value = debugState.lastStatus
                )
                StatusRow(
                    label = "触发原因",
                    value = debugState.lastReason.ifBlank { "暂无" }
                )
                StatusRow(
                    label = "目标应用",
                    value = debugState.targetApp.ifBlank { "暂无" }
                )
                StatusRow(
                    label = "识别消息数",
                    value = debugState.extractedMessages.size.toString()
                )
                StatusRow(
                    label = "过滤原因",
                    value = if (debugState.filterSummaries.isEmpty()) {
                        "暂无"
                    } else {
                        debugState.filterSummaries.take(3).joinToString("；") {
                            "${it.reason.label} ${it.count}"
                        }
                    }
                )
                StatusRow(
                    label = "更新时间",
                    value = formatTimestamp(debugState.updatedAtMillis)
                )

                SoftOutlinedAction(
                    text = if (isTestingScreenCapture) "取图中..." else "测试无障碍截图",
                    onClick = onTestScreenCapture,
                    enabled = !isTestingScreenCapture &&
                        !isTestingOcrRecognition,
                    modifier = Modifier.fillMaxWidth()
                )

                SoftOutlinedAction(
                    text = if (isTestingOcrRecognition) "识别中..." else "测试 OCR 识别",
                    onClick = onTestOcrRecognition,
                    enabled = !isTestingScreenCapture &&
                        !isTestingOcrRecognition,
                    modifier = Modifier.fillMaxWidth()
                )

                SoftOutlinedAction(
                    text = "复制 OCR 诊断",
                    onClick = {
                        clipboardManager.setText(
                            AnnotatedString(
                                buildOcrDebugSnapshot(
                                    debugState = debugState,
                                    screenCaptureState = screenCaptureState
                                )
                            )
                        )
                        Toast.makeText(context, "已复制 OCR 诊断", Toast.LENGTH_SHORT).show()
                    },
                    enabled = debugState.updatedAtMillis > 0L ||
                        screenCaptureState.updatedAtMillis > 0L,
                    modifier = Modifier.fillMaxWidth()
                )

                if (debugState.steps.isNotEmpty()) {
                    Text(
                        text = "OCR 步骤",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    debugState.steps.forEach { step ->
                        Text(
                            text = "- $step",
                            style = MaterialTheme.typography.bodySmall,
                            color = SoftSecondaryText
                        )
                    }
                }

                if (debugState.filterSummaryPreviews.isNotEmpty()) {
                    Text(
                        text = "过滤原因",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    debugState.filterSummaryPreviews.take(8).forEach { summary ->
                        Text(
                            text = "- $summary",
                            style = MaterialTheme.typography.bodySmall,
                            color = SoftSecondaryText
                        )
                    }
                }

                if (screenCaptureState.steps.isNotEmpty()) {
                    Text(
                        text = "截图步骤",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    screenCaptureState.steps.forEach { step ->
                        Text(
                            text = "- $step",
                            style = MaterialTheme.typography.bodySmall,
                            color = SoftSecondaryText
                        )
                    }
                }

                if (debugState.extractedMessagePreviews.isNotEmpty()) {
                    Text(
                        text = "OCR 消息",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    debugState.extractedMessagePreviews.takeLast(8).forEach { message ->
                        Text(
                            text = "- $message",
                            style = MaterialTheme.typography.bodySmall,
                            color = SoftPrimaryText
                        )
                    }
                }
        }
    }
}
