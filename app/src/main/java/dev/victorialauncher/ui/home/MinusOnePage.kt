// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.victorialauncher.R
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.MINUS_ONE_NEW_WIDGET_HEIGHT_DP
import dev.victorialauncher.data.MinusOneEntry
import dev.victorialauncher.data.MinusOneMode
import dev.victorialauncher.data.MinusOnePage
import dev.victorialauncher.widget.HostedWidget
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The page to the left of the home screen.
 *
 * ## Why it is this shape
 *
 * The whole placement model is an ordered list plus a size per entry. That is not a
 * simplification left over from a simpler design - it is the model Flow Launcher itself uses,
 * read out of `com.app.flowlauncher.widgets.WidgetModel` in Flow 40.1.6:
 *
 *     data class WidgetModel(widgetId: Int, dimensionsInLine: Int?, habitId: Long = -1)
 *
 * Searching that package for row, column, page, x, y, left, top or cellIndex returns nothing.
 * There are no coordinates. A widget's place is its index, and the only thing a user changes
 * about it is how big it is.
 *
 * The previous version of this page stored an absolute position per entry and resolved drops
 * against it. That is a layout engine with a collision problem, and it was buggy in six
 * separate ways before it was removed: a stale view per slot, a drag that only worked
 * downwards, writes applied to a stale snapshot, the gesture read from the wrong callback, one
 * MOVE event arriving out of forty, and a drop landing on top of another widget.
 *
 * Every one of those lived in machinery this model does not contain. Overlap is not a state it
 * can enter, so there is no overlap bug. Packing is automatic, so there is nothing to place.
 * A widget moving up and its neighbour coming down are not two behaviours - they are one list
 * reordered, and the neighbour is not told to do anything, it simply is no longer in that slot.
 *
 * What is added to Flow's model is width, so two narrow widgets can share a row. That is the
 * one dimension Flow has no concept of, and it is why the row is a wrapping FlowRow rather than
 * a single column.
 */
