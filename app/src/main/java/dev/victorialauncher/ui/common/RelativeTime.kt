// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

/**
 * How long ago something happened, in the short form a launcher row has room for.
 *
 * Deliberately pure and free of any resource or context: this is the one piece of a
 * notification line that can be got wrong in ways only a test would catch — a negative age
 * after a clock change, a zero that reads as an hour, a boundary landing on the wrong side.
 */
object RelativeTime {

    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 60 * MINUTE_MS
    private const val DAY_MS = 24 * HOUR_MS

    /**
     * The age of [thenMs] relative to [nowMs], in the terse form used beside an app name.
     *
     * Seconds are not shown at all. A row that reads "4s" is flicking by fast enough to be
     * unreadable and, worse, animating while someone is trying to read it; "now" says the
     * same thing and stays still.
     *
     * A [thenMs] in the future — which a clock correction produces, and a notification posted
     * by an app with a skewed clock produces routinely — reads as "now" rather than as a
     * negative age. Showing "-3m" would be pointing at a bug that is not there.
     *
     * Days stop the count. A notification that has been sitting for a month is not news, and
     * "31d" on a home screen invites a tap that cannot lead anywhere useful.
     */
    fun ago(thenMs: Long, nowMs: Long): String {
        val elapsed = nowMs - thenMs
        if (elapsed < MINUTE_MS) return "now"
        return when {
            elapsed < HOUR_MS -> "${elapsed / MINUTE_MS}m"
            elapsed < DAY_MS -> "${elapsed / HOUR_MS}h"
            else -> {
                val days = elapsed / DAY_MS
                if (days >= MAX_DAYS) MAX_DAYS_LABEL else "${days}d"
            }
        }
    }

    /** Beyond this it is simply "a while ago"; a precise figure for it is noise. */
    private const val MAX_DAYS = 7L
    private const val MAX_DAYS_LABEL = "a while"
}