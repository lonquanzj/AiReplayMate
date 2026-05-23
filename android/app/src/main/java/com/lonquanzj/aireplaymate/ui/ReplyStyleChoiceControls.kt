package com.lonquanzj.aireplaymate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lonquanzj.aireplaymate.prompt.ReplyPlaybookConfig

@Composable
internal fun ChoiceButtonGrid(
    items: List<Pair<String, String>>,
    selectedId: String,
    isEditMode: Boolean,
    onAdd: (() -> Unit)? = null,
    onSelect: (String) -> Unit
) {
    val columnCount = 4
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        val gridItems = items.map { StyleGridItem.Choice(it.first, it.second) } +
            if (isEditMode && onAdd != null) listOf(StyleGridItem.Add) else emptyList()
        gridItems.chunked(columnCount).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowItems.forEach { item ->
                    when (item) {
                        StyleGridItem.Add -> StyleAddButton(
                            modifier = Modifier.weight(1f),
                            onClick = { onAdd?.invoke() }
                        )

                        is StyleGridItem.Choice -> StyleChoiceButton(
                            label = item.label,
                            selected = item.id == selectedId,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(item.id) }
                        )
                    }
                }
                repeat(columnCount - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun PlaybookChoiceGroups(
    playbooks: List<ReplyPlaybookConfig>,
    selectedId: String,
    isEditMode: Boolean,
    onAdd: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        playbooks.groupBy { it.categoryLabel }.forEach { (category, groupItems) ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelLarge,
                    color = SoftSecondaryText,
                    fontWeight = FontWeight.SemiBold
                )
                ChoiceButtonGrid(
                    items = groupItems.map { it.id to it.label },
                    selectedId = selectedId,
                    isEditMode = isEditMode,
                    onAdd = { onAdd(category) },
                    onSelect = onSelect
                )
            }
        }
    }
}

private sealed class StyleGridItem {
    data class Choice(val id: String, val label: String) : StyleGridItem()
    object Add : StyleGridItem()
}

@Composable
private fun StyleChoiceButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(13.dp)
    val buttonModifier = modifier.defaultMinSize(minHeight = 30.dp)
    val labelContent: @Composable () -> Unit = {
        Text(
            text = label,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (selected) {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            shape = shape,
            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SoftAction,
                contentColor = Color.White
            )
        ) {
            labelContent()
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            shape = shape,
            contentPadding = PaddingValues(horizontal = 5.dp, vertical = 4.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SoftAccent)
        ) {
            labelContent()
        }
    }
}

@Composable
private fun StyleAddButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 30.dp),
        shape = RoundedCornerShape(13.dp),
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SoftAction,
            contentColor = Color.White
        )
    ) {
        Text(
            text = "新增",
            fontSize = 13.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