@Composable
fun MinusOnePage(
    page: MinusOnePage,
    appsByKey: Map<String, AppInfo>,
    contentColor: Color,
    /**
     * Asks for a widget to be added.
     *
     * [insertIndex] is where in the list it should go, or null to put it on the end. The page works
     * both out from the room that is actually left over, so a widget arrives already the right
     * size and already in the right place rather than somewhere the user then has to drag it out
     * of. That is the whole reason free resizing is worth having: the space a narrower widget
     * gives up has to be fillable, or giving it up only makes the page worse.
     */
    onAddWidget: (insertIndex: Int?, widthDp: Int, heightDp: Int) -> Unit,
    onResize: (index: Int, widthDp: Int, heightDp: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    /** Which entry is being arranged, and who changes it. Held by the route - see HomeRoute. */
    editingIndex: Int? = null,
    onEditingChange: (Int?) -> Unit = {},
    onResizeActiveChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (page.mode) {
        MinusOneMode.OFF -> Box(modifier)
        MinusOneMode.WIDGETS -> WidgetList(
            entries = page.entries,
            appsByKey = appsByKey,
            contentColor = contentColor,
            onAddWidget = onAddWidget,
            onResize = onResize,
            onRemove = onRemove,
            onMove = onMove,
            onLaunch = onLaunch,
            onOpenSettings = onOpenSettings,
            editingIndex = editingIndex,
            onEditingChange = onEditingChange,
            onResizeActiveChange = onResizeActiveChange,
            modifier = modifier,
        )
    }
}

/** The widget mode page: one wrapping line of entries, packed in list order. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetList(
    entries: List<MinusOneEntry>,
    appsByKey: Map<String, AppInfo>,
    contentColor: Color,
    onAddWidget: (insertIndex: Int?, widthDp: Int, heightDp: Int) -> Unit,
    onResize: (index: Int, widthDp: Int, heightDp: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    editingIndex: Int?,
    onEditingChange: (Int?) -> Unit,
    onResizeActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    /**
     * How wide the row is, so the room left on it can be worked out.
     *
     * This is the one measurement the gap arithmetic needs, and it is read once per layout rather
     * than per entry: the row is the same width whatever is in it.
     */
    var rowWidthDp by remember { mutableIntStateOf(0) }

    /**
     * Where each entry is on screen, in window coordinates.
     *
     * Recorded on layout rather than worked out from the list, because the list does not know
     * how tall its entries are: a resized widget is a different height, and two entries share a
     * row when one is narrow. [WidgetReorder] compares against these, so a drag is "over
     * whatever the finger is over" rather than a fixed number of steps.
     */
    val entryTops = remember { mutableStateMapOf<Int, Float>() }

    /** A full-width entry stores 0 to mean "fill", which is not a number a drag can be added to. */
    val measuredWidthDp = remember { mutableStateMapOf<Int, Int>() }

    /** Which entry is currently under the finger, and how far along it the finger has travelled. */
    var heldIndex by remember { mutableStateOf<Int?>(null) }

    /** The middle of an entry on screen, or null before it has been laid out. */
    fun centreY(index: Int): Float? {
        val top = entryTops[index] ?: return null
        val entry = entries.getOrNull(index) ?: return null
        return top + with(density) { entry.heightDp.dp.toPx() } / 2f
    }

    // A drag does one thing: it moves the held entry to the index the finger is over. The list
    // then packs itself, so everything above and below shifts without being told to.
    fun reorderTo(fingerY: Float) {
        val held = heldIndex ?: return
        if (entries.size < 2) return
        val to = WidgetReorder.targetIndex(held, ::centreY, fingerY, entries.lastIndex)
        if (to == held) return
        onMove(held, to)
        heldIndex = to
        // Arranging follows the entry rather than the slot, or the handles and the cross stay
        // behind on whichever entry had been sitting there.
        onEditingChange(to)
    }

    /**
     * Where the next widget would go, and how big it should be to fill the space.
     *
     * Recomputed on every composition rather than remembered, because it depends on the whole list
     * and the row's width: resize one widget and the answer changes, so anything cached would be
     * answering about the page as it used to be.
     */
    val freeSpace = WidgetPageFill.firstGap(
        entries = entries.map { WidgetPageFill.Slot(it.widthDp, it.heightDp) },
        containerDp = rowWidthDp,
        gapDp = ENTRY_LINE_GAP_DP,
        minWidthDp = MIN_FILLABLE_WIDTH_DP,
    )

    /**
     * Asks for a widget. Fills the room if there is any, and otherwise goes on the end - which is
     * what makes the button work on an empty page and on a page that is exactly full, rather than
     * only in the one case where a gap happens to exist.
     */
    fun addWidget() {
        val gap = freeSpace
        onAddWidget(
            gap?.insertIndex,
            gap?.widthDp ?: 0,
            gap?.heightDp ?: MINUS_ONE_NEW_WIDGET_HEIGHT_DP,
        )
    }

    Box(modifier.fillMaxSize()) {
        // Its own surface, so the page is a place rather than widgets floating over the home
        // screen it was reached from. Graded rather than flat: content sits under the top, and a
        // flat wash leaves the middle of the stack floating.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.45f to Color.Black.copy(alpha = 0.62f),
                        1f to Color.Black.copy(alpha = 0.72f),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(20.dp))

            if (entries.isEmpty()) {
                EmptyStackHint(contentColor, ::addWidget, onOpenSettings)
                Spacer(Modifier.height(20.dp))
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { rowWidthDp = with(density) { it.width.toDp().value.toInt() } },
                horizontalArrangement = Arrangement.spacedBy(ENTRY_GAP_DP.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                entries.forEachIndexed { index, entry ->
                    val editing = editingIndex == index
                    Entry(
                        index = index,
                        entry = entry,
                        editing = editing,
                        measuredWidthDp = measuredWidthDp,
                        entryTops = entryTops,
                        appsByKey = appsByKey,
                        contentColor = contentColor,
                        onLaunch = onLaunch,
                        onEditingChange = onEditingChange,
                        onResizeActiveChange = onResizeActiveChange,
                        onDragStart = {
                            heldIndex = index
                            onEditingChange(index)
                        },
                        onDragMove = ::reorderTo,
                        onDragEnd = { heldIndex = null },
                        onResize = onResize,
                        onRemove = onRemove,
                    )
                }
            }

            // The space a resized widget gave up, said out loud.
            //
            // It is drawn under the list rather than inside it on purpose. Putting a placeholder in
            // the row would take up the very room it is describing, and anything after it would no
            // longer fit where it used to - so the page would change shape just by having a gap
            // shown. A caption has no such effect, and it answers the question the gap raises:
            // what is this space for?
            if (freeSpace != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.minus_one_free_space, freeSpace.widthDp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.75f),
                )
            }

            if (entries.isNotEmpty()) Spacer(Modifier.height(96.dp))
        }

        // The one control this page has: a floating circle, so it stays in the same place
        // whatever is on the page and never pushes the list around as things are added.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 20.dp)
                .size(FAB_SIZE_DP.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = ::addWidget)
                .semantics {
                    contentDescription = if (freeSpace != null) {
                        ADD_WIDGET_LABEL + ", " + freeSpace.widthDp + " dp free"
                    } else {
                        ADD_WIDGET_LABEL
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "+",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Light,
            )
        }
    }
}

