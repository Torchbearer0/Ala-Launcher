// SPDX-License-Identifier: GPL-3.0-or-later
package dev.alalauncher.media

import android.content.Context
import androidx.core.app.NotificationManagerCompat

/**
 * Whether this launcher is currently one of the system's notification listeners.
 *
 * Read from the platform rather than remembered, because the permission is granted in a
 * separate system screen: a value captured at launch goes stale the moment someone turns the
 * feature on, and a launcher that believed it could not listen would draw a row asking for
 * access it already had.
 */
fun isListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
