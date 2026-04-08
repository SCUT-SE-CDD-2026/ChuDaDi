@file:Suppress("LongParameterList", "MatchingDeclarationName")

package com.example.chudadi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class ChuButtonStyle { PRIMARY, SECONDARY, GHOST, DANGER }

private val GoldDark = Color(0xFF8A6020)
private val GoldMid = Color(0xFFBA8C43)
private val GoldLight = Color(0xFFD4A85A)
private val GoldHighlight = Color(0x44FFE8A0)
private val SecondaryBg = Color(0xCC1D1A19)
private val SecondaryBorder = Color(0x66F7E8C2)
private val GhostBorder = Color(0x44C8A96A)
private val GhostBg = Color(0x18C8A96A)
private val DangerBg = Color(0xCC3A1010)
private val DangerBorder = Color(0x88E05050)
private val TextPrimary = Color(0xFFFDF7EA)
private val TextSecondary = Color(0xFFD6C9A8)
private val TextDisabled = Color(0xFF6B6050)

@Composable
fun ChuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ChuButtonStyle = ChuButtonStyle.PRIMARY,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val shape = RoundedCornerShape(10.dp)
    val alpha = if (!enabled) 0.45f else if (isPressed) 0.82f else 1f

    val bgModifier: Modifier = when (style) {
        ChuButtonStyle.PRIMARY -> Modifier
            .shadow(if (enabled) 6.dp else 0.dp, shape)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = if (enabled) listOf(GoldLight, GoldMid, GoldDark)
                    else listOf(Color(0xFF4A3A20), Color(0xFF3A2A10)),
                ),
            )
            .border(1.dp, if (enabled) GoldHighlight else Color(0x22C8A96A), shape)

        ChuButtonStyle.SECONDARY -> Modifier
            .clip(shape)
            .background(SecondaryBg.copy(alpha = alpha))
            .border(1.dp, SecondaryBorder.copy(alpha = alpha), shape)

        ChuButtonStyle.GHOST -> Modifier
            .clip(shape)
            .background(GhostBg.copy(alpha = alpha))
            .border(1.dp, GhostBorder.copy(alpha = alpha), shape)

        ChuButtonStyle.DANGER -> Modifier
            .clip(shape)
            .background(DangerBg.copy(alpha = alpha))
            .border(1.dp, DangerBorder.copy(alpha = alpha), shape)
    }

    val textColor = when {
        !enabled -> TextDisabled
        style == ChuButtonStyle.PRIMARY -> TextPrimary
        else -> TextSecondary
    }

    Box(
        modifier = modifier
            .height(44.dp)
            .then(bgModifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = textColor,
        )
    }
}
