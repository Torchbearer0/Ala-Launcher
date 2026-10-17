// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import android.appwidget.AppWidgetManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.abs
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
import dev.victorialauncher.R
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.MINUS_ONE_MAX_HEIGHT_DP
import dev.victorialauncher.data.MINUS_ONE_MAX_WIDTH_DP
import dev.victorialauncher.data.MINUS_ONE_MIN_HEIGHT_DP
import dev.victorialauncher.data.MinusOneEntry
import dev.victorialauncher.data.MinusOneMode
import dev.victorialauncher.data.MinusOnePage
import dev.victorialauncher.ui.common.AppIcon
import dev.victorialauncher.ui.common.TouchAnchoredMenu
import dev.victorialauncher.widget.HostedWidget
import androidx.compose.ui.res.stringResource

/** How much of a drag has to travel before a divider is treated as a resize rather than a scroll. */
private const val RESIZE_SLOP_PX = 14f

/** Gap between two entries that sit side by side, so they are not read as one control. */
private val ENTRY_GAP_DP = 8

/**
 * How much of an edge is grabbable.
 *
 * Wider than the 4dp line that is actually drawn, because a 4dp target on a phone is a
 * mis-tap waiting to happen and the handles are invisible until the drag starts anyway. This is
 * a strip that overlaps the edge rather than sitting outside it, so it cannot cover the next
 * entry or swallow a swipe meant for the one below.
 */
private val HANDLE_TOUCH_DP = 26

private val HANDLE_CORNER_DP = 34

/**
 * The narrowest leftover gap that is still an offer rather than a mistake.
 *
 * Below this the plus would be a sliver to aim at, and a mis-tap that opens the widget picker
 * for nothing is worse than not showing the gap at all.
 */
private const val MIN_INSERT_GAP_DP = 96

/** How far a drag travels before it is moving a widget rather than pressing it. */
private const val MOVE_SLOP_PX = 12f

/** How far a widget travels before it swaps places with its neighbour. */
private const val MOVE_STEP_DP = 40

/** The circle a handle is drawn in, and the arrow inside it. */
private const val GRIP_SIZE_DP = 30
private const val GRIP_ICON_DP = 17

/**
 * Where a drag is taken to have hold, for a shortcut.
 *
 * A hosted widget reports its own grab point, because the layout knows it. A shortcut is drawn
 * by this file, so it cannot know it any better than this without another layout pass, and a
 * card taken hold of anywhere reads the same as one taken hold of in the middle.
 */
private const val MOVE_GRAB_DEFAULT = 0.5f

/**
 * Where a dropped entry can actually go without landing on top of something else.
 *
 * Free placement means a position is absolute, so it can be dropped straight through another
 * entry and leave two widgets drawn over each other - which is not a layout anybody asked for
 * and gives no way out of it, since the only controls are on top of each other.
 *
 * So the dropped position is a *wish*: the nearest position to it that is free. Downwards
 * first, because that is where the finger was heading, and only then upwards - a drop that
 * was refused in one direction and quietly taken in the other reads as the app changing its
 * mind. Candidates are only ever the wish itself and the far side of something already there,
 * so the answer is a place that is genuinely empty rather than a hair away from one.
 *
 * Pure, so the rule can be tested without a screen, a finger or a widget.
 */
fun resolveDropY(
    desiredY: Int,
    heightDp: Int,
    occupied: List<IntRange>,
    pageHeightDp: Int,
    gapDp: Int,
): Int {
    if (pageHeightDp <= heightDp) return 0
    val highest = pageHeightDp - heightDp

    fun free(y: Int): Boolean =
        y in 0..highest && occupied.none { y < it.last + gapDp && it.first < y + heightDp + gapDp }

    // The wish first, then the gaps below, then the gaps above.
    //
    // Downwards is preferred over merely *nearest*, because nearest will happily send a widget
    // the length of the page in the opposite direction to the one the finger was travelling.
    // Someone dragging down onto a neighbour expects it to end up under that neighbour, which
    // is also what "make room" means - not the thing vanishing above where it came from.
    val below = occupied.map { it.last + 1 + gapDp }.filter { it in 0..highest }.sorted()
    val above = occupied.map { it.first - heightDp - gapDp }.filter { it in 0..highest }.sorted()
    return (listOf(desiredY) + below + above).firstOrNull { free(it) }
        ?: desiredY.coerceIn(0, highest)
}

