package com.lonquanzj.aireplaymate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.lonquanzj.aireplaymate.accessibility.ChatMessage
import com.lonquanzj.aireplaymate.context.ChatContext
import com.lonquanzj.aireplaymate.context.ConversationType
import com.lonquanzj.aireplaymate.prompt.PolishGoalConfig
import com.lonquanzj.aireplaymate.prompt.ReplyPersonaConfig
import com.lonquanzj.aireplaymate.prompt.ReplyPlaybookConfig
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalog
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalogState
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile
import com.lonquanzj.aireplaymate.settings.ReplyStyleCatalogStore

internal fun realPromptPreviewContext(messages: List<ChatMessage>): ChatContext {
    return ChatContext(
        messages = messages,
        targetApp = "wechat",
        conversationType = ConversationType.SINGLE_CHAT,
        collectedAt = System.currentTimeMillis()
    )
}

internal sealed class StyleEditTarget {
    data class Persona(val item: ReplyPersonaConfig) : StyleEditTarget()
    data class Playbook(val item: ReplyPlaybookConfig) : StyleEditTarget()
    data class Polish(val item: PolishGoalConfig) : StyleEditTarget()
}

@Composable
internal fun StyleItemEditorDialog(
    target: StyleEditTarget,
    onDismiss: () -> Unit,
    onSave: (StyleEditTarget) -> Unit,
    onDelete: (StyleEditTarget) -> Unit
) {
    var label by remember(target) { mutableStateOf(target.label) }
    var category by remember(target) { mutableStateOf(target.categoryLabel.orEmpty()) }
    var identityPrompt by remember(target) { mutableStateOf(target.identityPrompt) }
    var promptGuide by remember(target) { mutableStateOf(target.promptGuide) }
    val canDelete = !target.isBuiltin

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.dialogTitle) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("名称") },
                    shape = RoundedCornerShape(16.dp)
                )
                if (target is StyleEditTarget.Playbook) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("分类") },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
                OutlinedTextField(
                    value = identityPrompt,
                    onValueChange = { identityPrompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("身份/定位") },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                OutlinedTextField(
                    value = promptGuide,
                    onValueChange = { promptGuide = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    label = { Text("提示词") },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                if (!canDelete) {
                    Text(
                        text = "内置项可编辑，但不能删除。",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftSecondaryText
                    )
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canDelete) {
                    TextButton(onClick = { onDelete(target) }) {
                        Text("删除")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        target.withValues(
                            label = label.ifBlank { target.label },
                            categoryLabel = category.ifBlank { "自定义话术" },
                            identityPrompt = identityPrompt.ifBlank { target.identityPrompt },
                            promptGuide = promptGuide.ifBlank { target.promptGuide }
                        )
                    )
                }
            ) {
                Text("保存")
            }
        }
    )
}

internal val StyleEditTarget.label: String
    get() = when (this) {
        is StyleEditTarget.Persona -> item.label
        is StyleEditTarget.Playbook -> item.label
        is StyleEditTarget.Polish -> item.label
    }

internal val StyleEditTarget.categoryLabel: String?
    get() = when (this) {
        is StyleEditTarget.Playbook -> item.categoryLabel
        else -> null
    }

internal val StyleEditTarget.identityPrompt: String
    get() = when (this) {
        is StyleEditTarget.Persona -> item.identityPrompt
        is StyleEditTarget.Playbook -> item.identityPrompt
        is StyleEditTarget.Polish -> item.identityPrompt
    }

internal val StyleEditTarget.promptGuide: String
    get() = when (this) {
        is StyleEditTarget.Persona -> item.promptGuide
        is StyleEditTarget.Playbook -> item.promptGuide
        is StyleEditTarget.Polish -> item.promptGuide
    }

internal val StyleEditTarget.isBuiltin: Boolean
    get() = when (this) {
        is StyleEditTarget.Persona -> item.isBuiltin
        is StyleEditTarget.Playbook -> item.isBuiltin
        is StyleEditTarget.Polish -> item.isBuiltin
    }

internal val StyleEditTarget.dialogTitle: String
    get() = when (this) {
        is StyleEditTarget.Persona -> "编辑角色"
        is StyleEditTarget.Playbook -> "编辑话术宝典"
        is StyleEditTarget.Polish -> "编辑润色表达"
    }