/**
 * One entry: the widget or the shortcut, plus the furniture that appears while it is being
 * arranged.
 *
 * The furniture is only ever drawn on the entry being arranged. A page carrying handles and
 * crosses over every widget is an editing surface, not a page of widgets.
 */
@Composable
private fun Entry(
    index: Int,
    entry: MinusOneEntry,
    editing: Boolean,
    measuredWidthDp: MutableMap<Int, Int>,
    entryTops: MutableMap<Int, Float>,
    appsByKey: Map<String, AppInfo>,
    contentColor: Color,
    onLaunch: (AppInfo) -> Unit,
    onEditingChange: (Int?) -> Unit,
    onResizeActiveChange: (Boolean) -> Unit,
    onDragStart: () -> Unit,
    onDragMove: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onResize: (index: Int, widthDp: Int, heightDp: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
) {
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            // 0 means fill what is left of the row, which in a wrapping row means the full
            // width and therefore always ends it.
            .then(if (entry.widthDp > 0) Modifier.width(entry.widthDp.dp) else Modifier.fillMaxWidth())
            .height(entry.heightDp.dp)
            .onSizeChanged {
                measuredWidthDp[index] = with(density) { it.width.toDp().value.toInt() }
            }
            .onGloballyPositioned { entryTops[index] = it.positionInWindow().y }
            .padding(vertical = 4.dp)
            .zIndexIfEditing(editing),
    ) {
        when (entry) {
            is MinusOneEntry.Widget -> HostedWidget(
                widgetId = entry.widgetId,
                onPressAndHold = { _, _ -> onEditingChange(index) },
                // Only wired up while arranging. The rest of the time the widget keeps its own
                // touch stream entirely, which is the whole reason for the host wrapper.
                onDragStart = if (editing) { { _ -> onDragStart() } } else null,
                onDragMove = if (editing) onDragMove else null,
                onDragEnd = if (editing) onDragEnd else null,
            )

            is MinusOneEntry.Shortcut -> ShortcutRow(
                entry = entry,
                appsByKey = appsByKey,
                contentColor = contentColor,
                onLaunch = onLaunch,
                onEditingChange = { onEditingChange(index) },
                editing = editing,
                onDragStart = onDragStart,
                onDragMove = onDragMove,
                onDragEnd = onDragEnd,
            )
        }

        if (editing) {
            // The size, so it can be aimed at a number rather than nudged towards one. A drag
            // is continuous and a widget lays out in whole cells.
            val liveWidthDp = if (entry.widthDp > 0) entry.widthDp else measuredWidthDp[index]
            if (liveWidthDp != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.66f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        "$liveWidthDp x ${entry.heightDp} dp",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }

            // Move, in the middle, and visibly so. The whole widget used to be the move area,
            // which meant a drag meant for the widget's own content moved the widget.
            Grip(contentColor, Modifier.align(Alignment.Center), Icons.Filled.DragIndicator)

            SizeHandles(
                contentColor = contentColor,
                onActiveChange = onResizeActiveChange,
                onHeight = { dy -> onResize(index, entry.widthDp, entry.heightDp + dy) },
                // From what is measured on screen, not from the stored 0: adding a negative
                // delta to "full" clamps straight back to 0 and the entry could never narrow.
                onWidth = { dx ->
                    val base = if (entry.widthDp > 0) entry.widthDp else (measuredWidthDp[index] ?: 0)
                    onResize(index, base + dx, entry.heightDp)
                },
                onCorner = { dx, dy ->
                    val base = if (entry.widthDp > 0) entry.widthDp else (measuredWidthDp[index] ?: 0)
                    onResize(index, base + dx, entry.heightDp + dy)
                },
            )

            // Remove, in the corner every editor puts it.
            Grip(
                contentColor = contentColor,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                icon = Icons.Filled.Close,
                label = stringResource(R.string.minus_one_remove),
                onClick = {
                    onEditingChange(null)
                    onRemove(index)
                },
            )
        }
    }
}

