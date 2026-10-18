// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
 * **White, not cyan.** On an AMOLED panel the pixels are off when they are black, so white is the
 * only colour that reads as the screen switched on rather than as paint applied to it. A cyan
 * accent looks lit on an LCD and merely bright on an OLED; white looks lit on both, and it is the
 * only thing here that does not tint the black it sits on.
 *
 * Dull and dim when something is off, bright and blooming when it is on. There is no third state
 * where a control is coloured but not glowing.
 */
val GlowAccent = Color(0xFFFFFFFF)

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

/** How dark an unlit orb is. Dim, but not invisible - it has to be findable. */
private val OrbOffFill = Color(0xFF23262A)

private val ORB_SIZE = 24.dp

/**
 * A point of light: dim and small when it is off, bright and blooming when it is on.
 *
 * Not a track with a travelling dot. The Sailfish idea is that a switch is not a mechanism you
 * operate but a lamp you turn on, and a lamp does not have a handle.
 *
 * The bloom is **dot matrix**, not a blur, and that is the whole look: rings of dots around the
 * orb, thinning and fading outwards, so the glow is made of the same marks as everything else on
 * the screen rather than being a soft wash laid over it.
 *
 * ## Why it cannot spill
 *
 * The dots are drawn inside a box that is clipped to itself. A halo that scales a gradient past
 * its own bounds - which is what [GlowHalo] does, and what every version of this used to do -
 * paints over whatever is next to it, and on a settings list of ticked rows that meant every
 * row's light landing on the rows above and below it. Confining the drawing to its own box makes
 * the spill impossible rather than merely smaller, so the row spacing no longer has to be
 * negotiated against the glow.
 *
 * Off it is a small dark circle with the faintest suggestion of dots around it - an unlit LED.
 * On it is a white orb with the matrix lit around it, growing as it lights.
 */
@Composable
fun GlowOrb(
    active: Boolean,
    modifier: Modifier = Modifier,
    /** How far the dot matrix reaches, as a fraction of the orb's own radius. */
    bloom: Float = 2.6f,
    onClick: (() -> Unit)? = null,
    role: Role? = null,
    onValueChange: ((Boolean) -> Unit)? = null,
) {
    val lit by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(220),
        label = "orbLit",
    )

    Box(
        modifier = modifier
            .clipToBounds()
            .then(
                when {
                    onValueChange != null && role != null ->
                        Modifier.selectable(
                            selected = active,
                            role = role,
                            onClick = { onValueChange(!active) },
                        )
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f
            val dot = r * 0.17f

            // The matrix first, so the orb sits on top of it rather than under it.
            //
            // Rings of dots, each further out, smaller and fainter than the last. The falloff is
            // squared because a linear one reads as a flat disc with a hard edge; squared it
            // reads as light, which spends most of its brightness close to the source.
            val rings = 4
            for (ring in 1..rings) {
                val t = ring / (rings + 1f)
                val ringR = r * (1f + bloom * t)
                val alpha = (1f - t) * (1f - t) * lit * 0.85f
                if (alpha < 0.02f) continue
                val count = 6 + ring * 4
                val d = dot * (1f - 0.5f * t)
                for (i in 0 until count) {
                    val a = (i.toFloat() / count) * 2f * PI.toFloat()
                    drawCircle(
                        color = GlowAccent.copy(alpha = alpha),
                        radius = d,
                        center = Offset(c.x + ringR * cos(a), c.y + ringR * sin(a)),
                    )
                }
            }

            // The orb itself. Off it is a dark disc with a dim rim, so it reads as a lamp that is
            // not working rather than as a hole in the row.
            drawCircle(
                color = if (lit > 0.5f) GlowAccent else OrbOffFill,
                radius = r * (0.34f + 0.10f * lit),
                center = c,
            )
            if (lit <= 0.5f) {
                drawCircle(
                    color = OffInk.copy(alpha = 0.7f),
                    radius = r * 0.34f,
                    center = c,
                    style = Stroke(width = r * 0.10f),
                )
            }
            // The hot centre, only once it is on.
            if (lit > 0.02f) {
                drawCircle(color = Color.White.copy(alpha = 0.9f * lit), radius = r * 0.18f * lit, center = c)
            }
        }
    }
}

/** A switch: one lamp. Grey and dark when off, a lit orb when on. */
@Composable
fun GlowingSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    GlowOrb(
        active = checked,
        modifier = Modifier.size(ORB_SIZE),
        role = Role.Switch,
        onValueChange = onCheckedChange,
    )
}

/**
 * A checkbox, as the same lamp.
 *
 * A square box with a tick is a form to fill in; this is a decision, and it looks like one. The
 * same orb everywhere means a ticked row in a list of apps is unmistakable at a glance without
 * reading it - which is what the square-and-tick version was for, and it did that job less well.
 */
@Composable
fun GlowingCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    GlowOrb(
        active = checked,
        modifier = Modifier.size(22.dp),
        role = Role.Checkbox,
        onValueChange = onCheckedChange,
    )
}

/** A radio marker, as the same lamp. Selected is lit; the others stay dark. */
@Composable
fun GlowingRadioButton(selected: Boolean, onClick: () -> Unit) {
    GlowOrb(
        active = selected,
        modifier = Modifier.size(22.dp),
        onClick = onClick,
    )
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

        // The lit part of the line. No halo on it: at two dp a glow around the line reads as a
        // second, fatter line rather than as light, and the handle is where the brightness
        // belongs now.
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(if (enabled) GlowAccent else OffInk, CircleShape),
        )

        // The handle: an orb, at one end of the travel or the other and nowhere in between.
        Box(
            Modifier
                .offset { IntOffset((travel * fraction).roundToInt(), 0) }
                .size(SLIDER_THUMB_SIZE)
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            // Always lit, even when the slider is disabled: a handle that goes dark reads as a
            // slider that is broken rather than one that cannot be moved.
            GlowOrb(active = true, bloom = 1.9f)
        }
    }
}

/**
 * The handle is an orb, the same one every switch and tick is.
 *
 * Wider than the line it sits on, deliberately: the bloom needs room inside its own box to be
 * clipped rather than painted over the rows beside it, and the dot matrix needs somewhere to be.
 */
private val SLIDER_THUMB_SIZE = 30.dp

/**
 * Two dp. It was six, and at six the filled half stopped reading as a line with a light on it and
 * started reading as a bar being dragged along a channel. The handle carries the brightness now,
 * so the track does not have to as well.
 */
private val SLIDER_TRACK_HEIGHT = 2.dp

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
