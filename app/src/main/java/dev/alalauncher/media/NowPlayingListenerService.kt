// SPDX-License-Identifier: GPL-3.0-or-later
package dev.alalauncher.media

import android.app.Notification
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.content.ComponentName
import android.content.Intent
import android.content.Context
import android.app.PendingIntent
import android.os.Bundle
import android.os.UserManager
import android.app.RemoteInput
import android.provider.Settings
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import android.service.notification.StatusBarNotification
import dev.alalauncher.widget.ClockWidgetProvider

class NowPlayingListenerService : NotificationListenerService() {

    companion object {
        /** The shade's own limit on how many actions it shows. */
        private const val MAX_ACTIONS = 3
        /**
         * Asks the system to bind us again, when it is willing to and has not.
         *
         * Replacing the app kills the binding but leaves the permission switched on, so the
         * Now Playing card quietly stops updating and the setting still reads as granted —
         * there is nothing to turn back on, which makes it look like the feature broke. That
         * happens on every update, not only a sideload. requestRebind is the sanctioned way
         * back and does nothing if the permission was never given.
         */
        fun rebindIfPermitted(context: Context) {
            val component = ComponentName(context, NowPlayingListenerService::class.java)
            val enabled = runCatching {
                Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners",
                )
            }.getOrNull().orEmpty()
            if (!enabled.contains(component.flattenToString()) &&
                !enabled.contains(component.packageName + "/" + component.className)
            ) {
                return
            }
            runCatching { requestRebind(component) }
        }

        /**
         * The one instance the system has bound, while it is bound.
         *
         * Held rather than asked for: `getConnectedInstances` is hidden API, and the framework
         * instantiates this service itself, so it is the only way for the UI — which has no
         * handle on a service it did not create — to reach a bound listener. Null whenever the
         * permission is off or nothing is connected, which is what makes a dismissal asked for
         * at that moment do nothing rather than throw.
         */
        @Volatile
        private var bound: NowPlayingListenerService? = null

        /**
         * Takes a notification off the shade on the UI's behalf.
         *
         * The bus is told first so the row disappears wherever it is drawn - the panel, the
         * preview under a home-screen row, the drawer - rather than only where the caller
         * happened to be holding it. The system's own answer then overwrites that with the
         * truth, and a dismissal the system refused puts the notification back.
         *
         * The failure of the binder call is still swallowed: there is nothing to report it to,
         * and the authoritative publish that follows settles it either way.
         */
        fun dismiss(key: String) {
            NotificationBus.dismissLocally(key)
            runCatching { bound?.cancelNotification(key) }
        }

        /**
         * Taps the body of a notification: sends the intent the posting app asked that tap to
         * run, which is the screen the app meant to be on rather than merely its front page.
         *
         * Off the main thread for the same reason [performAction] is — the sending app runs its
         * own code in it, and a slow one would be a dropped frame here.
         */
        fun open(intent: PendingIntent?, onFallback: () -> Unit) {
            // A HandlerThread rather than a bare Thread, and that is the whole fix.
            // PendingIntent.send() is a synchronous binder call: with no Looper on the thread
            // it blocks and never returns, so the line after it was never reached, the
            // fallback never ran, and a tap did nothing at all — with no error to show for
            // it. Nothing here threw; the thread was simply still waiting.
            val worker = HandlerThread("ala-notification-open")
            worker.start()
            Handler(worker.looper).post {
                val sent = intent != null && runCatching { intent.send() }
                    .onFailure { Log.w(TAG, "notification content intent would not send", it) }
                    .isSuccess
                if (!sent) {
                    Handler(Looper.getMainLooper()).post(onFallback)
                }
                worker.quitSafely()
            }
        }