internal fun StyleEditTarget.withValues(
    label: String,
    categoryLabel: String,
    identityPrompt: String,
    promptGuide: String
): StyleEditTarget {
    return when (this) {
        is StyleEditTarget.Persona -> copy(
            item = item.copy(
                label = label,
                identityPrompt = identityPrompt,
                promptGuide = promptGuide
            )
        )

        is StyleEditTarget.Playbook -> copy(
            item = item.copy(
                categoryLabel = categoryLabel,
                label = label,
                identityPrompt = identityPrompt,
                promptGuide = promptGuide
            )
        )

        is StyleEditTarget.Polish -> copy(
            item = item.copy(
                label = label,
                identityPrompt = identityPrompt,
                promptGuide = promptGuide
            )
        )
    }
}

internal fun ReplyStyleCatalogState.saveStyleTarget(target: StyleEditTarget): ReplyStyleCatalogState {
    return when (target) {
        is StyleEditTarget.Persona -> copy(personas = personas.upsert(target.item, ReplyPersonaConfig::id))
        is StyleEditTarget.Playbook -> copy(playbooks = playbooks.upsert(target.item, ReplyPlaybookConfig::id))
        is StyleEditTarget.Polish -> copy(polishGoals = polishGoals.upsert(target.item, PolishGoalConfig::id))
    }
}

internal fun ReplyStyleCatalogState.deleteStyleTarget(target: StyleEditTarget): ReplyStyleCatalogState {
    if (target.isBuiltin) return this
    return when (target) {
        is StyleEditTarget.Persona -> copy(personas = personas.filterNot { it.id == target.item.id })
        is StyleEditTarget.Playbook -> copy(playbooks = playbooks.filterNot { it.id == target.item.id })
        is StyleEditTarget.Polish -> copy(polishGoals = polishGoals.filterNot { it.id == target.item.id })
    }
}

internal fun ReplyStyleProfile.withStyleTarget(target: StyleEditTarget): ReplyStyleProfile {
    return when (target) {
        is StyleEditTarget.Persona -> copy(
            persona = ReplyStyleCatalog.personaFromConfig(target.item),
            personaConfig = target.item
        )

        is StyleEditTarget.Playbook -> copy(
            playbookScene = ReplyStyleCatalog.sceneFromConfig(target.item),
            playbookConfig = target.item
        )

        is StyleEditTarget.Polish -> copy(
            polishGoal = ReplyStyleCatalog.polishGoalFromConfig(target.item),
            polishGoalConfig = target.item
        )
    }
}

internal fun ReplyStyleProfile.withResolvedCatalog(catalog: ReplyStyleCatalogState): ReplyStyleProfile {
    val resolvedPersona = catalog.resolvePersona(personaConfig.id)
    val resolvedPlaybook = catalog.resolvePlaybook(playbookConfig.id)
    val resolvedPolish = catalog.resolvePolishGoal(polishGoalConfig.id)
    return copy(
        persona = ReplyStyleCatalog.personaFromConfig(resolvedPersona),
        playbookScene = ReplyStyleCatalog.sceneFromConfig(resolvedPlaybook),
        polishGoal = ReplyStyleCatalog.polishGoalFromConfig(resolvedPolish),
        personaConfig = resolvedPersona,
        playbookConfig = resolvedPlaybook,
        polishGoalConfig = resolvedPolish
    )
}

internal fun <T> List<T>.upsert(
    item: T,
    idOf: (T) -> String
): List<T> {
    val exists = any { idOf(it) == idOf(item) }
    return if (exists) {
        map { current -> if (idOf(current) == idOf(item)) item else current }
    } else {
        this + item
    }
}

internal fun newCustomPersonaConfig(): ReplyPersonaConfig {
    return ReplyPersonaConfig(
        id = ReplyStyleCatalogStore.newCustomId("persona"),
        label = "自定义角色",
        identityPrompt = "你正在模仿一个自定义微信回复身份。",
        promptGuide = "按用户填写的风格自然回复。",
        isBuiltin = false
    )
}

internal fun newCustomPlaybookConfig(categoryLabel: String): ReplyPlaybookConfig {
    val category = categoryLabel.ifBlank { "自定义话术" }
    return ReplyPlaybookConfig(
        id = ReplyStyleCatalogStore.newCustomId("playbook"),
        categoryLabel = category,
        label = "自定义场景",
        identityPrompt = "你正在生成一个自定义聊天场景的话术。",
        promptGuide = "按用户填写的场景目标生成可直接发送的话术。",
        isBuiltin = false
    )
}

internal fun newCustomPolishGoalConfig(): PolishGoalConfig {
    return PolishGoalConfig(
        id = ReplyStyleCatalogStore.newCustomId("polish"),
        label = "自定义润色",
        identityPrompt = "你正在按自定义目标润色用户草稿。",
        promptGuide = "按用户填写的润色目标改写表达。",
        isBuiltin = false
    )
}
