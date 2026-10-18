// SPDX-License-Identifier: GPL-3.0-or-later
package dev.alalauncher.media

import android.app.Notification
import android.app.PendingIntent
import android.os.Bundle
import android.app.RemoteInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * One of the buttons a notification carries, already pulled out of the platform's own list.
 *
 * [intent] is the system's, not ours: a notification's actions were built by the app that
 * posted it and handed to the system, so firing one does what that app always intended rather
 * than anything this launcher decides. It is held as a handle rather than sent now and stored
 * as text, because the target of an action is the posting app's own service or broadcast and
 * cannot be rebuilt from here.
 */
data class NotificationActionEntry(
    val title: CharSequence,
    val intent: PendingIntent,
    /**
     * What the action means, from [Notification.Action.getSemanticAction].
     *
     * Carried so the UI can tell a reply from a mark-as-read. A reply finishes the
     * notification by sending it, where the others are acted on and the notification stays.
     */
    val semantic: Int,
    /**
     * The free-form inputs this action expects, or empty when it takes no text.
     *
     * Firing a reply action without filling these in sends an empty message — the posting app
     * receives a reply of nothing, and most of them post it. An action with an input is
     * therefore never fired directly: the UI has to ask for the text first, and there is no
     * path here that sends one blank.
     *
     * The whole array rather than just the first: an action can offer several, and the keys
     * are each the posting app's own, so dropping the rest would send a reply missing whatever
     * the others carry.
     */
    val remoteInputs: List<RemoteInput> = emptyList(),
) {
    /** Sending this ends the notification, because the notification was the asking. */
    val isReply: Boolean get() = semantic == Notification.Action.SEMANTIC_ACTION_REPLY

    /** Whether this action needs text before it can be sent. */
    val needsText: Boolean get() = remoteInputs.isNotEmpty()
}

/**
 * One notification as the shade currently holds it.
 *
 * Deliberately a snapshot of text rather than a reference to a live [Notification]: the object
 * belongs to another app's process, and reading from it after that app has gone leaves a binder
 * proxy that throws on every field. Everything here is read once, on the listener's thread, and
 * copied out.
 */
data class NotificationEntry(
    /** The system's own key for this notification, and what dismissing one needs. */
    val key: String,
    val packageName: String,
    val userSerial: Long,
    val title: CharSequence?,
    val text: CharSequence?,
    /** Inbox and messaging styles carry several lines where `text` carries one. */
    val lines: List<CharSequence>,
    val postTime: Long,
    /** A media player's "playing" notification is a state, not something waiting to be read. */
    val ongoing: Boolean,
    /**
     * Whether the system will let this be cleared. False for anything ongoing — a playing
     * track, a running download — and for notifications on a profile this app cannot reach.
     * Read from the platform rather than inferred from [ongoing], which is not the same test.
     */
    val clearable: Boolean,
    /**
     * Set for the summary of a group. The children are the ones worth listing, and showing both
     * lists the same conversation twice.
     */
    val isGroupSummary: Boolean,
    /**
     * What a tap on the body is meant to run.
     *
     * Null for a notification that posted no intent, which is a real thing apps do. The body
     * then falls back to opening the app, because a row that cannot be tapped at all is worse
     * than one that opens the app rather than the exact screen the app had in mind.
     *
     * Held as a PendingIntent rather than anything read out of it, for the same reason the
     * action buttons are: the system owns it, so it stays valid after the posting app is gone.
     */
    val contentIntent: PendingIntent? = null,
    /** The buttons this notification offers, in the order it offered them. */
    val actions: List<NotificationActionEntry> = emptyList(),
)

/**
 * What is on the shade right now, in full.
 *
 * Bridges the NotificationListenerService to the UI for the same reason [NotificationCountBus]
 * does: the system owns the service and hands the app no handle to it.
 *
 * This bus exists because the notification list needs the text and the actions, and the badge
 * does not. The two are separate on purpose — a badge is drawn whether or not anyone is looking,
 * and a list is opened on purpose, so there is no reason for the badge's redraw to carry any of
 * it.
 */
object NotificationBus {
    private val _notifications = MutableStateFlow<List<NotificationEntry>>(emptyList())
    val notifications: StateFlow<List<NotificationEntry>> = _notifications

    fun update(value: List<NotificationEntry>) {
        _notifications.value = value
    }

    /**
     * Takes a notification off this bus before the system has confirmed it gone.
     *
     * The listener is the authority on what is on the shade, but it only speaks when it is
     * bound, and a dismissal asked for while it is not is a no-op that nothing reports. The
     * row then vanished from the panel's own list while every other reading of the same
     * notification - the badge on the icon, the preview under a home-screen row - went on
     * claiming it was there until the next post or removal happened to arrive.
     *
     * So the entry is dropped here as well, and [update] overwrites the result wholesale the
     * moment the system does answer. If the dismissal was refused, the notification comes
     * back on that next publish rather than being lost for good, which is the honest outcome:
     * the system still has it.
     */
    fun dismissLocally(key: String) {
        val current = _notifications.value
        if (current.none { it.key == key }) return
        _notifications.value = current.filterNot { it.key == key }
    }

    /** Nothing is known once the service goes, and a stale notification is worse than none. */
    fun clear() {
        _notifications.value = emptyList()
    }
}