// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
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
    onWidgetSettings: (widgetId: Int) -> Unit,
    onAppInfo: (widgetId: Int) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    /** Reports that a resize handle is held, so the pager around this stops scrolling. */
    onResizeActiveChange: (Boolean) -> Unit = {},
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
            onWidgetSettings = onWidgetSettings,
            onAppInfo = onAppInfo,
            onLaunch = onLaunch,
            onOpenSettings = onOpenSettings,
            onResizeActiveChange = onResizeActiveChange,
            modifier = modifier,
        )
    }
}

/**
 * The Discover option: opening this page opens the Google app, which is where the feed is.
 *
 * Not a feed of our own. Drawing one here would mean a WebView loading a Google page, and a
 * WebView needs the INTERNET permission — which this app has never held, on purpose. Handing off
 * to the app that already has the feed costs no permission at all, shows exactly the same
 * content, and leaves the fetching to Google rather than to a launcher that has no business
 * doing it.
 *
 * Launches as the page is opened rather than waiting for a tap, because the page's only job is
 * to be the way there. If there is no Google app to hand off to, it says so instead of opening
 * and closing again.
 */
@Composable
private fun DiscoverPage(
    contentColor: Color,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var handedOff by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        handedOff = openGoogle(context)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (handedOff) {
            // The Google app is already in front; this is only what is left behind if it is
            // closed again straight away.
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.minus_one_discover_title),
                    color = contentColor,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.minus_one_discover_body),
                    color = contentColor.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { handedOff = openGoogle(context) }) {
                    Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.minus_one_discover_open))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.minus_one_change))
                }
            }
        } else {
            // No Google app to hand off to. Nothing here can fetch the feed instead, because
            // having no Google app is exactly the case where there is nothing to show it in.
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
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
    }
}

/** The Google app, by package first and by its search action second. False when neither exists. */
private fun openGoogle(context: android.content.Context): Boolean {
    val pm = context.packageManager
    val byPackage = runCatching { pm.getLaunchIntentForPackage(GOOGLE_SEARCH_PACKAGE) }.getOrNull()
    val byAction = Intent(GOOGLE_SEARCH_ACTION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val intent = byPackage ?: byAction.takeIf { it.resolveActivity(pm) != null }
    if (intent == null) return false
    return try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }
}

private const val GOOGLE_SEARCH_PACKAGE = "com.google.android.googlequicksearchbox"
private const val GOOGLE_SEARCH_ACTION = "com.google.android.gsf.search.action.GOOGLE_SEARCH"

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
    onWidgetSettings: (widgetId: Int) -> Unit,
    onAppInfo: (widgetId: Int) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    onResizeActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuIndex by remember { mutableStateOf<Int?>(null) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var resizingIndex by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current
    // What each entry actually measures, in dp. A full-width entry stores 0 to mean "fill", so
    // its stored width is not a number a drag can be added to — adding a negative delta to it
    // clamps straight back to 0 and the entry could never be narrowed at all. The corner drag
    // measures from what is on screen and writes a real width, which is what turns "fill" into
    // a number the first time anyone drags it.
    val measuredWidthDp = remember { mutableStateMapOf<Int, Int>() }

    Box(modifier = modifier.fillMaxSize()) {
        // A scrim behind the stack rather than a blur, because a launcher cannot blur the
        // system wallpaper - the wallpaper is the window's background, not a view this app owns,
        // so there is nothing here to hand a RenderEffect. What a scrim buys is the same thing
        // the blur was after: widgets that are hard to read on a busy wallpaper stop being hard
        // to read. Kept light, so a plain wallpaper still shows through.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
        Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
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
            Box(
                modifier = Modifier
                    // A width of 0 means fill what is left, which in a wrapping row means the
                    // full width and therefore always ends the row.
                    .then(if (entry.widthDp > 0) Modifier.width(entry.widthDp.dp) else Modifier.fillMaxWidth())
                    .height(entry.heightDp.dp)
                    .onSizeChanged { measuredWidthDp[index] = with(density) { it.width.toDp().value.toInt() } }
                    .padding(vertical = 4.dp),
            ) {
                EntryBody(
                    entry = entry,
                    appsByKey = appsByKey,
                    contentColor = contentColor,
                    onLaunch = onLaunch,
                    onPressAndHold = { x, y ->
                        menuIndex = index
                        menuOffset = with(density) { DpOffset(x.toDp(), y.toDp()) }
                    },
                )

                // Both handles live inside the entry they resize. Outside it they would sit on
                // whatever is next door - a full-width entry's bottom edge handle would swallow
                // a swipe meant for the entry below it, and the bottom-most entry would need an
                // invisible strip under it that nothing else knows is there.
                ResizeHandles(
                    contentColor = contentColor,
                    onDragWidth = { deltaDp ->
                        val base = if (entry.widthDp > 0) entry.widthDp else (measuredWidthDp[index] ?: 0)
                        onResize(index, base + deltaDp, entry.heightDp)
                    },
                    onDragHeight = { deltaDp -> onResize(index, entry.widthDp, entry.heightDp + deltaDp) },
                    onDragCorner = { dx, dy ->
                        // From the measured width when the entry is filling the row, so the
                        // first sideways drag is what gives it a width to have.
                        val base = if (entry.widthDp > 0) entry.widthDp else (measuredWidthDp[index] ?: 0)
                        onResize(index, base + dx, entry.heightDp + dy)
                    },
                    // Only offered where they mean something: a full-width entry has no edge
                    // to its right to grab.
                    showWidth = entry.widthDp > 0,
                    onActiveChange = onResizeActiveChange,
                )
            }
        }
        }

        if (entries.isNotEmpty()) {
            Spacer(Modifier.height(72.dp))
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

    menuIndex?.let { index ->
        val entry = entries.getOrNull(index) ?: return@let
        TouchAnchoredMenu(
            expanded = true,
            offset = menuOffset,
            onDismissRequest = { menuIndex = null },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.minus_one_resize)) },
                leadingIcon = { Icon(Icons.Filled.Height, contentDescription = null) },
                onClick = {
                    menuIndex = null
                    resizingIndex = index
                },
            )
            if (entry is MinusOneEntry.Widget) {
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
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.minus_one_remove)) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                onClick = {
                    menuIndex = null
                    onRemove(index)
                },
            )
        }
    }

    resizingIndex?.let { index ->
        val entry = entries.getOrNull(index) ?: return@let
        ResizeDialog(
            widthDp = entry.widthDp,
            heightDp = entry.heightDp,
            onDismiss = { resizingIndex = null },
            onChange = { w, h -> onResize(index, w, h) },
        )
    }
}

