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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.victorialauncher.R
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.MINUS_ONE_MAX_HEIGHT_DP
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
    onResize: (index: Int, heightDp: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    onWidgetSettings: (widgetId: Int) -> Unit,
    onAppInfo: (widgetId: Int) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
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
            modifier = modifier,
        )
    }
}

/**
 * The Discover option, which opens the search app rather than showing a feed.
 *
 * A real feed needs a WebView loading discover.google.com, and a WebView needs the INTERNET
 * permission. This launcher does not hold it, on purpose — it has no network access at all and
 * nothing about that is worth trading for a news feed. So the page is a card that hands off to
 * whichever search app is installed, which is what a launcher can honestly offer.
 */
@Composable
private fun DiscoverPage(
    contentColor: Color,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { openDiscover(context) },
            color = Color.Black.copy(alpha = 0.32f),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Filled.Apps,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(14.dp))
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
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.minus_one_change))
        }
    }
}

/**
 * The widget page: a vertical stack where each entry carries its own height.
 *
 * A Column rather than a LazyColumn because every entry is measured rather than recycled — a
 * hosted widget is a View, and recycling one out from under a provider is the kind of thing the
 * widget hosting code above exists to avoid. The stack scrolls instead, and the page is short
 * enough by construction that this is not a long list.
 */
@Composable
private fun WidgetStackPage(
    entries: List<MinusOneEntry>,
    appsByKey: Map<String, AppInfo>,
    contentColor: Color,
    onAddWidget: () -> Unit,
    onResize: (index: Int, heightDp: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    onWidgetSettings: (widgetId: Int) -> Unit,
    onAppInfo: (widgetId: Int) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuIndex by remember { mutableStateOf<Int?>(null) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var resizingIndex by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(20.dp))

        if (entries.isEmpty()) {
            EmptyStackHint(contentColor, onAddWidget, onOpenSettings)
            Spacer(Modifier.height(20.dp))
        }

        entries.forEachIndexed { index, entry ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(entry.heightDp.dp)
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
            }

            // The divider below an entry resizes that entry, which is the one whose bottom edge
            // the finger is actually on. Placed under the last entry too, so the bottom-most
            // widget can be grown without the page needing an invisible handle below it.
            ResizeDivider(
                contentColor = contentColor,
                onDrag = { deltaDp -> onResize(index, entries[index].heightDp + deltaDp) },
            )
        }

        if (entries.isNotEmpty()) {
            OutlinedButton(
                onClick = onAddWidget,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 32.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.widget_add))
            }
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
            heightDp = entry.heightDp,
            onDismiss = { resizingIndex = null },
            onChange = { onResize(index, it) },
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
 * The grab line between two entries.
 *
 * Only visible while a drag is in progress: a permanently drawn handle on every entry is a row
 * of bars across the page, and the page is meant to look like the content on it rather than
 * like an editing surface.
 */
@Composable
private fun ResizeDivider(contentColor: Color, onDrag: (deltaDp: Int) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var travelled by remember { mutableStateOf(0f) }
    var lastDelta by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = true; travelled = 0f; lastDelta = 0 },
                    onDragEnd = { dragging = false; travelled = 0f },
                    onDragCancel = { dragging = false; travelled = 0f },
                ) { change, amount ->
                    change.consume()
                    travelled += kotlin.math.abs(amount.y)
                    if (travelled > RESIZE_SLOP_PX) {
                        val deltaDp = with(density) { amount.y.toDp() }.value.toInt()
                        if (deltaDp != lastDelta) {
                            lastDelta = deltaDp
                            onDrag(deltaDp)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val alpha by animateFloatAsState(
            targetValue = if (dragging) 1f else 0f,
            label = "dividerAlpha",
        )
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(4.dp)
                .graphicsLayer { this.alpha = alpha }
                .background(contentColor.copy(alpha = 0.55f), CircleShape),
        )
    }
}

/** The precise way to resize, for anyone who would rather not drag and watch. */
@Composable
private fun ResizeDialog(heightDp: Int, onDismiss: () -> Unit, onChange: (Int) -> Unit) {
    var value by remember { mutableIntStateOf(heightDp) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.minus_one_resize)) },
        text = {
            Column {
                Text("${value} dp", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = value.toFloat(),
                    onValueChange = { value = it.toInt() },
                    valueRange = MINUS_ONE_MIN_HEIGHT_DP.toFloat()..MINUS_ONE_MAX_HEIGHT_DP.toFloat(),
                    onValueChangeFinished = { onChange(value) },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

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