        /**
         * Presses one of a notification's own buttons, carrying [text] when the button asked
         * for some.
         *
         * The intent belongs to the app that posted the notification, so this runs their code
         * rather than anything of ours. It is sent off the main thread because a posting app is
         * free to do work in it, and a slow one would otherwise be a dropped frame here.
         *
         * A failure is swallowed: the button has already been pressed from the reader's point
         * of view, and the notification either changes or does not on the next publish.
         */
        fun performAction(intent: PendingIntent, remoteInputs: List<RemoteInput> = emptyList(), text: String? = null) {
            val typed = text?.takeIf { it.isNotBlank() }
            val service = bound
            if (service == null) {
                // Nothing is bound, so nothing could be sent. Worth saying out loud: a
                // notification action that silently does nothing is indistinguishable from one
                // that was never offered.
                Log.w(TAG, "notification action pressed with no listener bound")
                return
            }
            Thread {
                runCatching {
                    if (remoteInputs.isNotEmpty() && typed != null) {
                        // The results travel on a fillIn Intent handed to send(), which is how
                        // they reach the action's own Intent once it is delivered. The key is
                        // the posting app's, read from its RemoteInput rather than guessed: the
                        // wrong key sends a reply the app cannot find, which is
                        // indistinguishable from sending none.
                        val results = Bundle().apply {
                            remoteInputs.forEach { input ->
                                putCharSequence(input.resultKey, typed)
                            }
                        }
                        val fillIn = Intent().apply {
                            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                            RemoteInput.setResultsSource(this, RemoteInput.SOURCE_FREE_FORM_INPUT)
                            RemoteInput.addResultsToIntent(remoteInputs.toTypedArray(), this, results)
                        }
                        intent.send(service, 0, fillIn)
                    } else {
                        intent.send()
                    }
                }.onFailure { Log.w(TAG, "notification action failed", it) }
            }.start()
        }

