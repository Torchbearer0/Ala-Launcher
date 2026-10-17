// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import android.content.pm.ShortcutInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.ShortcutSwipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Most an app is allowed to contribute, so a menu stays a menu rather than a list. */
private const val MAX_SHORTCUTS = 5

/**
 * The shortcuts an app publishes, as items at the top of its long-press menu.
 *
 * Read when the menu opens rather than with the app list: it is a call into the system per
 * app, and an app list is hundreds of apps of which one is ever asked about.
 *
 * Emits nothing at all when an app publishes none, so the menu is unchanged for most of them.
 */
@Composable
fun AppShortcutItems(
    app: AppInfo,
    expanded: Boolean,
    labelSizeSp: Int,
    onStarted: () -> Unit,
) {
    val context = LocalContext.current
    val victoriaApp = context.applicationContext as VictoriaApp
    val density = LocalConfiguration.current.densityDpi
    var shortcuts by remember(app.key) { mutableStateOf<List<ShortcutInfo>>(emptyList()) }

    LaunchedEffect(app.key, expanded) {
        if (!expanded) return@LaunchedEffect
        shortcuts = withContext(Dispatchers.IO) {
            victoriaApp.appRepository.appShortcuts(app).take(MAX_SHORTCUTS)
        }
    }

    if (shortcuts.isEmpty()) return

    shortcuts.forEach { shortcut ->
        val label = (shortcut.shortLabel ?: shortcut.longLabel)?.toString().orEmpty()
        if (label.isBlank()) return@forEach
        val iconDp = iconSizeFor(labelSizeSp)
        val icon = remember(shortcut.id, density) {
            runCatching {
                victoriaApp.appRepository.shortcutIcon(shortcut, density)
                    ?.toBitmap(ICON_PX, ICON_PX)
                    ?.asImageBitmap()
            }.getOrNull()
        }
        DropdownMenuItem(
            text = { Text(label, fontSize = labelSizeSp.sp) },
            leadingIcon = icon?.let {
                { Image(bitmap = it, contentDescription = null, modifier = Modifier.size(iconDp)) }
            },
            onClick = {
                victoriaApp.appRepository.startAppShortcut(shortcut)
                onStarted()
            },
        )
    }
}

/** Rasterised at menu-icon size; the drawable itself is whatever density the publisher had. */
private const val ICON_PX = 72

/**
 * The icon beside a shortcut, sized from the label it sits next to.
 *
 * Tied to the launcher's own label size rather than given a setting of its own: this is the
 * same list of app names as everywhere else, and a panel that ignored the size set for those
 * was the thing worth fixing. Bounded so it stays an icon in a menu at either extreme.
 */
private fun iconSizeFor(labelSizeSp: Int): Dp =
    (labelSizeSp * 1.5f).dp.coerceIn(20.dp, 44.dp)

/**
 * Swipe a row sideways to reach what the app publishes.
 *
 * A separate gesture from the long press on purpose: shortcuts are used daily and the menu
 * behind a long press is set-up-and-done, so they do not belong in the same place.
 *
 * One direction, chosen in settings, so the other stays free to mean something later. It fires
 * once per drag rather than per pixel, and only past a distance no tap wanders.
 *
 * Nothing happens on a row whose app publishes none. There is no way to know that before
 * asking, and asking every row up front is a call into the system per app.
 */
fun Modifier.swipeForShortcuts(
    mode: ShortcutSwipe,
    enabled: Boolean,
    onSwipe: () -> Unit,
): Modifier =
    if (mode == ShortcutSwipe.OFF || !enabled) {
        this
    } else this.pointerInput(mode) {
        // Written out rather than detectHorizontalDragGestures, for one reason: that consumes
        // the change where it decides a horizontal drag has started, whichever way it is going.
        // The page on the left is reached by dragging right across the home screen, and the
        // pager it lives in would take a leftward drag as "go back". So the two need to be
        // told apart by direction, and only the row that is going to act may consume anything.
        //
        // The row's own direction is therefore claimed at the Initial pass, which runs before
        // the pager can judge the drag. A drag the other way is not consumed at all, and is
        // left for the pager — which is the whole point of the two living side by side.
        //
        // Measured on absolute positions rather than positionChange(), because the down may
        // already be consumed by something further up the tree and positionChange() reports
        // zero for a consumed change.
        val toRight = mode == ShortcutSwipe.RIGHT
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val startX = down.position.x
            var fired = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val dx = change.position.x - startX
                val myWay = if (toRight) dx > SWIPE_THRESHOLD_PX else dx < -SWIPE_THRESHOLD_PX
                if (!fired && myWay) {
                    fired = true
                    // The rest of the drag belongs to this gesture too, so the pager does not
                    // start scrolling halfway through and take the row with it.
                    change.consume()
                    onSwipe()
                }
            }
        }
    }

/** Far enough not to fire on a tap that wandered, short enough to feel like a flick. */
private const val SWIPE_THRESHOLD_PX = 90f

/**
 * The shortcuts on their own, for the swipe. Renders nothing when the app publishes none, so
 * a swipe on such a row opens an empty popup rather than a stray one.
 *
 * Anchored to the row and not to the finger, unlike the long-press menu. That one is opened
 * from somewhere on a row that may be as tall as a widget, so it has to find the finger; this
 * one is always a swipe across a row one line high, and following the finger only meant it
 * landed somewhere slightly different every time. Against the row it is in the same place at
 * the same size on every use, which is what makes it something you reach for rather than read.
 */
@Composable
fun AppShortcutMenu(app: AppInfo, expanded: Boolean, labelSizeSp: Int, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        AppShortcutItems(app, expanded, labelSizeSp) { onDismiss() }
    }
}
