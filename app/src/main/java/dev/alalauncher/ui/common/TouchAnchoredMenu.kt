// SPDX-License-Identifier: GPL-3.0-or-later
package dev.alalauncher.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * A dropdown that opens at the finger rather than beneath whatever it happens to sit in.
 *
 * A DropdownMenu is a Popup, and a Popup anchors to the bounds of its *parent layout node*,
 * not to a point — Material then places it at that parent's bottom edge plus the offset. On a
 * row one line tall that error is invisible, but on something tall, like a 300dp widget, the
 * menu lands most of a screen below the press or flips above the whole thing. Giving it a
 * zero-size parent positioned at the touch point makes those bounds the fingertip, so the menu
 * opens against the finger and flips around it when there is no room below.
 *
 * Also what tells the layer behind it to blur. Done here rather than by the caller so that every
 * menu blurs, including the ones nobody remembered to wire up — the point of a translucent menu is
 * that it is read against a softened background, and a caller that forgets gets an unreadable one.
 */
@Composable
fun TouchAnchoredMenu(
    expanded: Boolean,
    offset: DpOffset,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Keyed on the flag, so opening acquires and closing releases. The release is in onDispose
    // because a menu taken out of composition while open never reports its own close.
    DisposableEffect(expanded) {
        if (!expanded) return@DisposableEffect onDispose {}
        MenuBlur.acquire()
        onDispose { MenuBlur.release() }
    }

    Box(modifier = Modifier.offset(x = offset.x, y = offset.y).size(0.dp)) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            shape = RoundedCornerShape(24.dp),
            // Kept translucent like the AppPanel: legibility comes from blurring the home
            // screen behind it, not from darkening the panel itself.
            containerColor = Color.Black.copy(alpha = 0.22f),
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            content = content,
        )
    }
}
