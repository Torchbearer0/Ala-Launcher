// SPDX-License-Identifier: GPL-3.0-or-later
package dev.alalauncher.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGroupsTest {

    private fun entry(
        key: String,
        pkg: String = "com.example",
        serial: Long = 0L,
        postTime: Long = 0L,
        summary: Boolean = false,
        clearable: Boolean = true,
    ) = NotificationEntry(
        key = key,
        packageName = pkg,
        userSerial = serial,
        title = "t",
        text = "x",
        lines = emptyList(),
        postTime = postTime,
        ongoing = !clearable,
        clearable = clearable,
        isGroupSummary = summary,
    )

    @Test
    fun `one app is one group`() {
        val groups = NotificationGroups.group(
            listOf(
                entry("a", postTime = 10),
                entry("b", postTime = 20),
                entry("c", postTime = 30),
            )
        )
        assertEquals(1, groups.size)
        assertEquals(3, groups[0].entries.size)
    }

    @Test
    fun `different apps are different groups`() {
        val groups = NotificationGroups.group(
            listOf(entry("a", pkg = "one"), entry("b", pkg = "two"))
        )
        assertEquals(2, groups.size)
        assertEquals(listOf("one", "two"), groups.map { it.packageName }.sorted())
    }

    @Test
    fun `the same app on two profiles stays apart`() {
        // A work app and its personal twin share a package name and are not the same app. Merged,
        // one profile's notifications would appear under the other's heading and dismissing
        // would read as the wrong one going.
        val groups = NotificationGroups.group(
            listOf(entry("a", serial = 0L), entry("b", serial = 10L))
        )
        assertEquals(2, groups.size)
        assertEquals(listOf(0L, 10L), groups.map { it.userSerial }.sorted())
    }

    @Test
    fun `group summaries are dropped`() {
        val groups = NotificationGroups.group(
            listOf(entry("a"), entry("s", summary = true))
        )
        assertEquals(1, groups.size)
        assertEquals(listOf("a"), groups[0].entries.map { it.key })
    }

    @Test
    fun `a group of nothing but summaries disappears entirely`() {
        val groups = NotificationGroups.group(listOf(entry("s1", summary = true), entry("s2", summary = true)))
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `newest notification is first within a group`() {
        val groups = NotificationGroups.group(
            listOf(entry("old", postTime = 100), entry("new", postTime = 300), entry("mid", postTime = 200))
        )
        assertEquals(listOf("new", "mid", "old"), groups[0].entries.map { it.key })
    }

    @Test
    fun `order is the same however the shade handed them over`() {
        // The point of sorting at all. activeNotifications has no promised order, so two
        // publishes of the same set in opposite orders must produce the same display, or the
        // list moves under the reader for no reason they can see.
        val forwards = listOf(entry("a", postTime = 10), entry("b", postTime = 20), entry("c", postTime = 30))
        val backwards = forwards.reversed()

        fun shape(list: List<NotificationEntry>) =
            NotificationGroups.group(list).flatMap { g -> g.entries.map { it.key } }

        assertEquals(shape(forwards), shape(backwards))
    }

    @Test
    fun `the group that posted most recently comes first`() {
        val groups = NotificationGroups.group(
            listOf(
                entry("old-app-msg", pkg = "quiet", postTime = 10),
                entry("new-app-msg", pkg = "busy", postTime = 500),
                entry("quiet-later", pkg = "quiet", postTime = 400),
            )
        )
        assertEquals("busy", groups[0].packageName)
    }

    @Test
    fun `clearable count ignores what the system will not let go of`() {
        val groups = NotificationGroups.group(
            listOf(
                entry("a", clearable = true),
                entry("b", clearable = false),
                entry("c", clearable = false),
            )
        )
        assertEquals(1, groups[0].clearableCount)
    }

    @Test
    fun `a group with nothing clearable reports none`() {
        // The Clear all button is hidden on this. Showing it over a panel holding only ongoing
        // notifications invites a tap that cannot do anything.
        val groups = NotificationGroups.group(listOf(entry("a", clearable = false)))
        assertEquals(0, groups[0].clearableCount)
    }

    @Test
    fun `an empty shade is empty and not a failure`() {
        assertTrue(NotificationGroups.group(emptyList()).isEmpty())
    }

    @Test
    fun `notifications at the same instant keep a stable order`() {
        // Two posted in the same millisecond is ordinary on a busy phone. Sorting by time alone
        // would leave them in whatever order the provider happened to use.
        val forwards = listOf(entry("a", postTime = 50), entry("b", postTime = 50))
        assertEquals(listOf("a", "b"), NotificationGroups.group(forwards)[0].entries.map { it.key })
    }

    @Test
    fun `a group is not disturbed by an ungroupable one`() {
        val groups = NotificationGroups.group(
            listOf(entry("grouped", pkg = "one", postTime = 10), entry("alone", pkg = "two", postTime = 20))
        )
        assertEquals(2, groups.size)
        assertFalse(groups.any { g -> g.entries.any { it.key == "nope" } })
    }
}