/** What the entry itself draws: a hosted widget, or a shortcut that launches something. */
@Composable
private fun EntryBody(
    entry: MinusOneEntry,
    appsByKey: Map<String, AppInfo>,
    contentColor: Color,
    onLaunch: (AppInfo) -> Unit,
    onPressAndHold: (x: Float, y: Float) -> Unit,
) {
    when (entry) {
        is MinusOneEntry.Widget -> HostedWidget(widgetId = entry.widgetId, onPressAndHold = onPressAndHold)

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
                        detectTapGestures(
                            onTap = { app?.let(onLaunch) },
                            onLongPress = { offset -> onPressAndHold(offset.x, offset.y) },
                        )
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
            // Consumed here rather than on the first move, and this is the whole reason the
            // handle works at all: the page lives in a pager, and narrowing an entry by
            // dragging its right edge inwards is a *leftward* drag — exactly what the pager
            // reads as "go back to home". Claiming the down is what stops the pager taking a
            // gesture that was aimed at a resize.
            down.consume()
            onActiveChange(true)
            while (true) {
                // Consumed at the Initial pass, which runs before anything else sees the event.
                // Consuming in the Main pass is too late to be decisive here: the pager around
                // this page decides whether a drag is its own from whether the slop-crossing
                // event is still unconsumed, and by the time an inner handler has consumed
                // anything in Main that decision has effectively been made. At Initial it has
                // not.
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
        val alpha by animateFloatAsState(
            targetValue = if (active) 1f else 0f,
            label = "handleAlpha",
        )
        Box(
            modifier = Modifier
                .then(if (vertical) Modifier.width(thickness) else Modifier.height(thickness))
                .then(if (vertical) Modifier.height(4.dp) else Modifier.width(4.dp))
                .graphicsLayer { this.alpha = alpha }
                .background(contentColor.copy(alpha = 0.6f), CircleShape),
        )
    }
}

/** Absolute px to dp, so a drag survives someone else having consumed the change. */
private fun Float.toDp(density: Density): Float = with(density) { this@toDp.toDp().value }

/** The precise way to resize, for anyone who would rather not drag and watch. */
@Composable
private fun ResizeDialog(
    widthDp: Int,
    heightDp: Int,
    onDismiss: () -> Unit,
    onChange: (Int, Int) -> Unit,
) {
    var w by remember { mutableIntStateOf(widthDp) }
    var h by remember { mutableIntStateOf(heightDp) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.minus_one_resize)) },
        text = {
            Column {
                Text(stringResource(R.string.minus_one_resize_width, widthLabel(w)), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = w.toFloat(),
                    onValueChange = { w = it.toInt() },
                    // 0 stands for full width, so the floor is 0 and the ceiling is whatever a
                    // full page is. Dragging to 0 is how an entry is put back to filling.
                    valueRange = 0f..MINUS_ONE_MAX_WIDTH_DP.toFloat(),
                    onValueChangeFinished = { onChange(w, h) },
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.minus_one_resize_height, h), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = h.toFloat(),
                    onValueChange = { h = it.toInt() },
                    valueRange = MINUS_ONE_MIN_HEIGHT_DP.toFloat()..MINUS_ONE_MAX_HEIGHT_DP.toFloat(),
                    onValueChangeFinished = { onChange(w, h) },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

/** Full width is stored as 0, so it has to be spelled out rather than shown as "0dp". */
private fun widthLabel(widthDp: Int): String =
    if (widthDp <= 0) "full" else "${widthDp}dp"

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