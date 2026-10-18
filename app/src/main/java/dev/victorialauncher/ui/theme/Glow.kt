// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * One accent, and the light it throws.
 *
 * Sailfish keeps a black screen and lets a few things shine on it, and what makes it read that
 * way is that nothing competes: the marks on the settings screen, the switches, the handles of
 * a slider, the tick in a checkbox - all the same colour, all glowing the same amount. Scattered
 * accents in different places look like a theme nobody chose; one accent used everywhere looks
 * deliberate, and is the thing that makes a launcher feel like it was designed rather than
 * assembled from stock parts.
 *
 * Cold cyan rather than this app's own teal, because it reads as light coming off the screen
 * rather than as another material colour.
 */
val GlowAccent = Color(0xFF6FE3F5)

/** A band barely off black, so a control has something to be lit against. */
val GlowTrack = Color(0x1FFFFFFF)

/**
 * The halo behind a lit control.
 *
 * Drawn as a radial falloff rather than a blur. A launcher cannot blur the wallpaper - that is
 * the window's background, not a view it owns - and a RenderEffect re-applied per frame is the
 * wrong thing for a list that scrolls continuously. At 20-30dp the two are not distinguishable,
 * and this one costs the same over black as over anything else.
 *
 * Deliberately larger than the slot it sits in, and drawn under the control rather than around
 * it. Held to the control's own size it reads as a tinted tile, and a tinted tile is exactly
 * what these replaced.
 */
@Composable
fun GlowHalo(
    size: Dp,
    scale: Float = 2.1f,
    alpha: Float = 0.26f,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
) {
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        GlowAccent.copy(alpha = alpha),
                        GlowAccent.copy(alpha = alpha * 0.3f),
                        Color.Transparent,
                    ),
                ),
                shape,
            ),
    )
}

/** Wraps a control so the halo sits behind it rather than around it. */
@Composable
private fun Lit(size: Dp, scale: Float, alpha: Float, content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        GlowHalo(size = size, scale = scale, alpha = alpha)
        content()
    }
}

/** What an unticked control is drawn in: grey, and not one of the accent's. */
private val OffInk = Color(0xFF6E7276)

/** A tick is a square with its corners turned, rather than hard corners. */
private val RoundedTickShape = RoundedCornerShape(5.dp)

private val SWITCH_SIZE = 26.dp

/**
 * A round target: grey and dead when it is off, the accent and lit when it is on.
 *
 * Not a track with a travelling dot. The dot was the problem - a pill with a dot is still a pill,
 * and on a settings screen full of them the row reads as a row of lozenges rather than as a set
 * of decisions. What is wanted here is a target: one round thing that is either lit or not.
 *
 * So there is no track and no thumb, only the circle. Off it is grey and flat, with a faint
 * lighter rim so it still reads as something you can press rather than as a smudge; on it is the
 * accent with a halo. The change is a colour and a light, not a movement, which also means there
 * is nothing to animate and nothing to lag behind a finger.
 */
@Composable
fun GlowingSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        GlowHalo(size = 58.dp, scale = 1.6f, alpha = if (checked) 0.42f else 0f)
        Box(
            Modifier
                .size(SWITCH_SIZE)
                .clip(CircleShape)
                .background(if (checked) GlowAccent else Color(0xFF2A2E33))
                .border(
                    width = 1.5.dp,
                    color = if (checked) Color.Transparent else OffInk.copy(alpha = 0.75f),
                    shape = CircleShape,
                )
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        )
    }
}

/**
 * A tick that shines when it is ticked, and is a quiet grey outline when it is not.
 *
 * A checkbox is already a square, so rounding it is a smaller change than for a switch - but it
 * was square with hard corners, which reads as a placeholder rather than a control, and turning
 * them a little is what makes it look handled. The tick is drawn rather than borrowed for the same
 * reason as the switch, and so that the unchecked box is grey rather than faintly cyan.
 */
@Composable
fun GlowingCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        GlowHalo(size = 52.dp, scale = 1.5f, alpha = if (checked) 0.26f else 0f)
        Box(
            Modifier
                .size(CHECKBOX_SIZE)
                .clip(RoundedTickShape)
                .background(if (checked) GlowAccent else Color.Transparent)
                .border(
                    width = if (checked) 0.dp else 1.5.dp,
                    color = if (checked) Color.Transparent else OffInk,
                    shape = RoundedTickShape,
                )
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Canvas(Modifier.size(CHECKBOX_SIZE * 0.62f)) {
                    val w = this.size.width
                    val h = this.size.height
                    val stroke = Stroke(width = w * 0.18f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    // A tick, drawn as two strokes meeting low and left of centre.
                    val path = Path().apply {
                        moveTo(w * 0.14f, h * 0.52f)
                        lineTo(w * 0.40f, h * 0.78f)
                        lineTo(w * 0.86f, h * 0.22f)
                    }
                    drawPath(path, Color.Black, style = stroke)
                }
            }
        }
    }
}

private val CHECKBOX_SIZE = 24.dp

/** A radio marker that shines when it is the chosen one, and a ring when it is not. */
@Composable
fun GlowingRadioButton(selected: Boolean, onClick: () -> Unit) {
    Lit(size = 48.dp, scale = 1.7f, alpha = if (selected) 0.28f else 0f) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = GlowAccent,
                unselectedColor = GlowTrack,
            ),
        )
    }
}

