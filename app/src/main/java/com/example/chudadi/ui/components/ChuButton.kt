@file:Suppress("LongParameterList", "MatchingDeclarationName")

package com.example.chudadi.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.chudadi.ui.theme.ChuButtonPalette
import com.example.chudadi.ui.theme.LocalChuUiPalette

enum class ChuButtonStyle { PRIMARY, SECONDARY, GHOST, DANGER }

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
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "buttonScale",
    )

    val shape = RoundedCornerShape(10.dp)
    val alpha = if (!enabled) 0.45f else if (isPressed) 0.82f else 1f
    val buttonPalette = LocalChuUiPalette.current.button
    val bgModifier = chuButtonBgModifier(style, shape, enabled, alpha, buttonPalette)
    val textColor = chuButtonTextColor(style, enabled, buttonPalette)

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
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

private fun chuButtonBgModifier(
    style: ChuButtonStyle,
    shape: RoundedCornerShape,
    enabled: Boolean,
    alpha: Float,
    palette: ChuButtonPalette,
): Modifier = when (style) {
    ChuButtonStyle.PRIMARY -> Modifier
        .shadow(if (enabled) palette.primaryShadow else 0.dp, shape)
        .clip(shape)
        .then(
            if (enabled) {
                Modifier.background(Brush.verticalGradient(colors = palette.primaryGradient))
            } else {
                Modifier.background(palette.secondaryBg.copy(alpha = alpha))
            },
        )
        .border(
            width = 1.dp,
            color = if (enabled) palette.primaryBorder else palette.ghostBorder.copy(alpha = alpha),
            shape = shape,
        )

    ChuButtonStyle.SECONDARY -> Modifier
        .shadow(if (enabled) palette.secondaryShadow else 0.dp, shape)
        .clip(shape)
        .background(palette.secondaryBg.copy(alpha = alpha))
        .border(1.dp, palette.secondaryBorder.copy(alpha = alpha), shape)

    ChuButtonStyle.GHOST -> Modifier
        .shadow(if (enabled) palette.ghostShadow else 0.dp, shape)
        .clip(shape)
        .background(palette.ghostBg.copy(alpha = alpha))
        .border(1.dp, palette.ghostBorder.copy(alpha = alpha), shape)

    ChuButtonStyle.DANGER -> Modifier
        .shadow(if (enabled) palette.dangerShadow else 0.dp, shape)
        .clip(shape)
        .background(palette.dangerBg.copy(alpha = alpha))
        .border(1.dp, palette.dangerBorder.copy(alpha = alpha), shape)
}

private fun chuButtonTextColor(
    style: ChuButtonStyle,
    enabled: Boolean,
    palette: ChuButtonPalette,
): Color = when {
    !enabled -> palette.textDisabled
    style == ChuButtonStyle.PRIMARY -> palette.textPrimary
    else -> palette.textSecondary
}