/**
 * Where a held entry lands when the finger is at [rootY].
 *
 * The held entry is lifted out of the count first, so it is never one of the ones it is being
 * carried past - counting itself would mean every drag moved two places instead of one.
 *
 * An entry whose position is not known yet sorts to the end rather than the start, so a drag
 * that begins before the list has finished laying out cannot throw the entry to the top.
 *
 * Pure, and taking the centres as a function rather than a list, so the part of arranging
 * that decides *where* something goes can be tested with no screen, no finger and no widget.
 */
internal fun dragTargetIndex(
    heldIndex: Int,
    centreY: (Int) -> Float?,
    rootY: Float,
    lastIndex: Int,
): Int {
    if (lastIndex < 1) return heldIndex
    val above = (0..lastIndex).count { it != heldIndex && (centreY(it) ?: Float.MAX_VALUE) < rootY }
    return above.coerceIn(0, lastIndex)
}

/** The add-widget control. Big enough to be a target without covering a widget. */
private const val FAB_SIZE_DP = 60

/**
 * The page to the left of the home screen, reached by swiping right through the gap between the
 * widget and the favorites.
 *
 * The home screen can show one widget at a time and swipes sideways between them, which means a
 * second widget is a swipe away rather than a glance away and only one of them can be read at
 * once. This page stacks them instead, so everything on it is visible together.
 */
@Composable
fun MinusOnePage(
    page: MinusOnePage,
    appsByKey: Map<String, AppInfo>,
    contentColor: Color,
    onAddWidget: () -> Unit,
    onResize: (index: Int, widthDp: Int, heightDp: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    /** Reorders the stack: a held entry dragged past a neighbour moves there. */
    onMove: (from: Int, to: Int) -> Unit,
    /**
     * Puts an entry where it was dropped, in dp on the page.
     *
     * The page width and height come with it because a position has to be clamped against
     * something: stored unclamped, a widget dragged past the bottom comes back invisible, with
     * nothing on the page to say where it went.
     */
    onPlace: (index: Int, xDp: Int, yDp: Int, pageWidthDp: Int, pageHeightDp: Int) -> Unit,
    onWidgetSettings: (widgetId: Int) -> Unit,
    onAppInfo: (widgetId: Int) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    /** Reports that a resize handle is held, so the pager around this stops scrolling. */
    onResizeActiveChange: (Boolean) -> Unit = {},
    /** Which entry is being arranged, and who changes it. Held by the route - see HomeRoute. */
    editingIndex: Int? = null,
    onEditingChange: (Int?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (page.mode) {
        MinusOneMode.OFF -> Box(modifier)
        MinusOneMode.DISCOVER -> DiscoverPage(
            contentColor = contentColor,
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )

        MinusOneMode.WIDGETS -> WidgetStackPage(
            entries = page.entries,
            appsByKey = appsByKey,
            contentColor = contentColor,
            onAddWidget = onAddWidget,
            onResize = onResize,
            onRemove = onRemove,
            onMove = onMove,
            onPlace = onPlace,
            onWidgetSettings = onWidgetSettings,
            onAppInfo = onAppInfo,
            onLaunch = onLaunch,
            onOpenSettings = onOpenSettings,
            onResizeActiveChange = onResizeActiveChange,
            editingIndex = editingIndex,
            onEditingChange = onEditingChange,
            modifier = modifier,
        )
    }
}

/**
 * The Discover option, which is the Google app rather than a page of ours.
 *
 * There is normally nothing to see here at all: reaching this page opens Google straight away,
 * and Google is what is on screen afterwards. This is only what is left behind if Google is not
 * installed - there is no feed to show without it, because a feed of our own would mean a
 * WebView loading a Google page, and a WebView needs the INTERNET permission this app has never
 * held.
 */
@Composable
private fun DiscoverPage(
    contentColor: Color,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.minus_one_discover_title),
            color = contentColor,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.minus_one_discover_missing),
            color = contentColor.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.minus_one_change))
        }
    }
}

