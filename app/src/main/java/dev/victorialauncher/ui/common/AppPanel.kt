// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.ShortcutInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import dev.victorialauncher.R
import dev.victorialauncher.VictoriaApp
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.media.NotificationBus
import dev.victorialauncher.media.NowPlayingListenerService
import dev.victorialauncher.ui.notifications.NotificationRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_SHORTCUTS = 5
private const val MAX_WIDGETS = 4
private const val ICON_PX = 72

/** Clear of the navigation bar and the gesture pill. */
private const val PANEL_BOTTOM_GAP_DP = 28

/**
 * The narrowest a panel is allowed to get.
 *
 * It follows the finger across, so without a floor a swipe near the right edge would open a panel
 * a few icons wide with the rest of its contents off the screen. This is about the width of the
 * widest thing it can hold - the app's own icon and name on one line - plus a little air.
 */
private const val PANEL_MIN_WIDTH_DP = 220

/** Tall enough for notifications, five shortcuts and four widgets, and no taller. */
private const val PANEL_MAX_HEIGHT_DP = 560

/**
 * Everything one app has going on: what it is telling you, what it offers, and what it can put
 * on the home screen.
 *
 * Opened by swiping a row sideways rather than by tapping it, because a tap is already spoken
 * for and this is a second look rather than a first one.
 *
 * A section with nothing in it is not drawn. Most apps publish no shortcuts and most publish no
 * widgets, and three permanently empty headings would make this a place to read rather than
 * something to glance at.
 */
