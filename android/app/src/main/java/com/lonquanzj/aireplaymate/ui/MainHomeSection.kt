package com.lonquanzj.aireplaymate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lonquanzj.aireplaymate.accessibility.ChatMessage
import com.lonquanzj.aireplaymate.accessibility.ChatRole
import com.lonquanzj.aireplaymate.PermissionSnapshot
import com.lonquanzj.aireplaymate.overlay.OverlayServiceState
import com.lonquanzj.aireplaymate.settings.AppSettingsValidation

@Composable
internal fun AboutEntry(onClick: () -> Unit) {
    SoftPanelCard(
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleSmall,
                    color = SoftPrimaryText,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "项目说明与使用边界",
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftSecondaryText
                )
            }
            Text(
                text = "查看",
                style = MaterialTheme.typography.labelLarge,
                color = SoftAccent
            )
        }
    }
}

@Composable
internal fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("关于 AiChat")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("AiReplayMate 基于当前微信上下文生成候选回复。")
                Text("当前版本面向微信单聊辅助回复：生成候选、由你选择、只填入输入框，不自动发送。")
                Text("如果 Accessibility 上下文不足，会按设置尝试 OCR 兜底；诊断信息默认只保留摘要，避免记录完整聊天原文。")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}

@Composable
internal fun DailyHomeHeader(
    permissionSnapshot: PermissionSnapshot,
    overlayServiceState: OverlayServiceState,
    llmValidation: AppSettingsValidation,
    latestLogTitle: String?
) {
    val permissionReady = permissionSnapshot.accessibilityEnabled && permissionSnapshot.overlayEnabled
    val llmReady = llmValidation.canRequest

    SoftPanelCard(
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(16.dp),
        brush = softPurplePanelBrush()
    ) {
            Text(
                text = "日常入口",
                style = MaterialTheme.typography.titleMedium,
                color = SoftPrimaryText,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "先确认权限、气泡和模型配置，再去微信单聊里点气泡生成候选。",
                style = MaterialTheme.typography.bodySmall,
                color = SoftSecondaryText
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DailyStatusItem(
                    label = "权限",
                    value = if (permissionReady) "已就绪" else "待处理",
                    modifier = Modifier.weight(1f)
                )
                DailyStatusItem(
                    label = "气泡",
                    value = overlayServiceState.status.label,
                    modifier = Modifier.weight(1f)
                )
                DailyStatusItem(
                    label = "LLM",
                    value = if (llmReady) "可测试" else "需配置",
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "最近诊断：${latestLogTitle ?: "暂无"}",
                style = MaterialTheme.typography.labelLarge,
                color = SoftAccent
            )
    }
}

@Composable
private fun DailyStatusItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.62f))
            .border(1.dp, SoftOutline, RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = SoftSecondaryText
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = SoftPrimaryText,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun PermissionStatusSection(
    permissionSnapshot: PermissionSnapshot,
    overlayServiceState: OverlayServiceState,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onStartOverlayService: () -> Unit,
    onStopOverlayService: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("权限状态")

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PermissionCard(
                title = "无障碍服务",
                enabled = permissionSnapshot.accessibilityEnabled,
                description = if (permissionSnapshot.accessibilityEnabled) {
                    "已开启，可从真实微信聊天页触发生成。"
                } else {
                    "未开启时无法读取聊天上下文，也不能触发真实链路。"
                },
                buttonText = "去开启",
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth()
            )

            PermissionCard(
                title = "悬浮窗权限",
                enabled = permissionSnapshot.overlayEnabled,
                description = overlayPermissionDescription(
                    permissionSnapshot = permissionSnapshot,
                    overlayServiceState = overlayServiceState
                ),
                statusRows = listOf(
                    "服务状态" to overlayServiceState.status.label,
                    "气泡视图" to if (overlayServiceState.bubbleVisible) "已挂载" else "未挂载",
                    "更新时间" to formatTimestamp(overlayServiceState.updatedAtMillis)
                ),
                buttonText = if (permissionSnapshot.overlayEnabled) "启动/刷新气泡" else "去设置",
                onClick = if (permissionSnapshot.overlayEnabled) {
                    onStartOverlayService
                } else {
                    onOpenOverlaySettings
                },
                secondaryButtonText = if (permissionSnapshot.overlayEnabled) "停止气泡" else null,
                onSecondaryClick = onStopOverlayService,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun overlayPermissionDescription(
    permissionSnapshot: PermissionSnapshot,
    overlayServiceState: OverlayServiceState
): String {
    if (!permissionSnapshot.overlayEnabled) {
        return "需要授权后才能在微信上方显示触发入口。"
    }

    return when {
        overlayServiceState.bubbleVisible -> "已授权，AI 小气泡正在显示。若微信里看不到，可点“启动/刷新气泡”重新挂载。"
        overlayServiceState.updatedAtMillis > 0L -> "${overlayServiceState.message}。若没有小气泡，点“启动/刷新气泡”恢复。"
        else -> "已授权，但还未启动 AI 小气泡。点“启动/刷新气泡”后切回微信单聊页使用。"
    }
}

@Composable
private fun PermissionCard(
    title: String,
    enabled: Boolean,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
    statusRows: List<Pair<String, String>> = emptyList(),
    secondaryButtonText: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    SoftPanelCard(
        modifier = modifier,
        cornerRadius = 18.dp,
        contentPadding = PaddingValues(14.dp)
    ) {
            SoftStatusPill(text = if (enabled) "已开启" else "待处理", selected = enabled)

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = SoftPrimaryText,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = SoftSecondaryText
            )

            statusRows.forEach { (label, value) ->
                StatusRow(label = label, value = value)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SoftOutlinedAction(text = buttonText, onClick = onClick)

                if (secondaryButtonText != null && onSecondaryClick != null) {
                    SoftOutlinedAction(text = secondaryButtonText, onClick = onSecondaryClick)
                }
            }
    }
}

@Composable
internal fun ConversationPreviewSection(
    conversationTitle: String?,
    messages: List<ChatMessage>
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("聊天预览")

        SoftPanelCard(
            cornerRadius = 18.dp,
            contentPadding = PaddingValues(14.dp),
            brush = softPurplePanelBrush()
        ) {
                Text(
                    text = conversationTitle ?: "当前聊天上下文",
                    style = MaterialTheme.typography.titleSmall,
                    color = SoftPrimaryText,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (messages.isEmpty()) {
                        "进入微信单聊页后，这里会展示最近提取到的聊天上下文。"
                    } else {
                        "当前会把这段上下文送去生成 3 条候选回复。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftSecondaryText
                )

                if (messages.isEmpty()) {
                    Text(
                        text = "还没有提取到聊天消息。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SoftSecondaryText
                    )
                } else {
                    messages.forEach { message ->
                        MessageBubble(message = message)
                    }
                }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isMine = message.role == ChatRole.ME
    val bubbleColor = when (message.role) {
        ChatRole.ME -> SoftActionContainer
        ChatRole.FRIEND -> Color.White.copy(alpha = 0.72f)
        ChatRole.SYSTEM -> Color.White.copy(alpha = 0.56f)
        ChatRole.UNKNOWN -> Color.White.copy(alpha = 0.72f)
    }
    val textColor = SoftPrimaryText

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(bubbleColor)
                .border(1.dp, SoftOutline, RoundedCornerShape(16.dp))
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
