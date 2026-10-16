// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.victorialauncher.media.NowPlayingListenerService
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.victorialauncher.R
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.media.NotificationBus
import dev.victorialauncher.media.NotificationActionEntry
import dev.victorialauncher.media.NotificationEntry
import dev.victorialauncher.media.NotificationGroups
import dev.victorialauncher.ui.common.AppIcon
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Everything on the shade, in full, grouped by the app that put it there.
 *
 * This shows the text of notifications, which the badges deliberately never did. That is a real
 * change in what leaves this app: a badge is drawn whether or not anyone is looking, whereas this
 * panel is only ever opened on purpose, and everything in it came from an app that chose to post
 * it while the phone was locked or left on a table.
 *
 * An ongoing notification is listed but cannot be swiped away, because the system shade will
 * not clear it either — cancelling a playing track or a running download is not a tidy-up.
 */
@Composable
fun NotificationPanel(
    visible: Boolean,
    notifications: List<NotificationEntry>,
    appsByPackage: Map<String, AppInfo?>,
    contentColor: Color,
    onDismissEntry: (String) -> Unit,
    onPerformAction: (NotificationActionEntry, String?) -> Unit,
    onOpenApp: (AppInfo) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    // Grouping is [NotificationGroups]'s rather than anything done here, so that the order the
    // panel shows is stated in one tested place instead of re-decided at the point of drawing.
    val grouped = remember(notifications) { NotificationGroups.group(notifications) }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Scrim first, so a tap anywhere the list does not cover closes it. Deliberately
            // not the same handler as the list's own: a tap on a notification is that app's
            // to open, and must not also close the panel underneath it.
            // Light, because the home screen behind is already blurred harder than the app
            // panel's is — a heavy wash on top of that just puts the grey back.
            .background(Color.Black.copy(alpha = 0.12f))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = onClose,
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 48.dp, start = 12.dp, end = 12.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xE6222226))
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    onClick = {},
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.notifications_title),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (grouped.any { it.clearableCount > 0 }) {
                    Text(
                        text = stringResource(R.string.notifications_clear_all),
                        color = contentColor,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onClearAll)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            if (grouped.isEmpty()) {
                Text(
                    text = stringResource(R.string.notifications_empty),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 26.dp),
                )
            } else {
                // Read once: the fallback below runs inside a plain callback, where
                // LocalContext cannot be read.
                val panelContext = LocalContext.current
                LazyColumn(
                    modifier = Modifier.heightIn(max = 1400.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 14.dp),
                ) {
                    grouped.forEach { group ->
                        item(key = "group:" + group.packageName + ":" + group.userSerial) {
                            NotificationGroupHeader(
                                packageName = group.packageName,
                                count = group.entries.size,
                                appsByPackage = appsByPackage,
                                onOpenApp = onOpenApp,
                            )
                        }
                        items(group.entries, key = { it.key }) { entry ->
                            NotificationRow(
                                entry = entry,
                                onDismiss = { onDismissEntry(entry.key) },
                                onPerformAction = onPerformAction,
                                // Always tappable, and it sends what the app asked the tap to run —
                                // which is the particular screen, not just the app's front page.
                                // Keyed off appsByPackage this only worked for apps already
                                // pinned, so every notification from an app the user had not
                                // favourited was a row that ignored the finger.
                                onOpen = {
                                    NowPlayingListenerService.open(entry.contentIntent) {
                                        // No intent, or one that would not fire. The app itself
                                        // is the best answer available rather than nothing.
                                        panelContext.packageManager
                                            .getLaunchIntentForPackage(entry.packageName)
                                            ?.let { launch ->
                                                panelContext.startActivity(
                                                    launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                                )
                                            }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The app's icon and name, once per group rather than once per notification. */
@Composable
private fun NotificationGroupHeader(
    packageName: String,
    count: Int,
    appsByPackage: Map<String, AppInfo?>,
    onOpenApp: (AppInfo) -> Unit,
) {
    val app = appsByPackage[packageName]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (app != null) Modifier.clickable { onOpenApp(app) } else Modifier
            )
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app != null) {
            AppIcon(app = app, sizeDp = 22)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = app?.label ?: packageName,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 12.sp,
        )
    }
}

/**
 * One notification, swiped sideways to take off the shade.
 *
 * The row is not moved out of the list when dismissed: the shade still holds it until the
 * listener says otherwise, and removing it here would make a dismissal that silently failed
 * look like one that worked. The service's next publish is what removes it.
 */
@Composable
internal fun NotificationRow(
    entry: NotificationEntry,
    onDismiss: () -> Unit,
    onPerformAction: (NotificationActionEntry, String?) -> Unit,
    /** Opens the app that posted this. Null when the row is already inside that app's panel. */
    onOpen: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val dismissPx = with(density) { 96.dp.toPx() }

    var offsetX by remember(entry.key) { mutableStateOf(0f) }
    // Fades as it goes, so the row reads as leaving rather than as being dragged off.
    val alpha by animateFloatAsState(
        targetValue = 1f - (abs(offsetX) / (dismissPx * 2f)).coerceIn(0f, 0.75f),
        label = "notificationRowAlpha",
    )

    // Set the moment the row is let go of, so the animation plays out and the service is told
    // once rather than on every frame of the drag.
    var released by remember(entry.key) { mutableStateOf(false) }

    // Which action, if any, is waiting for its text. Keyed to the notification so a publish
    // that replaces the row does not leave a reply box open for an action that is gone.
    var replyingTo by remember(entry.key) { mutableStateOf<NotificationActionEntry?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // A tap on the body opens the app. Declined here and not left to the row's parent,
            // because the whole row is the swipe target and a tap has to be told apart from the
            // release that ends a drag.
            .then(
                if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier
            )
            .graphicsLayer {
                translationX = offsetX
                this.alpha = alpha
            }
            .draggable(
                // Not offered at all where the system would refuse it. A row that invites a
                // swipe and then springs back reads as a broken gesture rather than as a
                // notification that cannot be cleared.
                enabled = entry.clearable,
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    // Both directions dismiss, because the shade offers no left/right meaning
                    // here and the row has no horizontal neighbour to get out of the way of.
                    offsetX += delta
                    if (abs(offsetX) > dismissPx && !released) {
                        released = true
                        onDismiss()
                    }
                },
                onDragStopped = {
                    // Past the threshold the service has been told and the row is on its way
                    // out; snapping back would fight that. Short of it, the row returns.
                    if (!released) {
                        offsetX = 0f
                    } else {
                        offsetX = if (offsetX > 0) dismissPx * 1.4f else -dismissPx * 1.4f
                    }
                },
            )
            .padding(horizontal = 20.dp, vertical = 9.dp),
    ) {
        Column {
            entry.title?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it.toString(),
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // An inbox or messaging notification carries several lines where `text` carries
            // only the newest, so the lines are shown and the summary text left out.
            val body = if (entry.lines.size > 1) entry.lines else listOfNotNull(entry.text)
            body.forEach { line ->
                if (line.isNotBlank()) {
                    Text(
                        text = line.toString(),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 13.sp,
                        maxLines = if (entry.lines.size > 1) 1 else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (entry.title.isNullOrBlank() && body.none { it.isNotBlank() }) {
                // Some apps post with nothing readable in them. Showing the app's name keeps
                // the row from being a blank strip that can only be swiped away.
                Text(
                    text = stringResource(R.string.notifications_no_text),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                )
            }
            if (entry.actions.isNotEmpty()) {
                // Below the text and outside the swipe's way: a drag that started on a button
                // pressed the button, not the row. The whole row is the swipe target, so the
                // buttons claim their own presses first.
                Spacer(Modifier.height(6.dp))
                NotificationActionBar(
                    actions = entry.actions,
                    onPerform = { action ->
                        // An action that wants text opens the field instead of firing. There is
                        // no path from here that sends a reply with nothing in it.
                        if (action.needsText) replyingTo = action else onPerformAction(action, null)
                    },
                )
            }
            // Open in place, under the button that asked for it, rather than in a dialog: the
            // thing being replied to is right above and stays visible while the reply is typed.
            replyingTo?.let { action ->
                Spacer(Modifier.height(6.dp))
                NotificationReplyBox(
                    onSend = { text ->
                        onPerformAction(action, text)
                        // Closed here rather than waiting for the shade to change: the send is
                        // asynchronous and slow if the posting app is slow, and a box left open
                        // around a message that has gone reads as the reply not having taken.
                        replyingTo = null
                    },
                    onCancel = { replyingTo = null },
                )
            }
        }
    }
}

/**
 * The text field a reply action needs before it can be sent.
 *
 * Sending nothing is not offered. A reply action fired without text delivers an empty message
 * to the other person, which is worse than not having pressed the button at all.
 */
@Composable
internal fun NotificationReplyBox(
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val canSend = text.isNotBlank()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.notifications_reply_hint), fontSize = 13.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.notifications_reply_send),
            color = if (canSend) Color(0xFF8AB4F8) else Color.White.copy(alpha = 0.3f),
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White.copy(alpha = if (canSend) 0.10f else 0.04f))
                // Disabled rather than hidden, so the field does not move when it becomes
                // sendable — a button that appears under the cursor is the one that is missed.
                .clickable(enabled = canSend) { onSend(text) }
                .padding(horizontal = 12.dp, vertical = 9.dp),
        )
        Text(
            text = stringResource(R.string.notifications_reply_cancel),
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .clickable { onCancel() }
                .padding(horizontal = 8.dp, vertical = 9.dp),
        )
    }
}

/**
 * A notification's own buttons, as they were labelled by the app that posted it.
 *
 * Not a substitute for the shade's overflow menu, and deliberately plain: a row has room for a
 * few short labels and not for icons, and an icon here would be the posting app's icon drawn
 * at a size it was never designed for.
 */
@Composable
internal fun NotificationActionBar(
    actions: List<NotificationActionEntry>,
    onPerform: (NotificationActionEntry) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            Text(
                text = action.title.toString(),
                color = Color(0xFF8AB4F8),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable { onPerform(action) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