@Composable
fun AppPanel(
    app: AppInfo,
    labelSizeSp: Int,
    /** Where the panel starts, as a top offset: just under the row that was swiped. */
    topDp: Dp,
    /** Where the panel's own left edge sits: in line with the row's icon. */
    leftDp: Dp,
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit,
    onOpenWidgetPicker: (String) -> Unit,
    /** Adds the chosen widget offered by an app without going through the full picker. */
    onAddWidgetDirect: (AppWidgetProviderInfo) -> Unit,
) {
    val context = LocalContext.current
    val victoriaApp = context.applicationContext as VictoriaApp
    val density = context.resources.displayMetrics.density
    val densityPx = context.resources.displayMetrics.densityDpi
    val packageName = app.componentName.packageName

    // How far across the panel may start and still leave it [PANEL_MIN_WIDTH_DP] wide.
    val maxLeftDp = (LocalConfiguration.current.screenWidthDp - PANEL_MIN_WIDTH_DP - 8).coerceAtLeast(0).dp

    val all by NotificationBus.notifications.collectAsState()
    // This app's own, and not its group summary: the summary is a heading for messages that
    // are listed here already.
    val notifications = remember(all, packageName) {
        all.filter { it.packageName == packageName && !it.isGroupSummary }
    }


    var shortcuts by remember(app.key) { mutableStateOf<List<ShortcutInfo>>(emptyList()) }
    var widgetProviders by remember(app.key) { mutableStateOf<List<AppWidgetProviderInfo>>(emptyList()) }
    var pendingWidget by remember(app.key) { mutableStateOf<AppWidgetProviderInfo?>(null) }
    // Read once when the panel opens rather than as the list is drawn: shortcuts are a call
    // into the system per app, and the widgets are a query across every installed provider.
    LaunchedEffect(app.key) {
        val repo = victoriaApp.appRepository
        shortcuts = withContext(Dispatchers.IO) { repo.appShortcuts(app).take(MAX_SHORTCUTS) }
        widgetProviders = withContext(Dispatchers.IO) {
            runCatching {
                AppWidgetManager.getInstance(context).installedProviders
                    .filter { it.provider.packageName == packageName }
                    .distinctBy { it.provider.flattenToString() }
                    .take(MAX_WIDGETS)
            }.getOrDefault(emptyList())
        }
    }

    // Measured, not assumed. Clamping against a fixed maximum put every panel from the middle of
    // the list downward at one height, which threw away the thing the placement was for: a
    // panel belongs under the row that opened it. Measuring what this panel actually needs and
    // moving it up only by that much keeps it under its own row wherever there is room, and
    // lifts it just enough where there is not.
    var panelHeightPx by remember { mutableStateOf(0) }
    val densityNow = LocalDensity.current
    val placedTopPx = with(densityNow) {
        val screenPx = LocalConfiguration.current.screenHeightDp.dp.toPx()
        val gapPx = PANEL_BOTTOM_GAP_DP.dp.toPx()
        // The offset is applied after layout, so reading the height here cannot feed back into
        // it: the panel is measured at whatever top it was drawn at first, and only then moved.
        minOf(topDp.toPx(), screenPx - panelHeightPx - gapPx).coerceAtLeast(0f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Barely there: the blur behind is doing the work. The scrim is only there to say
            // "this screen is behind something" — the home screen under it is already out of
            // focus, so it needs no more hiding than a little dim.
            .background(Color.Black.copy(alpha = 0.10f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                // Only as tall as what it has to say, and starting under the row that opened it.
                // A panel pinned to the screen's corner reads as a dialog; one that comes out of
                // the row reads as belonging to it.
                .offset { IntOffset(0, placedTopPx.roundToInt()) }
                // Where it starts across, clamped so it always keeps a usable width.
                //
                // This used to be pinned to the row's icon, which on a left-aligned list put every
                // panel hard against the left edge no matter where the row was. The long-press
                // menu on the same screen appears wherever you pressed, and the two sitting at
                // different horizontal places for the same gesture read as a mistake. So the
                // panel follows the finger now too - with a floor, because anchoring to a touch
                // near the right edge literally would squeeze the panel to a few icons wide.
                .padding(start = leftDp.coerceIn(0.dp, maxLeftDp), end = 8.dp)
                .widthIn(max = 340.dp)
                // A ceiling for a panel with a great deal in it. The list inside scrolls, so
                // hitting this costs nothing but the bottom rows being below the fold.
                .heightIn(max = PANEL_MAX_HEIGHT_DP.dp)
                .onSizeChanged { panelHeightPx = it.height }
                .clip(RoundedCornerShape(24.dp))
                // Near-clear. Over a blurred home screen the text reads on its own, and the
                // grey card this replaced was the thing that made it look like a dialog.
                .background(Color.Black.copy(alpha = 0.22f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {},
                ),
        ) {
            AppPanelHeader(
                app = app,
                clearableCount = notifications.count { it.clearable },
                onOpenApp = onOpenApp,
                onDismiss = onDismiss,
                onClearAll = {
                    // This app's, and only what the system will let go of: a playing track or a
                    // running download is not something to tidy away.
                    notifications.filter { it.clearable }
                        .forEach { NowPlayingListenerService.dismiss(it.key) }
                },
            )

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                if (notifications.isNotEmpty()) {
                    item(key = "label-notifications") {
                        PanelLabel(stringResource(R.string.notifications_title))
                    }
                    items(notifications, key = { it.key }) { entry ->
                        NotificationRow(
                            entry = entry,
                            onDismiss = { NowPlayingListenerService.dismiss(entry.key) },
                            onPerformAction = { action, text ->
                                NowPlayingListenerService.performAction(
                                    action.intent, action.remoteInputs, text,
                                )
                            },
                            // Sends what the app asked the tap to run, so a message opens the
                            // chat it arrived in rather than the app's front page — which is
                            // what onOpenApp alone did, and what made every notification here
                            // feel like it had ignored what it was about.
                            onOpen = {
                                NowPlayingListenerService.open(entry.contentIntent) { onOpenApp() }
                            },
                        )
                    }
                }

                if (shortcuts.isNotEmpty()) {
                    item(key = "label-shortcuts") {
                        PanelLabel(stringResource(R.string.app_panel_shortcuts))
                    }
                    items(shortcuts, key = { it.id }) { shortcut ->
                        val label = (shortcut.shortLabel ?: shortcut.longLabel)?.toString().orEmpty()
                        val icon = remember(shortcut.id, densityPx) {
                            runCatching {
                                victoriaApp.appRepository.shortcutIcon(shortcut, densityPx)
                                    ?.toBitmap(ICON_PX, ICON_PX)?.asImageBitmap()
                            }.getOrNull()
                        }
                        PanelRow(
                            label = label,
                            icon = icon,
                            labelSizeSp = labelSizeSp,
                            onClick = {
                                victoriaApp.appRepository.startAppShortcut(shortcut)
                                onDismiss()
                            },
                        )
                    }
                }

                if (widgetProviders.isNotEmpty()) {
                    item(key = "label-widgets") {
                        PanelLabel(stringResource(R.string.app_panel_widgets))
                    }
                    items(widgetProviders, key = { it.provider.flattenToString() }) { info ->
                        val label = runCatching { info.loadLabel(context.packageManager) }
                            .getOrNull()?.toString()?.takeIf { it.isNotBlank() }
                            ?: info.provider.packageName
                        PanelRow(
                            label = label,
                            icon = runCatching { info.loadIcon(context, densityPx).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull(),
                            labelSizeSp = labelSizeSp,
                            leading = {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            // Asks first rather than adding outright: a mis-tap here is easy
                            // and the widget lands on the home screen, where taking it off is
                            // a separate trip. Each row is one provider, so the confirmation
                            // is also where the variant is chosen — no catalogue needed.
                            onClick = { pendingWidget = info },
                        )
                    }
                }

                if (notifications.isEmpty() && shortcuts.isEmpty() && widgetProviders.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.app_panel_empty),
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 26.dp),
                        )
                    }
                }
            }
            pendingWidget?.let { info ->
                AlertDialog(
                    onDismissRequest = { pendingWidget = null },
                    title = { Text(stringResource(R.string.widget_add), color = Color.White) },
                    text = {
                        Text(
                            runCatching { info.loadLabel(context.packageManager) }
                                .getOrNull()?.toString()?.takeIf { it.isNotBlank() }
                                ?: info.provider.packageName,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            onAddWidgetDirect(info)
                            pendingWidget = null
                        }) { Text(stringResource(R.string.widget_add), color = Color.White) }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingWidget = null }) {
                            Text(stringResource(R.string.action_cancel), color = Color.White.copy(alpha = 0.75f))
                        }
                    },
                    containerColor = Color.Black.copy(alpha = 0.32f),
                )
            }
        }
    }
}

/** Header: the app, and the two controls that are not "launch". */
@Composable
private fun AppPanelHeader(
    app: AppInfo,
    clearableCount: Int,
    onOpenApp: () -> Unit,
    onDismiss: () -> Unit,
    onClearAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app = app, sizeDp = 36)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onOpenApp() }
        ) {
            Text(
                text = app.label,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.app_panel_tap_to_open),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
            )
        }
        // Present only when there is something it can clear. A control that does nothing is
        // worse than no control.
        if (clearableCount > 0) {
            Icon(
                Icons.Filled.DeleteSweep,
                contentDescription = stringResource(R.string.app_panel_clear_all),
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable { onClearAll() }
                    .padding(4.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            Icons.Filled.Close,
            contentDescription = stringResource(R.string.app_panel_close),
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable { onDismiss() }
                .padding(4.dp),
        )
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.45f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun PanelRow(
    label: String,
    icon: ImageBitmap?,
    labelSizeSp: Int,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        } else if (icon != null) {
            Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = labelSizeSp.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
