package com.goldmedal.aillm.core.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The liquid-glass layer of the design system.
 *
 * The app was already one frosted surface over a blurred gradient; this adds
 * the two things that make glass read as *glass* rather than as a flat
 * translucent card:
 *
 *  - a specular sheen that travels across a panel, so the surface behaves like
 *    it is refracting a moving light source, and
 *  - a springy, slightly overshooting press response instead of Material's
 *    ripple, so touches feel like they displace a liquid.
 *
 * Everything here is dependency-free: `Modifier.border` with a gradient brush,
 * `animateFloatAsState` with a spring, and one `InfiniteTransition` for the
 * sheen. The sheen is opt-in per surface because an endless animation on every
 * row of a list is a waste of frames.
 */
object LiquidMotion {
    /** Press feedback: quick, soft, with a little overshoot on release. */
    fun <T> press(): androidx.compose.animation.core.FiniteAnimationSpec<T> =
        spring(
            dampingRatio = 0.52f,
            stiffness = Spring.StiffnessMedium,
            visibilityThreshold = null
        )

    /** How long one sheen sweep takes. Slow enough to feel like light. */
    const val sheenMillis = 2600
}

/** Vertical refraction gradient used as the base of a glass panel. */
@Composable
fun liquidBaseBrush(): Brush {
    val colors = if (LocalAillmIsDark.current) {
        listOf(Color(0x2BFFFFFF), Color(0x0DFFFFFF), Color(0x14FFFFFF))
    } else {
        listOf(Color(0xF5FFFFFF), Color(0xC9FFFFFF), Color(0xE0FFFFFF))
    }
    return Brush.linearGradient(colors)
}

/** The rim light: bright where the light hits, dim on the far edge. */
@Composable
fun liquidEdgeBrush(): Brush {
    val dark = LocalAillmIsDark.current
    val highlight = Color.White.copy(alpha = if (dark) 0.30f else 0.95f)
    val falloff = Color.White.copy(alpha = if (dark) 0.04f else 0.35f)
    return Brush.linearGradient(listOf(highlight, falloff, highlight))
}

@Composable
private fun liquidSheenColor(): Color =
    Color.White.copy(alpha = if (LocalAillmIsDark.current) 0.09f else 0.45f)

/**
 * Scales a surface down while it is pressed and lets it spring back. Used
 * instead of an indication so the motion is the feedback.
 */
@Composable
fun Modifier.liquidPress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.965f,
    enabled: Boolean = true
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = LiquidMotion.press(),
        label = "liquidPressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** A travelling highlight, clipped to [shape]. */
@Composable
private fun LiquidSheen(modifier: Modifier, shape: Shape, enabled: Boolean) {
    if (!enabled) return
    val transition = rememberInfiniteTransition(label = "liquidSheen")
    val progress by transition.animateFloat(
        initialValue = -0.9f,
        targetValue = 1.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = LiquidMotion.sheenMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liquidSheenProgress"
    )
    val sheen = liquidSheenColor()
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                val travel = size.width * 2.4f
                val startX = progress * travel - size.width * 0.7f
                val start = Offset(startX, 0f)
                val end = Offset(startX + size.width * 0.55f, size.height)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, sheen, Color.Transparent),
                        start = start,
                        end = end
                    )
                )
            }
    )
}

/**
 * The standard glass panel: refraction gradient, rim light, and an optional
 * travelling sheen. Pass [onClick] to make it press-sensitive.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    onClick: (() -> Unit)? = null,
    animatedSheen: Boolean = false,
    pressedScale: Float = 0.965f,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .liquidPress(interactionSource, pressedScale = pressedScale, enabled = onClick != null)
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
            .background(liquidBaseBrush(), shape)
            .border(BorderStroke(AillmGlass.borderWidth, liquidEdgeBrush()), shape)
    ) {
        LiquidSheen(Modifier.matchParentSize(), shape, enabled = animatedSheen)
        content()
    }
}

/**
 * The primary action of a screen: an accent-coloured glass bead that lifts and
 * springs back. Used for "start a new chat" so the action is unmistakable.
 */
@Composable
fun LiquidGlassFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val primary = MaterialTheme.colorScheme.primary
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = LiquidMotion.press(),
        label = "liquidFabScale"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(60.dp)
            .clip(CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .background(
                Brush.linearGradient(
                    listOf(
                        primary,
                        primary.copy(alpha = 0.86f),
                        primary.copy(alpha = 0.72f)
                    )
                ),
                CircleShape
            )
            .border(BorderStroke(AillmGlass.borderWidth, liquidEdgeBrush()), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        LiquidSheen(Modifier.matchParentSize(), CircleShape, enabled = true)
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(26.dp)
        )
    }
}

/**
 * Fades and lifts its content in on first composition — the "settle" of a
 * glass panel arriving, rather than a hard cut.
 */
@Composable
fun LiquidAppear(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(AillmMotion.slow)) +
            slideInVertically(
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
                initialOffsetY = { fullHeight -> fullHeight / 16 }
            ),
        modifier = modifier
    ) {
        content()
    }
}
