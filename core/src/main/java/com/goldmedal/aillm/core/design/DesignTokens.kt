package com.goldmedal.aillm.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * AILLM design tokens.
 *
 * One restrained accent over deep, low-chroma neutrals. Dark is the primary
 * experience; light is a first-class fallback, not an afterthought. Values are
 * intentionally hand-tuned rather than taken straight from the Material
 * baseline so the app does not read as "default Material 3".
 */
object AillmPalette {
    // Accent — a calm, slightly desaturated indigo-blue.
    val Accent = Color(0xFF7DA2F7)
    val AccentDeep = Color(0xFF0B1B33)

    // Dark neutrals
    val Ink900 = Color(0xFF0B0D10)
    val Ink850 = Color(0xFF101318)
    val Ink800 = Color(0xFF15181E)
    val Ink750 = Color(0xFF1A1E25)
    val Ink700 = Color(0xFF212630)
    val Ink600 = Color(0xFF2C323D)
    val Ink400 = Color(0xFF8A93A3)
    val Ink200 = Color(0xFFC6CCD7)
    val Ink050 = Color(0xFFF2F4F8)

    // Light neutrals
    val Paper000 = Color(0xFFFBFBFD)
    val Paper050 = Color(0xFFF4F5F9)
    val Paper100 = Color(0xFFECEEF4)
    val Paper200 = Color(0xFFDFE2EA)
    val Paper900 = Color(0xFF16181D)
    val Paper600 = Color(0xFF5A6170)

    val Green = Color(0xFF7FD4A0)
    val Amber = Color(0xFFE9C46A)
    val Red = Color(0xFFFF8A8A)

    // Soft ambient light sources used behind the whole app. They are heavily
    // blurred, so they read as depth rather than as decoration.
    val GlowIndigo = Color(0xFF3D5FD0)
    val GlowViolet = Color(0xFF7A4FCE)
    val GlowTeal = Color(0xFF1B6E72)
    val GlowBlue = Color(0xFF2A6BC4)
}

/**
 * Glass tokens.
 *
 * The app is a frosted-glass surface over a blurred gradient: panels are
 * translucent, separated from the backdrop by a hairline highlight instead of a
 * drop shadow, which is what keeps them feeling like glass rather than paper.
 */
object AillmGlass {
    /** Radius of the ambient background blur. Large on purpose. */
    val ambientBlur = 110.dp

    /** Blur applied to floating panels such as the navigation pill. */
    val panelBlur = 40.dp

    /** Hairline highlight on the top edge of a glass panel. */
    val borderWidth = 1.dp

    const val BorderAlphaDark = 0.10f
    const val BorderAlphaLight = 0.75f
}

val AillmDarkColors = darkColorScheme(
    primary = AillmPalette.Accent,
    onPrimary = AillmPalette.AccentDeep,
    primaryContainer = Color(0xFF22345C),
    onPrimaryContainer = Color(0xFFD9E4FF),
    secondary = AillmPalette.Ink200,
    onSecondary = AillmPalette.Ink900,
    secondaryContainer = AillmPalette.Ink700,
    onSecondaryContainer = Color(0xFFE4E8F0),
    tertiary = AillmPalette.Green,
    onTertiary = AillmPalette.AccentDeep,
    tertiaryContainer = Color(0xFF1E3A2C),
    onTertiaryContainer = Color(0xFFCCEEDB),
    error = AillmPalette.Red,
    onError = Color(0xFF3A0A0A),
    errorContainer = Color(0xFF5A1D1D),
    onErrorContainer = Color(0xFFFFDAD8),
    // Transparent so the blurred ambient gradient shows through every screen.
    background = Color.Transparent,
    onBackground = Color(0xFFE7EAF0),
    surface = Color(0x1FFFFFFF),
    onSurface = Color(0xFFE7EAF0),
    surfaceVariant = Color(0x1FFFFFFF),
    onSurfaceVariant = AillmPalette.Ink400,
    surfaceContainerLowest = Color(0xE60B0D10),
    surfaceContainerLow = Color(0xB3101318),
    surfaceContainer = Color(0x1FFFFFFF),
    surfaceContainerHigh = Color(0x2BFFFFFF),
    surfaceContainerHighest = Color(0x3AFFFFFF),
    outline = AillmPalette.Ink600,
    outlineVariant = Color(0x1FFFFFFF),
    inverseSurface = Color(0xFFE7EAF0),
    inverseOnSurface = AillmPalette.Ink900,
    inversePrimary = Color(0xFF3C5FCD),
    scrim = Color(0xCC000000)
)

val AillmLightColors = lightColorScheme(
    primary = Color(0xFF3C5FCD),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE4FF),
    onPrimaryContainer = Color(0xFF0A1B44),
    secondary = AillmPalette.Paper600,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = AillmPalette.Paper100,
    onSecondaryContainer = Color(0xFF1B1E24),
    tertiary = Color(0xFF2E6B4C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3EEDD),
    onTertiaryContainer = Color(0xFF08251A),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color.Transparent,
    onBackground = AillmPalette.Paper900,
    surface = Color(0xB8FFFFFF),
    onSurface = AillmPalette.Paper900,
    surfaceVariant = Color(0x99FFFFFF),
    onSurfaceVariant = AillmPalette.Paper600,
    surfaceContainerLowest = Color(0xF7FFFFFF),
    surfaceContainerLow = Color(0xD9FFFFFF),
    surfaceContainer = Color(0xB8FFFFFF),
    surfaceContainerHigh = Color(0xCCFFFFFF),
    surfaceContainerHighest = Color(0xE6FFFFFF),
    outline = Color(0xFF8A909C),
    outlineVariant = Color(0x33FFFFFF),
    inverseSurface = Color(0xFF16181D),
    inverseOnSurface = Color(0xFFF4F5F9),
    inversePrimary = AillmPalette.Accent,
    scrim = Color(0x66000000)
)

/**
 * Tight, product-oriented type scale. Slightly negative tracking on large
 * titles keeps them from feeling like a generic Material headline.
 */
val AillmTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp
    )
)

/** Restrained corner radii — rounded, but never bubbly. */
val AillmShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(9.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

/** Consistent spacing scale so layouts breathe the same amount everywhere. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val screen = 16.dp
}

/** Motion durations/curves used across screens. Kept subtle and smooth. */
object AillmMotion {
    const val fast = 150
    const val medium = 260
    const val slow = 420
}
