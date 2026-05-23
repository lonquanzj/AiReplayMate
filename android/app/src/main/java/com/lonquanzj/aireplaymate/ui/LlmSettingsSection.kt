package com.lonquanzj.aireplaymate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.ContextSendPolicy
import com.lonquanzj.aireplaymate.settings.AppSettingsValidation
import kotlin.math.roundToInt

@Composable
internal fun LlmSettingsSection(
    settings: AppSettings,
    validation: AppSettingsValidation,
    isTesting: Boolean,
    onSettingsChange: (AppSettings) -> Unit,
    onTestConnection: () -> Unit,
    onImportSettings: () -> Unit,
    onExportSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("LLM 设置")

        SoftPanelCard(
            cornerRadius = 18.dp,
            contentPadding = PaddingValues(14.dp)
        ) {
                Text(
                    text = llmSettingsHint(settings, validation),
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftSecondaryText
                )

                OutlinedTextField(
                    value = settings.apiKey,
                    onValueChange = { onSettingsChange(settings.copy(apiKey = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                OutlinedTextField(
                    value = settings.baseUrl,
                    onValueChange = { onSettingsChange(settings.copy(baseUrl = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    label = { Text("Base URL") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                OutlinedTextField(
                    value = settings.model,
                    onValueChange = { onSettingsChange(settings.copy(model = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true,
                    label = { Text("Model") }
                )

                LlmParameterSlider(
                    label = "Temperature",
                    valueText = "%.1f".format(settings.temperature),
                    value = settings.temperature.coerceIn(0f, 2f),
                    valueRange = 0f..2f,
                    steps = 19,
                    onValueChange = { value ->
                        onSettingsChange(settings.copy(temperature = (value * 10).roundToInt() / 10f))
                    }
                )

                LlmParameterSlider(
                    label = "Max tokens",
                    valueText = settings.maxTokens.toString(),
                    value = settings.maxTokens.coerceIn(120, 2000).toFloat(),
                    valueRange = 120f..2000f,
                    onValueChange = { value ->
                        val maxTokens = (value / 10).roundToInt() * 10
                        onSettingsChange(settings.copy(maxTokens = maxTokens.coerceIn(120, 2000)))
                    }
                )

                ContextSendPolicySelector(
                    selected = settings.contextSendPolicy,
                    onSelected = { onSettingsChange(settings.copy(contextSendPolicy = it)) }
                )

                validation.errors.forEach { error ->
                    Text(
                        text = "错误：$error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                validation.warnings.forEach { warning ->
                    Text(
                        text = "提醒：$warning",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftAccent
                    )
                }

                SoftPrimaryAction(
                    text = if (isTesting) "测试中..." else "测试连接",
                    onClick = onTestConnection,
                    enabled = validation.canRequest && !isTesting,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SoftOutlinedAction(
                        text = "导入配置",
                        onClick = onImportSettings,
                        modifier = Modifier.weight(1f)
                    )
                    SoftOutlinedAction(
                        text = "导出配置",
                        onClick = onExportSettings,
                        modifier = Modifier.weight(1f)
                    )
                }
        }
    }
}

@Composable
private fun LlmParameterSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    steps: Int = 0
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = SoftPrimaryText,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = SoftAccent,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ContextSendPolicySelector(
    selected: ContextSendPolicy,
    onSelected: (ContextSendPolicy) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "发送给 LLM 的上下文",
            style = MaterialTheme.typography.bodyMedium,
            color = SoftPrimaryText,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ContextSendPolicyButton(
                text = "全部上下文",
                selected = selected == ContextSendPolicy.FULL_CONTEXT,
                onClick = { onSelected(ContextSendPolicy.FULL_CONTEXT) },
                modifier = Modifier.weight(1f)
            )
            ContextSendPolicyButton(
                text = "仅最近对方消息",
                selected = selected == ContextSendPolicy.LATEST_FRIEND_MESSAGE,
                onClick = { onSelected(ContextSendPolicy.LATEST_FRIEND_MESSAGE) },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = when (selected) {
                ContextSendPolicy.FULL_CONTEXT -> "生成时会发送最多 20 条最近聊天记录。"
                ContextSendPolicy.LATEST_FRIEND_MESSAGE -> "生成时只发送最近一条对方消息。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = SoftSecondaryText
        )
    }
}

@Composable
private fun ContextSendPolicyButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        SoftPrimaryAction(text = text, onClick = onClick, modifier = modifier)
    } else {
        SoftOutlinedAction(text = text, onClick = onClick, modifier = modifier)
    }
}

private fun llmSettingsHint(
    settings: AppSettings,
    validation: AppSettingsValidation
): String {
    return when {
        validation.errors.isNotEmpty() -> "请先修正配置错误；未配置可用 LLM 时，微信悬浮面板会使用本地兜底候选。"
        settings.apiKey.isBlank() -> "未配置 API Key 时，微信悬浮面板会使用本地兜底候选。"
        else -> "配置看起来可用；可先点测试连接，再到微信单聊页使用悬浮面板。"
    }
}
