package com.goldmedal.aillm.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/** User-selectable appearance mode, persisted in AppSettings. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/**
 * Whether the dark scheme is in effect. The ambient background needs this but
 * cannot infer it (the transparent background colour carries no information),
 * so the theme publishes it explicitly.
 */
val LocalAillmIsDark = staticCompositionLocalOf { true }

/**
 * Applies the AILLM design system. Dark-first, but the system/light modes are
 * fully supported. No window/renderer hacks — edge-to-edge is handled by the
 * Activity, so the theme stays a pure Compose concern.
 */
@Composable
fun AillmTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    CompositionLocalProvider(LocalAillmIsDark provides dark) {
        MaterialTheme(
            colorScheme = if (dark) AillmDarkColors else AillmLightColors,
            typography = AillmTypography,
            shapes = AillmShapes,
            content = content
        )
    }
}
