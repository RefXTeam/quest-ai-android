package com.chroniclequest.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.chroniclequest.presentation.theme.ExpEmerald
import com.chroniclequest.presentation.theme.NeonCyan
import com.chroniclequest.presentation.theme.QuestGold
import kotlin.math.cos
import kotlin.math.sin

private data class Particle(
    val angleRad: Float,
    val speed: Float,
    val color: Color,
    val radius: Float,
)

/**
 * A one-shot XP-confetti burst rendered on a Compose [Canvas]. [trigger] is a
 * monotonically changing key; each new value replays the explosion. Particles
 * radiate from the screen centre, accelerate outward, fall under gravity, and
 * fade — driven by a single [Animatable] 0→1 progression.
 */
@Composable
fun ConfettiCanvas(
    trigger: Int,
    modifier: Modifier = Modifier,
    particleCount: Int = 90,
) {
    if (trigger == 0) return

    val progress = remember { Animatable(0f) }
    val palette = listOf(QuestGold, NeonCyan, ExpEmerald, Color(0xFFFF6FB5))

    // Deterministic spread (no Math.random in this environment): fan particles
    // evenly around the circle with a pseudo-random radius/speed via index hashing.
    val particles = remember(trigger) {
        List(particleCount) { i ->
            val golden = 2.399963f // golden angle for an even spray
            val jitter = ((i * 2654435761u.toLong()) % 1000L) / 1000f
            Particle(
                angleRad = i * golden,
                speed = 0.6f + jitter * 0.8f,
                color = palette[i % palette.size],
                radius = 4f + (i % 4) * 2.5f,
            )
        }
    }

    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 1400))
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val origin = Offset(size.width / 2f, size.height * 0.42f)
        val maxReach = size.minDimension * 0.55f
        val gravity = size.height * 0.35f

        particles.forEach { p ->
            val distance = p.speed * maxReach * t
            val x = origin.x + cos(p.angleRad) * distance
            val y = origin.y + sin(p.angleRad) * distance + gravity * t * t
            val alpha = (1f - t).coerceIn(0f, 1f)
            drawCircle(
                color = p.color.copy(alpha = alpha),
                radius = p.radius * (1f - t * 0.4f),
                center = Offset(x, y),
            )
        }
    }
}
