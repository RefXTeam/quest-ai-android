package com.chroniclequest.presentation.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ChronicleDarkColors = darkColorScheme(
    primary = NeonCyan,
    onPrimary = MidnightVoid,
    secondary = ArcanePurple,
    onSecondary = TextPrimary,
    tertiary = QuestGold,
    onTertiary = MidnightVoid,
    background = MidnightVoid,
    onBackground = TextPrimary,
    surface = PanelSurface,
    onSurface = TextPrimary,
    surfaceVariant = PanelSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = DangerCrimson,
    onError = TextPrimary,
)

/**
 * Material 3 foundation themed with a dark RPG palette. We always run dark — the
 * app is designed as a moody quest console — but [darkTheme] is kept for previews.
 */
@Composable
fun ChronicleQuestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = ChronicleDarkColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ChronicleTypography,
        content = content,
    )
}