/**
 * The widget page: entries stacked down the screen, each carrying its own width and height.
 *
 * A plain Column with a wrapping row inside rather than a LazyColumn, because every entry is
 * measured rather than recycled — a hosted widget is a View, and recycling one out from under a
 * provider is the kind of thing the widget hosting code above exists to avoid. The page scrolls
 * instead, and is short enough by construction that this is not a long list.
 *
 * Widths are per entry, so a half-width clock sits beside a half-width calendar rather than
 * each taking a screen of its own. A width of 0 means fill the row, which always ends it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetStackPage(
    entries: List<MinusOneEntry>,
    appsByKey: Map<String, AppInfo>,
    contentColor: Color,
    onAddWidget: () -> Unit,
    onResize: (index: Int, widthDp: Int, heightDp: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    /** Reorders the stack: a held entry dragged past a neighbour moves there. */
    onMove: (from: Int, to: Int) -> Unit,
    /**
     * Puts an entry where it was dropped, in dp on the page.
     *
     * The page width and height come with it because a position has to be clamped against
     * something: stored unclamped, a widget dragged past the bottom comes back invisible, with
     * nothing on the page to say where it went.
     */
    onPlace: (index: Int, xDp: Int, yDp: Int, pageWidthDp: Int, pageHeightDp: Int) -> Unit,
    onWidgetSettings: (widgetId: Int) -> Unit,
    onAppInfo: (widgetId: Int) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    onResizeActiveChange: (Boolean) -> Unit,
    /** Which entry is being arranged, and who changes it. Held by the route - see HomeRoute. */
    editingIndex: Int?,
    onEditingChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuIndex by remember { mutableStateOf<Int?>(null) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }

    // Where the edit buttons of the entry being arranged sit on screen, so the menu can hang
    // off them. Recorded on layout rather than guessed from the entry, because the entry's
    // position moves with the scroll and with every other entry in the wrapping row.
    var menuAnchor by remember { mutableStateOf(Offset.Zero) }

    // Which widgets actually have an options screen of their own. A provider that declares none
    // leaves AppWidgetProviderInfo.configure null, and asking for one hands the user to a system
    // activity that has nothing to show and finishes immediately - which reads as the launcher
    // throwing them out to the home screen. Rather than guard the hand-off, the button is not
    // drawn: a widget with nothing to configure has nothing this corner can do.
    val pageContext = LocalContext.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(pageContext) }
    val widgetsWithOptions = remember(entries) {
        entries.mapNotNull { (it as? MinusOneEntry.Widget)?.widgetId }
            .filter { id -> id > 0 && appWidgetManager.getAppWidgetInfo(id)?.configure != null }
            .toSet()
    }

    val density = LocalDensity.current
    // What each entry actually measures, in dp. A full-width entry stores 0 to mean "fill", so
    // its stored width is not a number a drag can be added to — adding a negative delta to it
    // clamps straight back to 0 and the entry could never be narrowed at all. The corner drag
    // measures from what is on screen and writes a real width, which is what turns "fill" into
    // a number the first time anyone drags it.
    val measuredWidthDp = remember { mutableStateMapOf<Int, Int>() }

    // Which entry is being dragged right now. The one being arranged is [editingIndex], which
    // belongs to the route.
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var rowWidthDp by remember { mutableIntStateOf(0) }

    // How far a widget has to travel before it changes place with its neighbour. Small enough
    // that a deliberate drag needs only a nudge, and large enough that the touch slop that
    // starts every gesture cannot do it on its own.
    val moveStepPx = with(density) { MOVE_STEP_DP.dp.toPx() }

    // The index of the entry under the finger, tracked rather than captured from the entry's
    // own closure. A swap is itself the move: the moment the first one lands the entry that was
    // at `index` is somewhere else, so the captured index names a different entry and a second
    // swap would shuffle the wrong one.
    var heldIndex by remember { mutableStateOf<Int?>(null) }

    // Where each entry actually is on screen, in window coordinates. Recorded on layout rather
    // than worked out from the list, because the list does not know how tall its entries are:
    // a resized widget is a different height, and two entries share a row when one is narrow.
    val entryTops = remember { mutableStateMapOf<Int, Float>() }

    /** The middle of an entry on screen, or null before it has been laid out. */
    fun centreY(index: Int): Float? {
        val top = entryTops[index] ?: return null
        val entry = entries.getOrNull(index) ?: return null
        return top + with(density) { entry.heightDp.dp.toPx() } / 2f
    }

    // The entry being carried, and where the finger is while carrying it. Kept apart from the
    // entry's own box on purpose: a drag reported as a position inside the widget can never go
    // above that widget's top edge, so a lower entry could be dragged down past its neighbour
    // but never up - which is exactly how it behaved. The entry is lifted out of the flow and
    // drawn under the finger, so the finger can go anywhere on the page in either direction.
    var liftedIndex by remember { mutableStateOf<Int?>(null) }
    var fingerRootY by remember { mutableFloatStateOf(0f) }

    /** Where the finger grabbed it, as a fraction down its own height. Keeps it under the finger. */
    var grabFraction by remember { mutableFloatStateOf(0.5f) }

    /** Where the page itself sits in the window, so a drop can be turned into a position on it. */
    var pageTopInWindow by remember { mutableFloatStateOf(0f) }

    /** The page's own size in dp, which is what a position is clamped against. */
    var pageSizeDp by remember { mutableStateOf(0 to 0) }

    /** How tall an entry is in pixels, or zero if it is gone. */
    fun entryHeightPx(index: Int): Float {
        val entry = entries.getOrNull(index) ?: return 0f
        return with(density) { entry.heightDp.dp.toPx() }
    }

    /**
     * The bands on the page already taken by everything except the carried entry, in dp.
     *
     * Read from where each entry was actually laid out rather than worked out from the list,
     * because the list does not know how tall its entries are and does not know where the
     * placed ones ended up. Placed entries get their own position; the rest get theirs from
     * the flow they are packed into.
     */
    fun occupiedBands(exceptIndex: Int): List<IntRange> {
        val out = mutableListOf<IntRange>()
        entries.forEachIndexed { index, entry ->
            if (index == exceptIndex) return@forEachIndexed
            val top = entryTops[index] ?: return@forEachIndexed
            val topDp = with(density) { (top - pageTopInWindow).toDp().value.toInt() }
            out += topDp until (topDp + entry.heightDp)
        }
        return out
    }

    /** The carried entry is put down where the finger left it, if that is somewhere free. */
    fun releaseMove() {
        val from = liftedIndex
        val entry = from?.let { entries.getOrNull(it) }
        if (from != null && entry != null) {
            val wantedDp = with(density) {
                (fingerRootY - pageTopInWindow - entryHeightPx(from) * grabFraction).toDp().value.toInt()
            }
            val landingDp = resolveDropY(
                desiredY = wantedDp,
                heightDp = entry.heightDp,
                occupied = occupiedBands(from),
                pageHeightDp = pageSizeDp.second,
                gapDp = ENTRY_GAP_DP,
            )
            onPlace(from, entry.xDp, landingDp, pageSizeDp.first, pageSizeDp.second)
        }
        liftedIndex = null
        draggingIndex = null
        heldIndex = null
    }

    /** The held entry follows the finger, and everything else gets out of its way. */
    fun moveTo(rootY: Float) {
        val from = liftedIndex ?: return
        fingerRootY = rootY
        if (entries.size < 2) return
        val to = dragTargetIndex(from, ::centreY, rootY, entries.lastIndex)
        if (to == from) return
        onMove(from, to)
        heldIndex = to
        draggingIndex = to
        liftedIndex = to
        // Arranging follows the entry rather than the slot. Left on the old index, the handles
        // and the cross would stay behind on whichever entry had been sitting there.
        onEditingChange(to)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Where the page is in the window and how big it is: a widget dropped at a point on
            // the screen becomes a position on the page, and that needs both.
            .onGloballyPositioned {
                pageTopInWindow = it.positionInWindow().y
                pageSizeDp = with(density) {
                    it.size.width.toDp().value.toInt() to it.size.height.toDp().value.toInt()
                }
            },
    ) {
        // Its own surface, so the page is a place rather than widgets floating over the home
        // screen it was reached from. The gradient rather than a flat wash so the top is a little
        // heavier than the bottom: content sits under the top, and a flat scrim leaves the middle
        // of the stack floating.
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
            // After the padding, not before it. Attached any earlier this reports the width of
            // the whole scroll container rather than the width an entry actually gets to use,
            // and a gap worked out from that is wider than the row it has to fit into - which is
            // how "insert beside this one" turned into "insert below this one".
            .padding(horizontal = 16.dp)
            .onSizeChanged { rowWidthDp = with(density) { it.width.toDp().value.toInt() } },
    ) {
        Spacer(Modifier.height(20.dp))

        if (entries.isEmpty()) {
            EmptyStackHint(contentColor, onAddWidget, onOpenSettings)
            Spacer(Modifier.height(20.dp))
        }

        // Wrapping rather than one row per entry, so a narrow entry sits beside the next one
        // that fits instead of below it. Entries narrower than the page were the whole reason
        // for this page: two clocks side by side is the thing a single-widget slot cannot do.
        // Rows are as tall as their tallest entry, so a short one beside a tall one still lines
        // up at the top rather than floating in the middle.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ENTRY_GAP_DP.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
        entries.forEachIndexed { index, entry ->
            val editing = editingIndex == index
            // An entry the user has put somewhere is drawn by the overlay below, not packed
            // into the flow. Packing it as well would put it in two places at once; leaving it
            // in the flow would take its slot with it and the space left behind would close up,
            // which is the one thing free placement is for.
            val carried = liftedIndex == index
            if (entry.isPlaced && !carried) return@forEachIndexed
            Box(
                modifier = Modifier
                    // A width of 0 means fill what is left, which in a wrapping row means the
                    // full width and therefore always ends the row.
                    .then(if (entry.widthDp > 0) Modifier.width(entry.widthDp.dp) else Modifier.fillMaxWidth())
                    .height(entry.heightDp.dp)
                    .onSizeChanged { measuredWidthDp[index] = with(density) { it.width.toDp().value.toInt() } }
                    // Where this entry sits in the window, so a held entry can be arranged
                    // against where the finger is rather than against how far it has moved.
                    .onGloballyPositioned { entryTops[index] = it.positionInWindow().y }
                    // No border around a widget at any time. A frame says "this is a box in a
                    // layout"; the widgets are content, and the only thing that should outline
                    // one is the user holding it in edit mode.
                    .padding(vertical = 4.dp)
                    .graphicsLayer {
                        if (carried) {
                            // Carried. Drawn where the finger is rather than where the flow put
                            // it, so it can be taken anywhere on the page. The grab point travels
                            // with it, so a widget taken hold of near its top edge stays there
                            // instead of jumping so its middle is under the finger.
                            val slotTop = entryTops[index] ?: 0f
                            val h = with(density) { entry.heightDp.dp.toPx() }
                            translationY = fingerRootY - slotTop - h * grabFraction
                            scaleX = 1.04f
                            scaleY = 1.04f
                            shadowElevation = 18.dp.toPx()
                        } else if (editing) {
                            scaleX = 1.02f
                            scaleY = 1.02f
                        }
                    }
                    .zIndex(if (carried) 3f else if (editing) 1f else 0f),
            ) {
                EntryBody(
                    entry = entry,
                    appsByKey = appsByKey,
                    contentColor = contentColor,
                    onLaunch = onLaunch,
                    // A long press is the way into editing, which is why there is no dp dialog
                    // and no handle strip sitting on the page all the time: the gesture that
                    // arranges a widget is the same gesture that already edited a row. It also
                    // no longer opens a menu - the menu used to be the only way to resize or
                    // remove, and both of those are now buttons on the entry, so a menu over
                    // the top of it would only hide the thing it was opened for.
                    onPressAndHold = { _, _ -> onEditingChange(index) },
                    // Dragging the middle moves it. Only while editing, because otherwise a drag
                    // on a widget belongs to the widget.
                    onDragMove = if (editing) { { rootY -> moveTo(rootY) } } else null,
                    onDragEnd = if (editing) { { releaseMove() } } else null,
                    onDragStart = if (editing) {
                        { fraction ->
                            heldIndex = index
                            draggingIndex = index
                            liftedIndex = index
                            grabFraction = fraction
                        }
                    } else null,
                )

                if (editing) {
                    // The size, while it is being changed. A drag is continuous and a widget
                    // is laid out in whole cells, so without a number to aim at there is no
                    // saying "make it exactly this" - the best available is to keep nudging
                    // and hope. Full width is stored as 0, so it is spelled out here.
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
                                "$liveWidthDp × ${entry.heightDp} dp",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                            )
                        }
                    }

                    ResizeHandles(
                        contentColor = contentColor,
                        onDragWidth = { deltaDp ->
                            val base = if (entry.widthDp > 0) entry.widthDp else (measuredWidthDp[index] ?: 0)
                            onResize(index, base + deltaDp, entry.heightDp)
                        },
                        onDragHeight = { deltaDp -> onResize(index, entry.widthDp, entry.heightDp + deltaDp) },
                        onDragCorner = { dx, dy ->
                            val base = if (entry.widthDp > 0) entry.widthDp else (measuredWidthDp[index] ?: 0)
                            onResize(index, base + dx, entry.heightDp + dy)
                        },
                        showWidth = entry.widthDp > 0,
                        onActiveChange = onResizeActiveChange,
                    )

                    // Remove and more, in the corner where every editor puts them. Both are on
                    // the entry rather than behind a long press, because the long press is what
                    // brings them into being and a menu cannot be inside the thing that opened
                    // it. The description is on the button as well as the glyph: a clickable
                    // parent merges its children, and that merge is what left the glyph's own
                    // description off the accessibility tree.
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .onGloballyPositioned {
                                // Where the menu should hang from, once one of these is tapped.
                                menuAnchor = it.positionInRoot() + Offset(it.size.width.toFloat(), 0f)
                            },
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (entry is MinusOneEntry.Widget && entry.widgetId in widgetsWithOptions) {
                            EditButton(
                                label = stringResource(R.string.minus_one_more),
                                onClick = {
                                    menuIndex = index
                                    menuOffset = with(density) {
                                        DpOffset(menuAnchor.x.toDp(), menuAnchor.y.toDp())
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        EditButton(
                            label = stringResource(R.string.minus_one_remove),
                            onClick = {
                                onEditingChange(null)
                                menuIndex = null
                                onRemove(index)
                            },
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            // The space a half-width widget leaves beside it is not dead - it is an offer.
            // A sibling of the entry rather than a child of it: given the entry's own width it
            // lands on top of the widget instead of in the gap beside it, which is the only
            // place it is any use. As a sibling, FlowRow gives it whatever is left of the same
            // row and wraps it down to the next one when nothing is left.
            // Only while something is being edited, so the page is never cluttered with holes,
            // and only where the gap is wide enough to be worth aiming at.
            if (editing && entry.widthDp > 0) {
                val gapDp = rowWidthDp - entry.widthDp - ENTRY_GAP_DP
                if (gapDp >= MIN_INSERT_GAP_DP) {
                    Box(
                        modifier = Modifier
                            .width(gapDp.dp)
                            .height(entry.heightDp.dp)
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable {
                                onEditingChange(null)
                                onAddWidget()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.minus_one_insert_here),
                            tint = contentColor.copy(alpha = 0.55f),
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }
        }

        if (entries.isNotEmpty()) {
            Spacer(Modifier.height(72.dp))
        }
        }

        // Everything the user has placed by hand, drawn at the coordinates it was dropped at.
        // Outside the scrolling column on purpose: a position is a place on the page, not a
        // place in a list, and putting this inside the flow would make it scroll away and would
        // mean the empty space it leaves could never be measured against anything.
        if (entries.any { it.isPlaced }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                entries.forEachIndexed { index, entry ->
                    if (!entry.isPlaced) return@forEachIndexed
                    if (liftedIndex == index) return@forEachIndexed
                    val editing = editingIndex == index
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(entry.xDp.dp.roundToPx(), entry.yDp.dp.roundToPx()) }
                            .then(
                                if (entry.widthDp > 0) Modifier.width(entry.widthDp.dp)
                                else Modifier.fillMaxWidth(),
                            )
                            .height(entry.heightDp.dp)
                            .padding(vertical = 4.dp)
                            .onGloballyPositioned { entryTops[index] = it.positionInWindow().y }
                            .graphicsLayer { if (editing) { scaleX = 1.02f; scaleY = 1.02f } }
                            .zIndex(if (editing) 1f else 0f),
                    ) {
                        EntryBody(
                            entry = entry,
                            appsByKey = appsByKey,
                            contentColor = contentColor,
                            onLaunch = onLaunch,
                            onPressAndHold = { _, _ -> onEditingChange(index) },
                            onDragMove = if (editing) { { rootY -> moveTo(rootY) } } else null,
                            onDragEnd = if (editing) { { releaseMove() } } else null,
                            onDragStart = if (editing) {
                                { fraction ->
                                    heldIndex = index
                                    draggingIndex = index
                                    liftedIndex = index
                                    grabFraction = fraction
                                }
                            } else null,
                        )
                    }
                }
            }
        }

        // The one control this page has, as a floating circle in the corner rather than a full
        // width button under the list. It sits out of the way of the widgets, stays in the same
        // place whatever is on the page, and never pushes the stack around as things are added.
        // Present even when the page is empty, where it is the obvious next thing to do.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 20.dp)
                .size(FAB_SIZE_DP.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onAddWidget),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.widget_add),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp),
            )
        }
    }

    // Only what the buttons on the entry cannot do: resizing is a handle and removal is the
    // cross beside it, so both are gone from here rather than offered twice.
    menuIndex?.let { index ->
        val entry = entries.getOrNull(index) ?: return@let
        if (entry is MinusOneEntry.Widget) {
            TouchAnchoredMenu(
                expanded = true,
                offset = menuOffset,
                onDismissRequest = { menuIndex = null },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.widget_change_settings)) },
                    leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    onClick = {
                        menuIndex = null
                        onWidgetSettings(entry.widgetId)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_app_info)) },
                    leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    onClick = {
                        menuIndex = null
                        onAppInfo(entry.widgetId)
                    },
                )
            }
        }
    }
}

