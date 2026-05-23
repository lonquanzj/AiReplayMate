package com.lonquanzj.aireplaymate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val SoftPanelTop = Color(0xFEFFFCFF)
internal val SoftPanelBottom = Color(0xFEF8F4FF)
internal val SoftPurplePanelTop = Color(0xFFF3EEFF)
internal val SoftPurplePanelBottom = Color(0xFFE9E0FF)
internal val SoftOutline = Color(0x26A886FF)
internal val SoftOutlineStrong = Color(0x337A57E8)
internal val SoftPrimaryText = Color(0xFF3F2B78)
internal val SoftSecondaryText = Color(0xFF7A659C)
internal val SoftMutedText = Color(0xFF8B7AA3)
internal val SoftAccent = Color(0xFF5B3DC8)
internal val SoftAction = Color(0xFF7A57E8)
internal val SoftActionContainer = Color(0x247A57E8)

internal fun softPanelBrush() = Brush.linearGradient(listOf(SoftPanelTop, SoftPanelBottom))

internal fun softPurplePanelBrush() = Brush.linearGradient(listOf(SoftPurplePanelTop, SoftPurplePanelBottom))

@Composable
internal fun SoftPanelCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    brush: Brush = softPanelBrush(),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val baseModifier = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(brush)
        .border(1.dp, SoftOutline, shape)
    val cardModifier = if (onClick != null) {
        baseModifier.clickable(onClick = onClick)
    } else {
        baseModifier
    }

    Column(
        modifier = cardModifier.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
internal fun SoftStatusPill(
    text: String,
    selected: Boolean = true,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(999.dp)
    Text(
        text = text,
        modifier = modifier
            .clip(shape)
            .background(if (selected) SoftActionContainer else Color.Transparent)
            .border(1.dp, if (selected) SoftOutlineStrong else SoftOutline, shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = if (selected) SoftAccent else SoftSecondaryText,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun SoftPrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(13.dp),
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SoftAction,
            contentColor = Color.White,
            disabledContainerColor = SoftActionContainer,
            disabledContentColor = SoftMutedText
        ),
        modifier = modifier.defaultMinSize(minHeight = 32.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun SoftOutlinedAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(13.dp),
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = SoftAccent,
            disabledContentColor = SoftMutedText
        ),
        modifier = modifier.defaultMinSize(minHeight = 32.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = SoftPrimaryText,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
internal fun SectionBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = SoftSecondaryText
    )
}

@Composable
internal fun StatusRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.width(96.dp),
            style = MaterialTheme.typography.labelLarge,
            color = SoftSecondaryText
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = SoftPrimaryText
        )
    }
}
