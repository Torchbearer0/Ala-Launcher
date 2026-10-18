// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.settings

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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

    // The working copy, as a plain state object rather than a `by` delegate, and read through
    // `.value` **inside** the drag handler.
    //
    // Keyed on the stored list, so a key added or removed elsewhere resets it. Nothing is written
    // back until the finger lifts; while a drag is in flight this is what gets drawn.
    //
    // Reading it directly matters, and the reason is the worst bug this component has had. It used
    // to be read through `rememberUpdatedState`, which is refreshed **during recomposition** - so
    // between the handler's first write and the next recomposition it handed back the *old* list.
    // The handler then computed the dragged row's position against a list that no longer existed,
    // and built the new order out of that stale one, discarding every swap it had already made. The
    // symptom was a row that would leap several places on the first swap and then thrash in place
    // for the rest of the drag, scrolling against itself without ever settling.
    //
    // A `MutableState.value` read inside the drag coroutine is a snapshot read, so it is always the
    // value as of now. `rememberUpdatedState` is the wrong tool here: it exists to bridge a
    // *composition* boundary, and this is a gesture that has to see its own writes immediately.
    val orderState = remember(keys) { mutableStateOf(keys) }

    /**
     * Which row is being dragged, by key rather than by position.
     *
     * It has to be the key. A reorder changes positions and nothing else, so a position is not
     * something the drag can afford to be holding on to - see the note on `pointerInput` below for
     * what that cost.
     */
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        orderState.value.forEachIndexed { index, itemKey ->
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
                                // **Claim the press immediately**, before the parent LazyColumn
                                // can.
                                //
                                // This list is the body of a `LazyColumn`, so a vertical drag on a
                                // row and a scroll of the list are literally the same gesture, and
                                // `detectDragGestures` does not claim anything until the touch has
                                // travelled a whole slop. In that window the LazyColumn wins the
                                // race and starts scrolling - the list slides out from under the
                                // finger mid-drag, which reads as a row scrolling endlessly against
                                // itself. Reordering still worked; it just looked broken.
                                //
                                // So the down is consumed on the first event, which stops the parent
                                // ever seeing a drag it could claim. Same reasoning as the slider's
                                // own pointer input, and for the same reason.
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()

                                    // The row does not move until the finger does, so a tick
                                    // is the only confirmation that the grab took.
                                    HapticUtil.tick(view, true)
                                    dragOffset = 0f
                                    draggingKey = itemKey

                                    // Absolute positions, never `positionChange()`: a change this
                                    // block has consumed reports no movement, and reading deltas off
                                    // a consumed change stops the drag dead.
                                    var lastY = down.position.y

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change =
                                            event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) break
                                        change.consume()

                                        val y = change.position.y
                                        val dy = y - lastY
                                        lastY = y

                                        // Read once, and every decision below is made against
                                        // this same snapshot. Reading it twice would let the
                                        // second read see a write the first one did not.
                                        val current = orderState.value
                                        // Looked up every time rather than remembered, because
                                        // this is the only thing that knows where the row is *now*.
                                        val from = current.indexOf(itemKey)
                                        if (from < 0) continue
                                        dragOffset += dy
                                        val shift = (dragOffset / rowPx).roundToInt()
                                        if (shift == 0) continue
                                        val to = (from + shift).coerceIn(0, current.lastIndex)
                                        if (to == from) continue
                                        orderState.value = current.toMutableList().apply {
                                            add(to, removeAt(from))
                                        }
                                        // The row has moved under the finger, so the offset it
                                        // is drawn at has to come back by as much. Clamped, because
                                        // a fast flick can deliver a whole row's worth of travel in
                                        // one event and an unclamped correction overshoots into the
                                        // opposite direction - which is what made a hard drag walk
                                        // the row up and back down forever.
                                        dragOffset -= ((to - from) * rowPx).coerceIn(-rowPx, rowPx)
                                        HapticUtil.tick(view, true)
                                    }

                                    // Committed on release, once, against the list as it now is.
                                    if (draggingKey != null) onReorder(orderState.value)
                                    draggingKey = null
                                    dragOffset = 0f
                                }
                            },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            }
        }
    }
}
