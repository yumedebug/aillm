package com.goldmedal.aillm.core.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The translucent surface layer of the design system.
 *
 * This replaces the earlier liquid-glass treatment (travelling sheen +
 * springy, overshooting press) with a calmer, more modern reading of the same
 * idea: panels are still translucent and sit on the blurred ambient gradient,
 * but they are separated from it the way current UI systems do it — a soft
 * elevation shadow, a hairline edge, and a gentle vertical gradient for depth
 * rather than specular refraction.
 *
 * Everything here is dependency-free: a gradient brush, `Modifier.shadow`,
 * `Modifier.border`, and the default Material ripple for touch feedback.
 */

/**
 * Subtle press feedback for a surface that is not using the default ripple.
 * A short, critically damped scale — it settles rather than springs, so the
 * motion reads as deliberate instead of liquid.
 */
@Composable
fun Modifier.surfacePress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.975f,
    enabled: Boolean = true
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = tween(AillmMotion.fast),
        label = "surfacePressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * The base fill of a translucent panel: a soft top-to-bottom gradient between
 * the theme's translucent surface and its container tone. Two closely spaced
 * stops are enough to read as glass over the blurred backdrop without the
 * busy refraction of the previous design.
 */
@Composable
fun panelBaseBrush(): Brush {
    val scheme = MaterialTheme.colorScheme
    val top = scheme.surface
    val bottom = scheme.surfaceContainerHigh
    return Brush.verticalGradient(listOf(top, bottom))
}

/**
 * The standard translucent panel. Pass [onClick] to make it interactive, in
 * which case the Material ripple (and a gentle press scale) is the feedback.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    onClick: (() -> Unit)? = null,
    elevation: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .surfacePress(interactionSource, enabled = onClick != null)
            .shadow(elevation = elevation, shape = shape, clip = false)
            .clip(shape)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                }
            )
            .background(panelBaseBrush(), shape)
            .border(
                width = AillmSurface.borderWidth,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
    ) {
        content()
    }
}

/**
 * The primary action of a screen: a solid accent bead that lifts off the
 * backdrop with a shadow. Used for "start a new chat" so the action is
 * unmistakable, and it responds with the normal Material ripple.
 */
@Composable
fun GlassFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .shadow(elevation = 10.dp, shape = CircleShape, clip = false)
            .size(60.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(
                Brush.linearGradient(
                    listOf(
                        primary,
                        primary.copy(alpha = 0.88f)
                    )
                ),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(26.dp)
        )
    }
}

/**
 * Fades and lifts its content in on first composition — a screen arriving
 * rather than a hard cut. Kept from the earlier motion language because it is
 * unobtrusive and reads as polish, not as glass.
 */
@Composable
fun AillmAppear(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(AillmMotion.slow)) +
            slideInVertically(
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f),
                initialOffsetY = { fullHeight -> fullHeight / 20 }
            ),
        modifier = modifier
    ) {
        content()
    }
}

/** A plain translucent fill, for places that want the surface without a panel. */
@Composable
fun translucentSurfaceColor(alpha: Float = 1f): Color =
    MaterialTheme.colorScheme.surface.copy(alpha = alpha)