        private const val TAG = "AlaNotifications"
    }

    private lateinit var sessionManager: MediaSessionManager
    private lateinit var componentName: ComponentName

    private var watched: MediaController? = null

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = republish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = republish()
        override fun onSessionDestroyed() {
            detach()
            republish()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        attachTo(controllers)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        bound = this
        publish()
        sessionManager = getSystemService(MediaSessionManager::class.java)
        componentName = ComponentName(this, NowPlayingListenerService::class.java)
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, componentName)
            attachTo(sessionManager.getActiveSessions(componentName))
        } catch (e: SecurityException) {
            // No listener standing, which the shade's own row already says.
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        bound = null
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
        detach()
        NotificationCountBus.clear()
        NotificationBus.clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        if (::sessionManager.isInitialized) {
            runCatching { attachTo(sessionManager.getActiveSessions(componentName)) }
        }
        publish()
    }

    /**
     * What is on the shade right now: counted for the badges, copied in full for the list.
     *
     * Both are read in one pass because both come from the same snapshot of `activeNotifications`
     * and a second read could catch a shade that has changed underneath it.
     *
     * Recounted from scratch rather than added to and subtracted from: a posted notification
     * can replace one already there without a removal ever arriving, and a count kept by
     * arithmetic drifts upward until something clears it.
     *
     * The count and the list disagree about ongoing notifications, on purpose. A count is a
     * badge, and a music player sitting in the shade should not put a number on the icon of an
     * app nobody is waiting on. The list is somewhere a person asked to be, and there a playing
     * track is worth showing on its own terms.
     */
    private fun publish() {
        val active = runCatching { activeNotifications }.getOrNull()
        if (active == null) {
            NotificationCountBus.clear()
            NotificationBus.clear()
            ClockWidgetProvider.renderAll(this)
            return
        }
        val counts = HashMap<String, Int>()
        val entries = ArrayList<NotificationEntry>(active.size)
        active.forEach { sbn ->
            val notification = sbn.notification ?: return@forEach
            val isSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
            if (!sbn.isClearable) {
                // Kept in the list even though it cannot be counted or dismissed: an ongoing
                // notification is visible on the shade, and hiding it here would make this
                // list claim there is nothing while the system is showing something.
                entries += sbn.toEntry(notification, isSummary)
                return@forEach
            }
            if (!isSummary) counts[sbn.packageName] = (counts[sbn.packageName] ?: 0) + 1
            entries += sbn.toEntry(notification, isSummary)
        }
        val before = NotificationCountBus.counts.value.values.sum()
        NotificationCountBus.update(counts)
        NotificationBus.update(entries)
        // A widget is drawn only when something asks. The home screen follows the bus on its
        // own; a RemoteViews cannot, so the total changing is what has to ask for it.
        if (counts.values.sum() != before) ClockWidgetProvider.renderAll(this)
    }

    /**
     * Copies one notification out into something safe to keep.
     *
     * The extras are read here, on the listener's thread, because `StatusBarNotification` is a
     * binder parcel owned by another process: keeping one and reading a field later goes
     * through a proxy that throws as soon as that process is gone.
     */
    private fun StatusBarNotification.toEntry(
        notification: Notification,
        isSummary: Boolean,
    ): NotificationEntry {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
        val lines = ArrayList<CharSequence>()
        val rawLines: Array<CharSequence>? = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        rawLines?.forEach { line -> lines.add(line) }
        val text = when {
            !big.isNullOrBlank() -> big
            lines.size > 1 -> lines.last()
            else -> extras.getCharSequence(Notification.EXTRA_TEXT)
        }
        return NotificationEntry(
            key = key,
            packageName = packageName,
            // UserHandle.serialNumber is hidden API, so the public lookup is asked instead —
            // it is what the badge path already goes through for the same reason.
            userSerial = runCatching {
                getSystemService(UserManager::class.java)
                    .getSerialNumberForUser(this@toEntry.user)
            }.getOrDefault(0L),
            title = title,
            text = text,
            lines = lines,
            postTime = postTime,
            ongoing = isOngoing,
            clearable = isClearable,
            isGroupSummary = isSummary,
                        contentIntent = notification.contentIntent,
            actions = readActions(notification),
        )
    }

    /**
     * The buttons a notification offers, capped at what fits.
     *
     * Three is the shade's own limit and is kept for the same reason: a notification with six
     * actions is not asking to have all of them on a launcher row, and a row that grows with
     * whatever an app decided is a row nobody can hit reliably.
     *
     * An action with no title is skipped — there is nothing to label a button with — and one
     * with no intent is skipped too, because a button that cannot do anything is worse than no
     * button. Both happen: apps do post actions they never finished wiring up.
     */
    private fun readActions(notification: Notification): List<NotificationActionEntry> {
        val actions = notification.actions ?: return emptyList()
        return actions.asSequence()
            .take(MAX_ACTIONS)
            .mapNotNull { action ->
                val title = action.title
                val intent = action.actionIntent
                if (title.isNullOrBlank() || intent == null) return@mapNotNull null
                NotificationActionEntry(
                    title = title,
                    intent = intent,
                    semantic = action.semanticAction,
                    // The whole array, not just the free-form one: an action can carry several
                    // and the posting app reads each under a key of its own choosing.
                    remoteInputs = action.remoteInputs?.filter { it.allowFreeFormInput }
                        ?: emptyList(),
                )
            }
            .toList()
    }

    /** Follow whichever session is worth showing, and watch it for changes. */
    private fun attachTo(controllers: List<MediaController>?) {
        val next = controllers?.firstOrNull { isLive(it.playbackState) }
            ?: controllers?.firstOrNull { it.playbackState != null }

        if (next?.sessionToken != watched?.sessionToken) {
            detach()
            watched = next
            next?.registerCallback(controllerCallback)
        }
        republish()
    }

    private fun detach() {
        watched?.let { runCatching { it.unregisterCallback(controllerCallback) } }
        watched = null
    }

    private fun republish() {
        // Only the shade is republished from here. The bus that carried a Now Playing
        // snapshot to a home-screen block went with that block: the music is announced by
        // the notification panel already, and keeping a copy alive that nothing reads was
        // a second source of truth for something that only ever has one.
    }

    /** Playing, paused or buffering counts; stopped, errored or idle does not. */
    private fun isLive(state: PlaybackState?): Boolean = when (state?.state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_PAUSED,
        PlaybackState.STATE_BUFFERING,
        -> true

        else -> false
    }
}
