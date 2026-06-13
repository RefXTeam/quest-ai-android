package com.chroniclequest.presentation.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A soft neon glow around a rounded surface — the RPG accent layered on top of
 * Material 3 surfaces. Uses an outer shadow tinted with [color].
 */
fun Modifier.neonGlow(
    color: Color,
    cornerRadius: Dp = 16.dp,
    elevation: Dp = 12.dp,
): Modifier = this.shadow(
    elevation = elevation,
    shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius),
    ambientColor = color,
    spotColor = color,
)

/** Draws a thin glowing ring border (used on stat chips and the quest modal). */
fun Modifier.glowBorder(
    color: Color,
    cornerRadius: Dp = 16.dp,
    width: Dp = 1.5.dp,
): Modifier = this.drawBehind {
    val stroke = width.toPx()
    drawRoundRect(
        color = color.copy(alpha = 0.7f),
        topLeft = Offset(stroke / 2, stroke / 2),
        size = androidx.compose.ui.geometry.Size(
            size.width - stroke,
            size.height - stroke,
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx()),
        style = Stroke(width = stroke),
    )
}.padding(1.dp)
