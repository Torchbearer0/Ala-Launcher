// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

/**
 * How square the rounded controls are.
 *
 * Material's own switch is a pill: the track's radius is half its height, so the ends are
 * semicircles and the thing reads as a lozenge. Rounded here means a rectangle whose corners are
 * turned but still visibly corners - close enough to a squircle that it looks deliberate, far
 * enough from a pill that it stops reading as one. Seven dp on a track twenty-four tall is about a
 * third of the height, which is where the shape turns from a lozenge into a rounded rectangle.
 */
private val RoundedTrackShape = RoundedCornerShape(7.dp)

/** The same idea for the tick, which was square with its corners turned rather than round. */
private val RoundedTickShape = RoundedCornerShape(5.dp)

/** What an unticked control is drawn in: grey, and not one of the accent's. */
private val OffInk = Color(0xFF6E7276)

private val SWITCH_TRACK_W = 46.dp
private val SWITCH_TRACK_H = 24.dp
private val SWITCH_THUMB = 18.dp
private val SWITCH_INSET = 3.dp

/**
 * A switch that is lit rather than coloured, and rounded rather than a pill.
 *
 * Drawn rather than taken from Material: Material3 1.3 took the shape parameters off `Switch` and
 * left the pill as the only option, so there is nothing to round without replacing it. Building
 * it out of a box and a circle is less code than working around that, and it means the off state
 * can be exactly what it should be - grey, with no glow and none of the accent anywhere in it.
 * Everything that is on is lit; nothing that is off is lit at all.
 */
@Composable
fun GlowingSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val travel = SWITCH_TRACK_W - SWITCH_THUMB - SWITCH_INSET * 2
    val offset by animateDpAsState(
        targetValue = if (checked) travel + SWITCH_INSET else SWITCH_INSET,
        animationSpec = tween(170),
        label = "switchThumb",
    )

    Box(contentAlignment = Alignment.Center) {
        GlowHalo(size = 64.dp, scale = 1.45f, alpha = if (checked) 0.34f else 0f)
        Box(
            Modifier
                .size(SWITCH_TRACK_W, SWITCH_TRACK_H)
                .clip(RoundedTrackShape)
                .background(if (checked) GlowAccent else GlowTrack)
                .border(1.dp, if (checked) Color.Transparent else OffInk, RoundedTrackShape)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        ) {
            Box(
                Modifier
                    .offset(x = offset)
                    .align(Alignment.CenterStart)
                    .size(SWITCH_THUMB)
                    .clip(CircleShape)
                    .background(if (checked) Color.White else OffInk),
            )
        }
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
 * Two things changed from the plain line. The active track carries its own halo, because a flat
 * band of colour on a black screen is a line and not light - the halo is what makes it read as lit,
 * and it is drawn under the track rather than around it so it never widens the bar it is lighting.
 * And the handle is a ball: a bar with a line running out of both sides of it reads as a break in
 * the track, whereas a circle reads as something sitting on it, which is what a handle is.
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
    val span = valueRange.endInclusive - valueRange.start
    val fraction = if (span > 0f) ((value - valueRange.start) / span).coerceIn(0f, 1f) else 0f

    Box(modifier) {
        // The track is drawn here rather than left to Material, because the lit half needs its
        // halo clipped to exactly the width of the colour - Material's track is drawn on top of
        // whatever is behind it and gives nowhere to put one.
        Box(
            Modifier
                .fillMaxWidth()
                .height(SLIDER_TRACK_HEIGHT)
                .clip(CircleShape)
                .background(GlowTrack, CircleShape),
        ) {
            // Clipped to the filled width, so the light stops where the colour stops instead of
            // washing the whole slider and telling the eye the whole thing is lit.
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
                        .background(GlowAccent, CircleShape),
                )
            }
        }

        // Material's slider on top, with everything of its own made invisible: it is here for its
        // drag handling, its accessibility and its range steps, not for its paint.
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                disabledActiveTrackColor = Color.Transparent,
                disabledInactiveTrackColor = Color.Transparent,
                disabledThumbColor = Color.Transparent,
            ),
            track = {},
            thumb = {
                // A halo the size of the ball, and the ball. The light is what the handle is made
                // of rather than a ring drawn around something else.
                Box(contentAlignment = Alignment.Center) {
                    GlowHalo(size = SLIDER_THUMB_SIZE * 2.8f, scale = 1f, alpha = if (enabled) 0.65f else 0f)
                    Box(
                        Modifier
                            .size(SLIDER_THUMB_SIZE)
                            .background(if (enabled) Color.White else OffInk, CircleShape),
                    )
                }
            },
        )
    }
}

private val SLIDER_THUMB_SIZE = 18.dp
private val SLIDER_TRACK_HEIGHT = 6.dp

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
