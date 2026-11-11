package io.github.yyan115.opentune.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal fun playerMorphRect(mini: Rect, full: Rect, fraction: Float): Rect {
    val f = fraction.coerceIn(0f, 1f)
    fun mix(a: Float, b: Float) = a + (b - a) * f
    return Rect(mix(mini.left, full.left), mix(mini.top, full.top), mix(mini.right, full.right), mix(mini.bottom, full.bottom))
}

/** Resize the enclosing panel, never the controls. Both layouts retain their endpoint sizes. */
@Composable
internal fun PlayerMorph(
    fraction: Float, miniBounds: Rect,
    mini: @Composable (Modifier) -> Unit, full: @Composable (Modifier) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val fullWidth = maxWidth
        val fullHeight = maxHeight
        val screen = Rect(0f, 0f, constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val target = if (miniBounds.width > 0) miniBounds else Rect(0f, screen.bottom - with(density) { 80.dp.toPx() }, screen.right, screen.bottom)
        val insetX = with(density) { 16.dp.toPx() }
        val insetY = with(density) { 5.dp.toPx() }
        val surface = playerMorphRect(Rect(target.left + insetX, target.top + insetY, target.right - insetX, target.bottom - insetY), screen, fraction)
        Surface(
            modifier = Modifier.offset { IntOffset(surface.left.roundToInt(), surface.top.roundToInt()) }
                .size(with(density) { surface.width.toDp() }, with(density) { surface.height.toDp() }),
            shape = RoundedCornerShape(7.dp * (1f - fraction)),
            color = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.colorScheme.background, fraction),
            // Interpolated colors have no automatic contentColor mapping in MaterialTheme.
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Box {
                // TopStart is essential: requiredSize alone centers oversized content and hides controls.
                Box(Modifier.wrapContentSize(unbounded = true, align = Alignment.TopStart)
                    .requiredSize(fullWidth, fullHeight)
                    .graphicsLayer { alpha = ((fraction - .1f) / .55f).coerceIn(0f, 1f) }
                    .then(if (fraction < .65f) Modifier.clearAndSetSemantics {} else Modifier)) {
                    full(Modifier)
                }
                if (fraction < .35f) {
                    mini(Modifier.wrapContentSize(unbounded = true, align = Alignment.TopStart)
                        .offset { IntOffset(-insetX.roundToInt(), -insetY.roundToInt()) }
                        .requiredSize(with(density) { target.width.toDp() }, with(density) { target.height.toDp() })
                        .graphicsLayer { alpha = (1f - fraction / .35f).coerceIn(0f, 1f) })
                }
            }
        }
    }
}