/**
 * A round button that appears on an entry only while the page is arranging it.
 *
 * The description sits on the button rather than on the glyph inside it. A clickable parent
 * merges the semantics of its children into its own, and that merge is why the glyph's own
 * description stopped reaching the accessibility tree - which also means it stopped reaching
 * anything reading the screen from outside.
 */
@Composable
private fun EditButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/** What the entry itself draws: a hosted widget, or a shortcut that launches something. */
@Composable
private fun EntryBody(
    entry: MinusOneEntry,
    appsByKey: Map<String, AppInfo>,
    contentColor: Color,
    onLaunch: (AppInfo) -> Unit,
    onPressAndHold: (x: Float, y: Float) -> Unit,
    onDragStart: ((grabFraction: Float) -> Unit)? = null,
    onDragMove: ((dy: Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
) {
    when (entry) {
        is MinusOneEntry.Widget -> HostedWidget(
            widgetId = entry.widgetId,
            onPressAndHold = onPressAndHold,
            // A widget is a hosted Android view, so it needs the same wording as the row below:
            // the whole widget stands in for its own middle.
            onDragStart = onDragStart,
            onDragMove = onDragMove,
            onDragEnd = onDragEnd,
        )

        is MinusOneEntry.Shortcut -> {
            // An app that has been uninstalled since it was added. Shown as a dead row rather
            // than dropped: the height is the user's, and silently removing the entry would move
            // everything below it.
            val app = appsByKey[entry.appKey]
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
                    .pointerInput(entry.appKey) {
                        if (onDragMove == null) {
                            detectTapGestures(
                                onTap = { app?.let(onLaunch) },
                                onLongPress = { offset -> onPressAndHold(offset.x, offset.y) },
                            )
                        } else {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var dragged = false
                                onDragStart?.invoke(MOVE_GRAB_DEFAULT)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    val dy = change.position.y - down.position.y
                                    if (abs(dy) > MOVE_SLOP_PX) dragged = true
                                    if (dragged) {
                                        change.consume()
                                        onDragMove(dy)
                                    }
                                }
                                if (dragged) onDragEnd?.invoke()
                            }
                        }
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (app != null) {
                    AppIcon(app = app, sizeDp = 40)
                    Spacer(Modifier.width(14.dp))
                    Text(app.label, color = contentColor, style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = contentColor.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(R.string.minus_one_missing_app),
                        color = contentColor.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/**
 * The three grab handles an entry carries: its bottom edge, its right edge, and the corner
 * where they meet.
 *
 * Inside the entry rather than around it, so a handle can never sit on top of its neighbour.
 * Shown only while a drag is running, because a permanently drawn frame around every entry
 * turns the page into an editing surface rather than the content it is meant to be showing.
 *
 * Each is measured in absolute positions for the same reason the home screen's swipe is: the
 * down is already consumed by whatever shares this screen, and positionChange() reports zero
 * for a consumed change, so a drag measured that way is always exactly 0dp.
 */
@Composable
private fun ResizeHandles(
    contentColor: Color,
    onDragWidth: (deltaDp: Int) -> Unit,
    onDragHeight: (deltaDp: Int) -> Unit,
    onDragCorner: (dxDp: Int, dyDp: Int) -> Unit,
    showWidth: Boolean,
    onActiveChange: (Boolean) -> Unit,
) {
    var active by remember { mutableStateOf<Handle?>(null) }
    val density = LocalDensity.current

    Box(Modifier.fillMaxSize()) {
        // Bottom edge, across the middle of the entry's width.
        HandleStrip(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(HANDLE_TOUCH_DP.dp),
            active = active == Handle.HEIGHT,
            contentColor = contentColor,
            thickness = 52.dp,
            vertical = true,
            onDrag = { dx, dy -> onDragHeight(dy.roundToInt()) },
            onActiveChange = onActiveChange,
        ) { active = Handle.HEIGHT }

        if (showWidth) {
            // Right edge, up the middle of the entry's height.
            HandleStrip(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(HANDLE_TOUCH_DP.dp),
                active = active == Handle.WIDTH,
                contentColor = contentColor,
                thickness = 52.dp,
                vertical = false,
                onDrag = { dx, _ -> onDragWidth(dx.roundToInt()) },
                onActiveChange = onActiveChange,
            ) { active = Handle.WIDTH }
        }

        // The corner, which does both at once - the gesture people expect when they grab a
        // box by its corner and pull it diagonally.
        HandleStrip(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(HANDLE_TOUCH_DP.dp),
            active = active == Handle.CORNER,
            contentColor = contentColor,
            thickness = HANDLE_CORNER_DP.dp,
            vertical = true,
            corner = true,
            onDrag = { dx, dy -> onDragCorner(dx.roundToInt(), dy.roundToInt()) },
            onActiveChange = onActiveChange,
        ) { active = Handle.CORNER }
    }
}

private enum class Handle { WIDTH, HEIGHT, CORNER }

@Composable
private fun HandleStrip(
    modifier: Modifier,
    active: Boolean,
    contentColor: Color,
    thickness: Dp,
    vertical: Boolean,
    /** Drawn as a corner bracket rather than a bar, the way a photo editor marks its corner. */
    corner: Boolean = false,
    onDrag: (dxDp: Float, dyDp: Float) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    val density = LocalDensity.current
    Box(modifier = modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            onStart()
            val start = down.position
            // Consumed on the down rather than on the first move. Narrowing an entry by
            // dragging its right edge inwards is a *leftward* drag, which anything around this
            // page is entitled to read as "go back". Claiming the down is what stops it taking
            // a gesture that was aimed at a resize.
            down.consume()
            onActiveChange(true)
            while (true) {
                // Consumed at the Initial pass, which runs before anything else sees the event.
                // In the Main pass the decision has already been made by whoever watches for
                // the slop-crossing event to still be unconsumed.
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                change.consume()
                val dx = (change.position.x - start.x).toDp(density)
                val dy = (change.position.y - start.y).toDp(density)
                onDrag(dx, dy)
            }
            onActiveChange(false)
            onStart()
        }
    }, contentAlignment = Alignment.Center) {
        // Visible while the entry is being arranged, not only while a finger is already on it.
        // At zero until touched there was nothing on screen saying the edge could be dragged at
        // all, so the handles were a thing that had to be guessed at.
        val alpha by animateFloatAsState(
            targetValue = if (active) 1f else 0.62f,
            label = "handleAlpha",
        )
        // An arrow rather than a bar, because a bar is a divider and an arrow says which way
        // the edge moves. Each one is drawn in a circle so it reads as a target rather than as
        // something belonging to the widget underneath, and the arrow for the axis it resizes
        // is the one pointing along it.
        val arrow: ImageVector = when {
            corner -> Icons.AutoMirrored.Filled.CompareArrows
            vertical -> Icons.Filled.SwapVert
            else -> Icons.Filled.SwapHoriz
        }
        Box(
            modifier = Modifier
                .size(GRIP_SIZE_DP.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = if (active) 0.55f else 0.34f))
                .border(1.dp, contentColor.copy(alpha = 0.5f), CircleShape)
                // The corner arrow lies along the diagonal, so it is drawn horizontal and turned.
                .then(if (corner) Modifier.rotate(45f) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                arrow,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(GRIP_ICON_DP.dp)
                    .graphicsLayer { this.alpha = alpha },
            )
        }
    }
}

/** Absolute px to dp, so a drag survives someone else having consumed the change. */
private fun Float.toDp(density: Density): Float = with(density) { this@toDp.toDp().value }

@Composable
private fun EmptyStackHint(contentColor: Color, onAddWidget: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp)
            .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(26.dp))
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
            color = contentColor.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAddWidget) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.widget_add))
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.minus_one_change))
        }
    }
}

/**
 * Hand the feed off to whatever search app is installed.
 *
 * An explicit component first, because a generic web search would answer with a search results
 * page rather than the feed the card is offering.
 */
private fun openDiscover(context: android.content.Context) {
    val intents = listOf(
        Intent(Intent.ACTION_WEB_SEARCH).addCategory(Intent.CATEGORY_DEFAULT),
        Intent("com.google.android.gsf.search.action.GOOGLE_SEARCH"),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
            // Nothing handles it; try the next.
        }
    }
}