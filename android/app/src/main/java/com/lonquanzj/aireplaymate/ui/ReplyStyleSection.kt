package com.lonquanzj.aireplaymate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonquanzj.aireplaymate.context.ChatContext
import com.lonquanzj.aireplaymate.prompt.AppSettings
import com.lonquanzj.aireplaymate.prompt.DefaultPromptBuilder
import com.lonquanzj.aireplaymate.prompt.LlmRequest
import com.lonquanzj.aireplaymate.prompt.ReplyStyleCatalogState
import com.lonquanzj.aireplaymate.prompt.ReplyStyleMode
import com.lonquanzj.aireplaymate.prompt.ReplyStyleProfile

@Composable
internal fun ReplyStyleSection(
    appSettings: AppSettings,
    promptPreviewContext: ChatContext,
    profile: ReplyStyleProfile,
    catalog: ReplyStyleCatalogState,
    onProfileChange: (ReplyStyleProfile) -> Unit,
    onCatalogChange: (ReplyStyleCatalogState) -> Unit,
    onResetBuiltinCatalog: () -> Unit
) {
    var previewRequest by remember { mutableStateOf<LlmRequest?>(null) }
    var editingItem by remember { mutableStateOf<StyleEditTarget?>(null) }
    var isPersonaEditMode by remember { mutableStateOf(false) }
    var isPlaybookEditMode by remember { mutableStateOf(false) }
    var isPolishEditMode by remember { mutableStateOf(false) }

    previewRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { previewRequest = null },
            confirmButton = {
                TextButton(onClick = { previewRequest = null }) {
                    Text("关闭")
                }
            },
            title = { Text("Prompt 预览") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("System Prompt", fontWeight = FontWeight.SemiBold)
                    Text(request.systemPrompt, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    Text("User Prompt", fontWeight = FontWeight.SemiBold)
                    Text(request.userPrompt, style = MaterialTheme.typography.bodySmall)
                }
            }
        )
    }

    editingItem?.let { target ->
        StyleItemEditorDialog(
            target = target,
            onDismiss = { editingItem = null },
            onSave = { savedTarget ->
                onCatalogChange(catalog.saveStyleTarget(savedTarget))
                onProfileChange(profile.withStyleTarget(savedTarget))
                editingItem = null
            },
            onDelete = { deleteTarget ->
                onCatalogChange(catalog.deleteStyleTarget(deleteTarget))
                onProfileChange(profile.withResolvedCatalog(catalog.deleteStyleTarget(deleteTarget)))
                editingItem = null
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "LLM 回复风格",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = SoftPrimaryText,
                fontWeight = FontWeight.SemiBold
            )
        }

        SoftPanelCard(
            cornerRadius = 18.dp,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
        ) {
                Text(
                    text = "选择默认角色、话术和润色目标；每一类都可以编辑提示词，也可以新增自己的条目。",
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftSecondaryText
                )

                StatusRow(label = "默认回复", value = profile.asDefaultReply().displayLabel)

                HorizontalDivider()
                StyleCategoryHeader(
                    title = "角色",
                    isEditMode = isPersonaEditMode,
                    onEditClick = { isPersonaEditMode = !isPersonaEditMode },
                    onPreviewClick = {
                        previewRequest = DefaultPromptBuilder.build(
                            context = promptPreviewContext,
                            settings = appSettings,
                            styleProfile = profile.asDefaultReply().withResolvedCatalog(catalog)
                        )
                    }
                )
                ChoiceButtonGrid(
                    items = catalog.personas.map { it.id to it.label },
                    selectedId = profile.personaConfig.id,
                    isEditMode = isPersonaEditMode,
                    onAdd = {
                        editingItem = StyleEditTarget.Persona(newCustomPersonaConfig())
                    },
                    onSelect = { personaId ->
                        val persona = catalog.resolvePersona(personaId)
                        if (isPersonaEditMode) {
                            editingItem = StyleEditTarget.Persona(persona)
                        } else {
                            onProfileChange(
                                profile.copy(personaConfig = persona)
                                    .withResolvedCatalog(catalog)
                            )
                        }
                    }
                )

                HorizontalDivider()
                StyleCategoryHeader(
                    title = "话术宝典",
                    isEditMode = isPlaybookEditMode,
                    onEditClick = { isPlaybookEditMode = !isPlaybookEditMode },
                    onPreviewClick = {
                        previewRequest = DefaultPromptBuilder.build(
                            context = promptPreviewContext,
                            settings = appSettings,
                            styleProfile = profile.copy(mode = ReplyStyleMode.PLAYBOOK)
                                .withResolvedCatalog(catalog)
                        )
                    }
                )
                PlaybookChoiceGroups(
                    playbooks = catalog.playbooks,
                    selectedId = profile.playbookConfig.id,
                    isEditMode = isPlaybookEditMode,
                    onAdd = { categoryLabel ->
                        editingItem = StyleEditTarget.Playbook(newCustomPlaybookConfig(categoryLabel))
                    },
                    onSelect = { playbookId ->
                        val playbook = catalog.resolvePlaybook(playbookId)
                        if (isPlaybookEditMode) {
                            editingItem = StyleEditTarget.Playbook(playbook)
                        } else {
                            onProfileChange(
                                profile.copy(playbookConfig = playbook)
                                    .withResolvedCatalog(catalog)
                            )
                        }
                    }
                )

                HorizontalDivider()
                StyleCategoryHeader(
                    title = "润色表达",
                    isEditMode = isPolishEditMode,
                    onEditClick = { isPolishEditMode = !isPolishEditMode },
                    onPreviewClick = {
                        previewRequest = DefaultPromptBuilder.build(
                            context = promptPreviewContext,
                            settings = appSettings,
                            styleProfile = profile.copy(mode = ReplyStyleMode.POLISH)
                                .withResolvedCatalog(catalog)
                        )
                    }
                )
                ChoiceButtonGrid(
                    items = catalog.polishGoals.map { it.id to it.label },
                    selectedId = profile.polishGoalConfig.id,
                    isEditMode = isPolishEditMode,
                    onAdd = {
                        editingItem = StyleEditTarget.Polish(newCustomPolishGoalConfig())
                    },
                    onSelect = { goalId ->
                        val goal = catalog.resolvePolishGoal(goalId)
                        if (isPolishEditMode) {
                            editingItem = StyleEditTarget.Polish(goal)
                        } else {
                            onProfileChange(
                                profile.copy(polishGoalConfig = goal)
                                    .withResolvedCatalog(catalog)
                            )
                        }
                    }
                )

                Text(
                    text = styleExample(profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftSecondaryText
                )

                TextButton(onClick = onResetBuiltinCatalog) {
                    Text("恢复内置项默认")
                }
        }
    }
}

@Composable
private fun StyleCategoryHeader(
    title: String,
    isEditMode: Boolean,
    onEditClick: () -> Unit,
    onPreviewClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = SoftPrimaryText,
            fontWeight = FontWeight.SemiBold
        )
        StyleSmallActionButton(
            text = if (isEditMode) "完成" else "编辑",
            highlighted = isEditMode,
            onClick = onEditClick
        )
        StyleSmallActionButton(
            text = "Prompt 预览",
            highlighted = false,
            onClick = onPreviewClick
        )
    }
}

@Composable
private fun StyleSmallActionButton(
    text: String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.defaultMinSize(minHeight = 26.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 1.dp),
        border = BorderStroke(
            width = 0.8.dp,
            color = if (highlighted) SoftAction else SoftOutlineStrong
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (highlighted) SoftActionContainer else SoftPurplePanelTop.copy(alpha = 0.45f),
            contentColor = if (highlighted) SoftAction else SoftAccent
        )
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
    }
}
