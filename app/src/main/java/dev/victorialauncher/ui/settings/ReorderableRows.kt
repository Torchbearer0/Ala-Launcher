// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.settings

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.victorialauncher.R
import dev.victorialauncher.service.HapticUtil
import kotlin.math.roundToInt

/** Every reorderable row is this tall, so a drag works out where it landed by division. */
val REORDER_ROW_HEIGHT = 60.dp

/**
 * A short list of keys that can be dragged into a different order.
 *
 * Deliberately not lazy, and deliberately fixed height. Both are what make the drag simple:
 * nothing recycles the row under the finger, and how many places it has travelled is the
 * offset over one row. The lists this serves — a folder's members, the favorites — are short
 * enough that laziness would buy nothing.
 *
 * [onReorder] fires once, when the finger lifts, so the store is not rewritten on every frame
 * of a drag.
 */
@Composable
fun ReorderableRows(
    keys: List<String>,
    onReorder: (List<String>) -> Unit,
    row: @Composable RowScope.(key: String) -> Unit,
) {
    val view = LocalView.current
    val rowPx = with(LocalDensity.current) { REORDER_ROW_HEIGHT.toPx() }

    // Keyed on the stored list, so a key added or removed elsewhere resets the working copy.
    // While a drag is in flight this is what gets drawn, since nothing is written until the
    // finger lifts.
    var order by remember(keys) { mutableStateOf(keys) }

    /**
     * Which row is being dragged, by key rather than by position.
     *
     * It has to be the key. A reorder changes positions and nothing else, so a position is not
     * something the drag can afford to be holding on to - see the note on `pointerInput` below for
     * what that cost.
     */
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // Read through these rather than captured. The drag handler has to survive a reorder, so it
    // cannot be restarted by one, so it cannot close over the state it is about to change.
    val orderNow = rememberUpdatedState(order)

    Column(modifier = Modifier.fillMaxWidth()) {
        order.forEachIndexed { index, itemKey ->
            val dragging = draggingKey == itemKey
            // Each row keeps its own node. Without this Compose reuses them by position, so after
            // a swap the handle that was on the dragged row is now on whichever row moved into
            // its slot - which is how a drag ends up moving the wrong thing.
            key(itemKey) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(REORDER_ROW_HEIGHT)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            if (dragging) {
                                translationY = dragOffset
                                alpha = 0.9f
                            }
                        }
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row(itemKey)
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = stringResource(R.string.home_drag_handle),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(40.dp)
                            // Keyed on the row's own key, which a reorder does not change.
                            //
                            // This used to be keyed on the index, which restarted the block the
                            // instant the first swap landed - and a swap is the entire purpose of
                            // the gesture. Restarting cancelled it through `onDragCancel`, so a
                            // drag could move a row exactly one place and then died, leaving the
                            // order however far the finger had actually travelled. Keyed on the
                            // key, the handler starts once and keeps going until the finger lifts.
                            .pointerInput(itemKey) {
                                detectDragGestures(
                                    onDragStart = {
                                        // The row does not move until the finger does, so a tick
                                        // is the only confirmation that the grab took.
                                        HapticUtil.tick(view, true)
                                        dragOffset = 0f
                                        draggingKey = itemKey
                                    },
                                    onDragEnd = {
                                        if (draggingKey != null) onReorder(orderNow.value)
                                        draggingKey = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingKey = null
                                        dragOffset = 0f
                                    },
                                ) { change, amount ->
                                    change.consume()
                                    val current = orderNow.value
                                    // Looked up every time rather than remembered, because this
                                    // is the only thing that knows where the row is *now*.
                                    val from = current.indexOf(itemKey)
                                    if (from < 0) return@detectDragGestures
                                    dragOffset += amount.y
                                    val shift = (dragOffset / rowPx).roundToInt()
                                    if (shift == 0) return@detectDragGestures
                                    val to = (from + shift).coerceIn(0, current.lastIndex)
                                    if (to == from) return@detectDragGestures
                                    order = current.toMutableList().apply { add(to, removeAt(from)) }
                                    // The row has moved under the finger, so the offset it
                                    // is drawn at has to come back by as much.
                                    dragOffset -= (to - from) * rowPx
                                    HapticUtil.tick(view, true)
                                }
                            },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            }
        }
    }
}