/**
 * A slider with a lit track and a handle that glows.
 *
 * Written out rather than taken from Material, because it has to look like this and Material will
 * not let it. Its own track and thumb slots are enough for the shape, but the handle stopped
 * following the value altogether: the ball sat in the middle of the track whatever the setting was
 * set to - dead centre at 0% - and no drag moved it, because the gesture was being read by a Slider
 * whose own width was not the width of the track beside it. Two components answering "how wide is
 * this" differently is enough to stop a slider working, and there is no way to see that from the
 * source when the whole thing is somebody else's layout.
 *
 * So the geometry is all here, and it is the same arithmetic on both sides: the handle travels
 * from the middle of its own ball at the left end to the middle of its own ball at the right end,
 * which is the only definition that lets 0% actually rest at the left.
 *
 * The ball is white rather than the accent, on purpose. On a black screen the accent is the colour
 * everything else is judged against, and an accented handle disappears into the track it is on.
 */
@Composable
fun GlowingSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span > 0f) ((value - valueRange.start) / span).coerceIn(0f, 1f) else 0f

    /** How wide this slider is, in pixels, once it has been laid out. */
    var widthPx by remember { mutableIntStateOf(0) }
    val thumbPx = with(density) { SLIDER_THUMB_SIZE.toPx() }
    // The handle's centre travels, so the distance it covers is the width less one ball.
    val travel = (widthPx - thumbPx).coerceAtLeast(0f)

    /** A finger at this x, as a value in the range. */
    fun valueAt(x: Float): Float {
        val f = (x / widthPx).coerceIn(0f, 1f)
        return valueRange.start + f * span
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Tall enough to be a comfortable target even though the line in it is 6dp.
            .height(SLIDER_ROW_HEIGHT)
            .onSizeChanged { widthPx = it.width }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                if (enabled) {
                    setProgress { target ->
                        onValueChange(target.coerceIn(valueRange))
                        true
                    }
                }
            }
            .pointerInput(enabled, widthPx, valueRange) {
                if (!enabled || widthPx <= 0) return@pointerInput
                awaitEachGesture {
                    // Claim the press at once. Without this a drag is only noticed once it has
                    // moved a whole touch slop, and the first part of the gesture goes to whatever
                    // is behind - which on a settings screen is the row underneath.
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    onValueChange(valueAt(down.position.x))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        // Absolute position, never a delta: a consumed change reports no movement,
                        // and a slider that reads deltas stops dead the moment it claims the drag.
                        onValueChange(valueAt(change.position.x))
                    }
                    onValueChangeFinished?.invoke()
                }
            },
        // CentreStart, so the track starts at the left edge and the handle is placed by offset
        // rather than by an alignment that would fight it.
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(SLIDER_TRACK_HEIGHT)
                .clip(CircleShape)
                .background(if (enabled) GlowTrack else GlowTrack.copy(alpha = 0.4f), CircleShape),
        )

        // The lit part, and the light it throws, clipped to exactly the filled width - so the glow
        // stops where the colour stops instead of washing the whole slider.
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            GlowHalo(size = SLIDER_TRACK_HEIGHT * 7f, scale = 1f, alpha = if (enabled) 0.55f else 0f)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (enabled) GlowAccent else OffInk, CircleShape),
            )
        }

        // The ball, at one end or the other of the travel and nowhere in between.
        Box(
            Modifier
                .offset { IntOffset((travel * fraction).roundToInt(), 0) }
                .size(SLIDER_THUMB_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            GlowHalo(size = SLIDER_THUMB_SIZE, scale = 3.2f, alpha = if (enabled) 0.6f else 0f)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (enabled) Color.White else OffInk, CircleShape),
            )
        }
    }
}

private val SLIDER_THUMB_SIZE = 18.dp
private val SLIDER_TRACK_HEIGHT = 6.dp
private val SLIDER_ROW_HEIGHT = 40.dp

/** The same, for a range: both ends lit, and the band between them in the accent. */
@Composable
fun GlowingRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Lit(size = 56.dp, scale = 1.5f, alpha = if (enabled) 0.16f else 0f) {
        RangeSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = modifier,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = GlowAccent,
                inactiveTrackColor = GlowTrack,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

/**
 * A filter chip that lights when it is on and goes nearly flat when it is not.
 *
 * A chip is a label rather than a value, so it fills with the accent when it is chosen instead
 * of ticking - which is also what makes a row of them readable at a glance: the chosen ones are
 * lit and the rest are not.
 */
@Composable
fun GlowingFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    Lit(size = 44.dp, scale = 1.7f, alpha = if (selected) 0.22f else 0f) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = label,
            modifier = modifier,
            leadingIcon = leadingIcon,
            shape = RoundedCornerShape(18.dp),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = GlowTrack,
                selectedBorderColor = GlowAccent.copy(alpha = 0.5f),
            ),
            colors = FilterChipDefaults.filterChipColors(
                containerColor = GlowTrack,
                labelColor = Color.White.copy(alpha = 0.7f),
                selectedContainerColor = GlowAccent.copy(alpha = 0.22f),
                selectedLabelColor = GlowAccent,
                selectedLeadingIconColor = GlowAccent,
            ),
        )
    }
}

/**
 * One segment of a segmented control.
 *
 * Built by hand rather than with SegmentedButton so it matches everything else here: a single
 * selected segment is the accent, the rest are flat, and the whole run of them is one rounded
 * shape rather than a row of separate pills.
 */
@Composable
fun GlowingSegment(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    Brush.verticalGradient(listOf(GlowAccent.copy(alpha = 0.22f), GlowAccent.copy(alpha = 0.10f)))
                } else {
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) GlowAccent.copy(alpha = 0.55f) else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            ),
    ) {
        content()
    }
}

/** The frame a run of [GlowingSegment]s sits in. */
@Composable
fun GlowingSegmentTrack(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GlowTrack.copy(alpha = 0.5f))
            .border(BorderStroke(1.dp, GlowTrack), RoundedCornerShape(16.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}