/** A launch shortcut: the app's icon and name, tappable, long-pressable, draggable. */
@Composable
private fun ShortcutRow(
    entry: MinusOneEntry.Shortcut,
    appsByKey: Map<String, AppInfo>,
    contentColor: Color,
    onLaunch: (AppInfo) -> Unit,
    onEditingChange: () -> Unit,
    editing: Boolean,
    onDragStart: () -> Unit,
    onDragMove: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val app = appsByKey[entry.appKey]
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
            .pointerInputShortcut(entry.appKey, editing, onEditingChange, onDragStart, onDragMove, onDragEnd)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (app != null) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
            )
        } else {
            // A shortcut whose app has gone is still a row that has to be removable, or the page
            // has something on it with no way to get rid of it.
            Text(
                stringResource(R.string.minus_one_missing_app),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun EmptyStackHint(contentColor: Color, onAddWidget: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.minus_one_empty_title),
            color = contentColor,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.minus_one_empty_body),
            color = contentColor.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.widget_add),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clickable(onClick = onAddWidget),
        )
    }
}

/** A round marker on an entry being arranged: the move grip, or the remove cross. */
@Composable
private fun Grip(
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .size(GRIP_SIZE_DP.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = if (onClick != null) 0.55f else 0.34f))
            .border(1.dp, contentColor.copy(alpha = 0.5f), CircleShape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick).semantics { contentDescription = label.orEmpty() }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(GRIP_ICON_DP.dp),
        )
    }
}

/**
 * The three size handles: bottom edge, right edge, and the corner where they meet.
 *
 * Inside the entry rather than around it, so a handle can never sit on top of its neighbour. An
 * arrow rather than a bar, because a bar is a divider and an arrow says which way the edge moves.
 */
@Composable
private fun SizeHandles(
    contentColor: Color,
    onActiveChange: (Boolean) -> Unit,
    onHeight: (dy: Int) -> Unit,
    onWidth: (dx: Int) -> Unit,
    onCorner: (dx: Int, dy: Int) -> Unit,
) {
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        HandleStrip(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(HANDLE_TOUCH_DP.dp),
            icon = Icons.Filled.SwapVert,
            contentColor = contentColor,
            thickness = 52.dp,
            corner = false,
            onActiveChange = onActiveChange,
            onDrag = { _, dy -> onHeight(dy.roundToInt()) },
        )

        HandleStrip(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(HANDLE_TOUCH_DP.dp),
            icon = Icons.Filled.SwapHoriz,
            contentColor = contentColor,
            thickness = 52.dp,
            corner = false,
            onActiveChange = onActiveChange,
            onDrag = { dx, _ -> onWidth(dx.roundToInt()) },
        )

        HandleStrip(
            modifier = Modifier.align(Alignment.BottomEnd).size(HANDLE_TOUCH_DP.dp),
            icon = Icons.AutoMirrored.Filled.CompareArrows,
            contentColor = contentColor,
            thickness = HANDLE_CORNER_DP.dp,
            corner = true,
            onActiveChange = onActiveChange,
            onDrag = { dx, dy -> onCorner(dx.roundToInt(), dy.roundToInt()) },
        )
    }
}

