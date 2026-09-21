package com.goldmedal.aillm.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * AILLM design system.
 * Dark-first, minimal, modern on-device AI aesthetic.
 * Defined via semantic tokens rooted in Material 3 but with a
 * distinct, product-specific color direction.
 */

// ---- Semantic tokens (dark-first) ----
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF072B57),
    primaryContainer = Color(0xFF1B47A0),
    onPrimaryContainer = Color(0xFFD7E3FF),
    secondary = Color(0xFFB9C5D9),
    onSecondary = Color(0xFF25303F),
    secondaryContainer = Color(0xFF3C4757),
    onSecondaryContainer = Color(0xFFD5E1F6),
    tertiary = Color(0xFFD0B9A6),
    onTertiary = Color(0xFF3A2A1B),
    tertiaryContainer = Color(0xFF543F2E),
    onTertiaryContainer = Color(0xFFF1DECB),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1216),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF0F1216),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF22262E),
    onSurfaceVariant = Color(0xFFB7BCC6),
    surfaceContainer = Color(0xFF161A20),
    surfaceContainerHigh = Color(0xFF1B2028),
    surfaceContainerHighest = Color(0xFF22262E),
    outline = Color(0xFF4C515B),
    outlineVariant = Color(0xFF313641),
    inverseSurface = Color(0xFFE1E2E8),
    inverseOnSurface = Color(0xFF2A2D34),
    inversePrimary = Color(0xFF0050B8),
    scrim = Color(0xFF000000)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0050B8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E3FF),
    onPrimaryContainer = Color(0xFF00163B),
    secondary = Color(0xFF566170),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE5F9),
    onSecondaryContainer = Color(0xFF131E2C),
    tertiary = Color(0xFF6E5A46),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF8DCC4),
    onTertiaryContainer = Color(0xFF271903),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF9FBFF),
    onBackground = Color(0xFF1A1C21),
    surface = Color(0xFFF9FBFF),
    onSurface = Color(0xFF1A1C21),
    surfaceVariant = Color(0xFFE6E9F4),
    surfaceContainer = Color(0xFFFDFDFF),
    surfaceContainerHigh = Color(0xFFF3F4FA),
    surfaceContainerHighest = Color(0xFFEEF0F6),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    inverseSurface = Color(0xFF2F3137),
    inverseOnSurface = Color(0xFFF0F0F6),
    inversePrimary = Color(0xFFAAC8FF),
    scrim = Color(0xFF000000)
)

// ---- Typography scale (system default for readability) ----
private val AppTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

// ---- Shape scale: subtle, restrained ----
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AILLMTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
