// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.victorialauncher.data.IconShape

/**
 * The parts of the settings screen every section is built from, kept out of [SettingsScreen]
 * so that screen is a list of settings rather than a list of drawing code.
 *
 * The look is one rounded surface per section with the rows inside it, rather than a screen of
 * dividers. A divider-separated list reads as a settings *dialog*; a card per section reads as
 * something with parts, and it is why the rows inside a section can be tighter than the gaps
 * between sections.
 */

/**
 * The three values the settings screen is drawn from, in one place because they are one idea.
 *
 * On a true-black page a card cannot separate itself by being lighter — there is nothing above
 * black to be lighter *than*. So the card sits a few steps off black and is separated by a
 * hairline rather than by a difference in fill, which is the whole trick: on OLED an unlit
 * border costs nothing, and a grey card costs every pixel it covers.
 */
internal object SettingsTone {
    /** The page. True black: an OLED pixel off is a pixel not drawing power. */
    val page = Color.Black

    /** The card. Barely off black, so it is a surface and not a hole. */
    val card = Color(0xFF0B0B0E)

    /** Row dividers inside a card, quieter than the card's own edge. */
    val hairline = Color(0x14FFFFFF)

    /** The edge of a card, the only thing that draws it on black. */
    val edge = Color(0x1FFFFFFF)

    /**
     * The one colour anything on this screen is allowed to be bright with.
     *
     * Sailfish keeps a black screen and lets a handful of things shine on it, and what makes it
     * work is that nothing else competes. A cold cyan rather than the app's own teal: it reads
     * as light coming off the screen rather than as another material colour.
     */
    val accent = Color(0xFF6FE3F5)
}

/**
 * An icon standing on its own light.
 *
 * The glow is a radial falloff rather than a blur. There is nothing here to hand a RenderEffect
 * - it is drawn by the app, not by the window - and a blurred layer re-applied every frame is
 * the wrong thing for a list that scrolls continuously. A gradient costs the same over black as
 * over anything else, and at 30dp the two are not distinguishable.
 *
 * The halo is drawn larger than its slot on purpose. Held to the mark's own size it reads as a
 * tinted tile, and a tinted tile is what the grey one this replaced looked like.
 */
@Composable
internal fun GlowingMark(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    glow: Color = SettingsTone.accent,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer { scaleX = 2.1f; scaleY = 2.1f }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            glow.copy(alpha = 0.26f),
                            glow.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .size(size * 0.68f)
                .clip(RoundedCornerShape(size * 0.24f))
                .background(
                    Brush.linearGradient(
                        listOf(glow.copy(alpha = 0.22f), glow.copy(alpha = 0.06f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = glow,
                modifier = Modifier.size(size * 0.36f),
            )
        }
    }
}

/** One section's card. */
@Composable
internal fun SettingsSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SettingsTone.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, SettingsTone.edge),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // Light caught along the top edge, which is what makes a flat card read as a raised
            // one on a screen with nothing else to judge depth by. Fades out at both ends so it
            // looks like the light is coming from above rather than like a border on one side.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                SettingsTone.edge,
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Column(content = content)
        }
    }
}

/** The heading above a card. Small, quiet, and indented to line up with the rows inside it. */
@Composable
internal fun SettingsSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        // The one bright colour on the screen, spent on the heading rather than on the rows:
        // it is what makes a wall of black cards read as a set of named places.
        color = SettingsTone.accent.copy(alpha = 0.82f),
        fontWeight = FontWeight.SemiBold,
        letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
        modifier = Modifier.padding(start = 22.dp, bottom = 8.dp),
    )
}

/**
 * A row with an optional second line of explanation and an optional control at the end.
 *
 * The padding here is the spacing the old list-of-dividers layout could not have: rows sit close
 * together inside a card and the cards sit far apart, which is what makes a section legible as
 * one thing without a single rule on the screen.
 */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 22.dp, end = 18.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/**
 * A switch row. The whole row is the target and the switch is not, or a press on the switch
 * toggles it and then the row toggles it straight back — a switch that will not stay put.
 */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    )
}

/**
 * A preview of what a setting is about to do, shown inside its own row.
 *
 * "Peek" rather than a preview of the finished screen: a miniature of the thing being changed,
 * next to the control that changes it, so the connection is made without leaving the page or
 * waiting for a redraw somewhere else. It is a few composables rather than a second copy of the
 * home screen — the point is to show the shape of the effect, not to be the effect.
 */
@Composable
internal fun PeekPreview(
    modifier: Modifier = Modifier,
    height: Dp = 76.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            // A hairline rather than a fill: enough to read as a framed sample, not enough to
            // compete with the setting it belongs to.
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                shape = RoundedCornerShape(16.dp),
            ),
    ) {
        content()
    }
}

/**
 * The wallpaper tint at the strength it will actually be applied.
 *
 * Gradient rather than a flat fill because the setting dims a gradient over a wallpaper, and a
 * flat swatch cannot show that; the warm-to-cool sweep stands in for a photo and shows the
 * strength of the wash from either end.
 */
@Composable
internal fun WallpaperDimPeek(alpha: Float, modifier: Modifier = Modifier) {
    PeekPreview(modifier = modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF3E4A59), Color(0xFF7C6A58), Color(0xFF2C3440)),
                    ),
                )
                .background(Color.Black.copy(alpha = alpha.coerceIn(0f, 1f))),
        )
    }
}

/** Where the home screen's rows will sit, at the alignment being chosen. */
@Composable
internal fun AlignmentPeek(
    alignedRight: Boolean,
    label: String,
    modifier: Modifier = Modifier,
) {
    PeekPreview(modifier = modifier, height = 68.dp) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = if (alignedRight) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .fillMaxWidth(if (alignedRight) (0.72f - index * 0.12f) else (0.72f - index * 0.12f))
                        .height(9.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f)),
                )
            }
        }
    }
}

/**
 * An app icon in the shape being chosen, at a size that shows the difference.
 *
 * SYSTEM is drawn as a circle because that is what the styling actually falls back to when a
 * launcher wants a shape of its own — see shapePath in IconStyling, which treats SYSTEM and
 * CIRCLE the same way.
 */
@Composable
internal fun IconShapePeek(shape: IconShape, modifier: Modifier = Modifier) {
    val outline: androidx.compose.ui.graphics.Shape = when (shape) {
        IconShape.SYSTEM, IconShape.CIRCLE -> CircleShape
        IconShape.ROUNDED -> RoundedCornerShape(10.dp)
        IconShape.SQUARE -> RoundedCornerShape(2.dp)
    }
    PeekPreview(modifier = modifier, height = 68.dp) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(outline)
                    .background(
                        Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
                        ),
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                        shape = outline,
                    ),
            )
        }
    }
}

/** A dot, used beside a section heading to say at a glance which one is open. */
@Composable
internal fun SectionDot(color: Color, size: Dp = 6.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}