/** One draggable size handle. */
@Composable
private fun HandleStrip(
    modifier: Modifier,
    icon: ImageVector,
    contentColor: Color,
    thickness: Dp,
    corner: Boolean,
    onActiveChange: (Boolean) -> Unit,
    onDrag: (dxDp: Float, dyDp: Float) -> Unit,
) {
    val density = LocalDensity.current
    Box(
        modifier = modifier.pointerInputHandle(onActiveChange, onDrag, density),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(GRIP_SIZE_DP.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.42f))
                .border(1.dp, contentColor.copy(alpha = 0.55f), CircleShape)
                // The corner arrow lies along the diagonal, so it is drawn level and turned.
                .then(if (corner) Modifier.rotate(45f) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(GRIP_ICON_DP.dp),
            )
        }
    }
}

private fun Modifier.zIndexIfEditing(editing: Boolean): Modifier =
    if (editing) this.zIndex(1f) else this

/**
 * A shortcut's touch handling: tap to launch, long press to arrange, and while arranging a
 * drag moves it.
 *
 * Written out rather than through `detectTapGestures` because that owns the whole stream once it
 * starts, which would take the drag from the modifier that has to see it. Travel is measured on
 * absolute positions rather than `positionChange()` for the same reason the handles are: that
 * reports zero for a consumed change.
 */
private fun Modifier.pointerInputShortcut(
    key: String,
    editing: Boolean,
    onLongPress: () -> Unit,
    onDragStart: () -> Unit,
    onDragMove: (Float) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = if (!editing) {
    this.pointerInput(key) {
        detectTapGestures(
            onTap = { },
            onLongPress = { onLongPress() },
        )
    }
} else {
    this.pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            onDragStart()
            var dragging = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val dy = change.position.y - down.position.y
                if (!dragging && abs(dy) > MOVE_SLOP_PX) dragging = true
                if (dragging) {
                    change.consume()
                    onDragMove(dy)
                }
            }
            if (dragging) onDragEnd()
        }
    }
}

/**
 * A size handle's drag.
 *
 * The down is consumed immediately, not on the first move. A resize is a *horizontal* drag when
 * narrowing from the right edge, and anything around this page is entitled to read that as a
 * swipe away; claiming the press is what stops it taking a gesture that was aimed at a resize.
 */
private fun Modifier.pointerInputHandle(
    onActiveChange: (Boolean) -> Unit,
    onDrag: (dxDp: Float, dyDp: Float) -> Unit,
    density: Density,
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        onActiveChange(true)
        val start = down.position
        while (true) {
            // Read at the Initial pass, which runs before anything else sees the event.
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            change.consume()
            onDrag(
                (change.position.x - start.x).toDp(density),
                (change.position.y - start.y).toDp(density),
            )
        }
        onActiveChange(false)
    }
}

/** Absolute px to dp, so a drag survives someone else having consumed the change. */
private fun Float.toDp(density: Density): Float = with(density) { this@toDp.toDp().value }

private const val FAB_SIZE_DP = 56
private const val GRIP_SIZE_DP = 30
private const val GRIP_ICON_DP = 17
private const val HANDLE_TOUCH_DP = 34
private const val HANDLE_CORNER_DP = 30
private const val ENTRY_GAP_DP = 8
private const val ADD_WIDGET_LABEL = "Add a widget to this page"
private const val MOVE_SLOP_PX = 12f
