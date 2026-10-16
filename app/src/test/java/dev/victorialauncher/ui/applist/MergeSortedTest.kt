// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How a letter's apps and its folders go into one order.
 *
 * Only reachable from a JVM test because it takes plain values. [buildAppListModel] itself
 * cannot be built here — [dev.victorialauncher.data.AppInfo] holds a ComponentName, which is a
 * framework stub off-device — so this is where the ordering is pinned instead, and the model is
 * checked on a phone.
 */
class MergeSortedTest {

    private fun merge(
        apps: List<String>,
        folders: List<String>,
        byUsage: Boolean = false,
    ) = mergeSorted(apps, folders, { it }, byUsage)

    @Test
    fun `a folder sorts among the apps by name`() {
        assertEquals(
            listOf("Firefox", "Games", "Gmail", "Instagram"),
            merge(apps = listOf("Gmail", "Instagram", "Firefox"), folders = listOf("Games")),
        )
    }

    @Test
    fun `folders sort among themselves`() {
        assertEquals(
            listOf("Games", "Media", "Work"),
            merge(apps = emptyList(), folders = listOf("Work", "Games", "Media")),
        )
    }

    @Test
    fun `sorting by usage puts folders after the apps`() {
        // A folder has no launch count. Ranking it would mean inventing one, so under a usage
        // sort the apps keep the order the user asked for and the folders follow, by name.
        assertEquals(
            listOf("Zed", "Gmail", "Games"),
            merge(apps = listOf("Zed", "Gmail"), folders = listOf("Games"), byUsage = true),
        )
    }

    @Test
    fun `a usage sort still orders the folders among themselves`() {
        assertEquals(
            listOf("Zed", "Apple", "Mango"),
            merge(apps = listOf("Zed"), folders = listOf("Mango", "Apple"), byUsage = true),
        )
    }

    @Test
    fun `names are compared without regard to case`() {
        // So does the alphabet, or "games" and "Gmail" would not sit together under G.
        assertEquals(
            listOf("amazon", "Games", "Gmail"),
            merge(apps = listOf("Gmail", "amazon"), folders = listOf("Games")),
        )
    }

    @Test
    fun `two rows of the same name keep the order they arrived in`() {
        // An app and a folder can share a name. A stable sort is the difference between that
        // being decided and it being decided differently on the next recomposition.
        assertEquals(
            listOf("Games", "Games"),
            merge(apps = listOf("Games"), folders = listOf("Games")),
        )
    }

    @Test
    fun `no folders leaves the apps exactly as they were`() {
        // The common case, and the one that must not be disturbed by any of this.
        assertEquals(
            listOf("Alpha", "Beta"),
            merge(apps = listOf("Alpha", "Beta"), folders = emptyList()),
        )
    }

    @Test
    fun `no apps leaves the folders exactly as they were`() {
        assertEquals(
            listOf("Games", "Work"),
            merge(apps = emptyList(), folders = listOf("Games", "Work")),
        )
    }

    @Test
    fun `nothing at all is still nothing`() {
        assertEquals(emptyList<String>(), merge(apps = emptyList(), folders = emptyList()))
    }
}