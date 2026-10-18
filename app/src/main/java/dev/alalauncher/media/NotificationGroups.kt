// SPDX-License-Identifier: GPL-3.0-or-later
package dev.alalauncher.media

/**
 * The notifications of one app, in the order they should be read.
 */
data class NotificationGroup(
    val packageName: String,
    val userSerial: Long,
    val entries: List<NotificationEntry>,
) {
    /**
     * How many of these the system would let be cleared.
     *
     * Counted here rather than at the call site because there are two callers that need it and
     * a Clear all button that appears when it is above zero are one decision between them.
     */
    val clearableCount: Int get() = entries.count { it.clearable }
}

/**
 * Arranges the shade for display: one group per app, each ordered, the groups themselves
 * ordered by recency.
 *
 * Free of Android types so the whole of it can be tested on the JVM, which matters more than it
 * sounds. `groupBy` alone is stable, but only with respect to the order it was handed — and the
 * shade hands over `activeNotifications`, whose order the platform makes no promise about. That
 * is not a hypothetical: rows would settle into a different order on the next post, for no
 * reason anyone could name, which reads as the launcher rearranging itself while you read it.
 * Both orders here are therefore stated rather than inherited.
 */
object NotificationGroups {

    /**
     * One group per app, newest group first.
     *
     * Group summaries are dropped. A summary is the parent of a bundle and says the same thing
     * its children say, so keeping it lists the same conversation twice.
     */
    fun group(entries: List<NotificationEntry>): List<NotificationGroup> =
        entries
            .filterNot { it.isGroupSummary }
            .groupBy { it.packageName to it.userSerial }
            .map { (key, list) ->
                NotificationGroup(
                    packageName = key.first,
                    userSerial = key.second,
                    // Newest first within the group, so the heading sits above the thing most
                    // likely to be the reason the panel was opened.
                    entries = list.sortedByDescending { it.postTime },
                )
            }
            // By the newest thing in the group rather than the oldest: an app that just posted
            // is the one being looked for, however long ago its first notification arrived.
            .sortedByDescending { group -> group.entries.maxOf { it.postTime } }
}