// SPDX-License-Identifier: GPL-3.0-or-later
package dev.alalauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where a page with entries on it still has room for another one.
 *
 * The arithmetic lives in [WidgetPageFill] in the main source set, because the page uses it.
 *
 * The case at the bottom of this file - [a gap one inter-item gap too wide would offer] - is the
 * one worth reading twice. Every earlier version of this function and of these expectations was
 * eight dp wrong in the same direction, so they agreed with each other and passed, and the error
 * only showed up on a device: the page said 171dp free, offered a 171dp widget, and could not lay
 * it out. A widget that fills a gap still has to be *placed* there, and placing costs a gap.
 */
class WidgetPageFillTest {

    private val container = 400
    private val gap = 8
    private val min = 140

    private fun slot(width: Int, height: Int = 100) = WidgetPageFill.Slot(width, height)

    /** One full-width entry: it takes the line, so nothing is left to fill. */
    @Test
    fun `a single full-width entry leaves no gap`() {
        assertNull(WidgetPageFill.firstGap(listOf(slot(0)), container, gap, min))
    }

    /**
     * The case that started this. A widget narrowed to 169dp in a 400dp row: 400 - 169 - 8 = 223
     * of room, and a 223dp widget would sit in it exactly (169 + 8 + 223 = 400).
     */
    @Test
    fun `a narrowed entry leaves a gap that can be filled`() {
        val found = WidgetPageFill.firstGap(listOf(slot(169)), container, gap, min)
        assertEquals(1, found?.insertIndex)
        assertEquals(223, found?.widthDp)
        assertEquals(100, found?.heightDp)
    }

    /**
     * Only the *last* line needs this case, and it is the easy one to leave out: nothing follows a
     * single widget, so the loop that looks for a gap while the next entry is considered never
     * runs. Missing it left the page unable to fill the gap a resized widget opens.
     */
    @Test
    fun `the gap after the last entry is found without a following entry to find it`() {
        assertEquals(223, WidgetPageFill.firstGap(listOf(slot(169)), container, gap, min)?.widthDp)
    }

    /** Two entries that exactly fill a line leave no gap. */
    @Test
    fun `two entries that exactly fill a line leave no gap`() {
        assertNull(WidgetPageFill.firstGap(listOf(slot(169), slot(223)), container, gap, min))
    }

    /** The next entry does not fit, so what is left of that line is the gap. */
    @Test
    fun `the gap is what is left of the line the next entry could not fit on`() {
        val found = WidgetPageFill.firstGap(listOf(slot(169), slot(300)), container, gap, min)
        assertEquals(1, found?.insertIndex)
        assertEquals(223, found?.widthDp)
    }

    /** A gap too narrow for a widget is skipped rather than offered, so the button still works. */
    @Test
    fun `a gap narrower than the minimum is not offered`() {
        assertNull(WidgetPageFill.firstGap(listOf(slot(380)), container, gap, min))
    }

    /** The whole point of a minimum: it is checked, not assumed. 400 - 252 - 8 = 140 exactly. */
    @Test
    fun `a gap exactly at the minimum is offered`() {
        assertEquals(140, WidgetPageFill.firstGap(listOf(slot(252)), container, gap, min)?.widthDp)
    }

    /** One dp narrower and it is not. The minimum is a floor, not a rounded figure. */
    @Test
    fun `a gap one dp under the minimum is not offered`() {
        assertNull(WidgetPageFill.firstGap(listOf(slot(253)), container, gap, min))
    }

    /** An empty page has nothing to fill, so a widget goes on as its own line. */
    @Test
    fun `an empty page has no gap`() {
        assertNull(WidgetPageFill.firstGap(emptyList(), container, gap, min))
    }

    /**
     * A full-width entry goes onto its own line and leaves the space beside the narrowed entry
     * genuinely empty. That space is real and reachable, so it is offered.
     */
    @Test
    fun `a full-width entry leaves the space beside the one before it fillable`() {
        val found = WidgetPageFill.firstGap(listOf(slot(169), slot(0)), container, gap, min)
        assertEquals(1, found?.insertIndex)
        assertEquals(223, found?.widthDp)
    }

    /** The first gap is the one filled, not the largest or the last. 400 - 200 - 8 = 192. */
    @Test
    fun `the first gap is the one returned`() {
        val found = WidgetPageFill.firstGap(listOf(slot(200), slot(500)), container, gap, min)
        assertEquals(1, found?.insertIndex)
        assertEquals(192, found?.widthDp)
    }

    /** The gap's height is the tallest entry on the line it belongs to, not the first. */
    @Test
    fun `the gap takes the height of the tallest entry on its line`() {
        val found = WidgetPageFill.firstGap(
            listOf(slot(100, height = 80), slot(100, height = 180)),
            container, gap, min,
        )
        assertEquals(180, found?.heightDp)
    }

    /** Two lines, with a gap on the first: the first line's gap wins. */
    @Test
    fun `a wrapped line's gap is found before the ones below it`() {
        val found = WidgetPageFill.firstGap(listOf(slot(169), slot(400)), container, gap, min)
        assertEquals(1, found?.insertIndex)
        assertEquals(223, found?.widthDp)
    }

    /** A row measured before it has been laid out has no width, and so has no gaps. */
    @Test
    fun `no container means no gap`() {
        assertNull(WidgetPageFill.firstGap(listOf(slot(169)), 0, gap, min))
    }

    /**
     * Two widgets that do not share a line. The gap offered is beside the first one, not beside
     * the second: the button fills the first hole it finds in list order.
     */
    @Test
    fun `two widgets that do not quite share a line fill the space beside the first`() {
        val found = WidgetPageFill.firstGap(listOf(slot(169), slot(232)), container, gap, min)
        assertEquals(1, found?.insertIndex)
        assertEquals(223, found?.widthDp)
    }

    /** A gap on a later line, found by the entry that could not fit after it. */
    @Test
    fun `a gap on a later line is found by the following entry`() {
        val found = WidgetPageFill.firstGap(listOf(slot(400), slot(200), slot(400)), container, gap, min)
        assertEquals(2, found?.insertIndex)
        assertEquals(192, found?.widthDp)
    }

    /**
     * The bug, pinned.
     *
     * A 251dp widget in a 422dp row: 422 - 251 = 171 of space is visibly there, but a widget put
     * into it needs the 8dp that goes between two things, and 251 + 8 + 171 = 430 does not fit in
     * 422. The honest answer is 163, and 251 + 8 + 163 is exactly 422.
     *
     * Offered 171, the page put the widget on the next line and the gap stayed open - so the
     * feature looked broken while every number on screen agreed with every other number.
     */
    @Test
    fun `a gap one inter-item gap too wide would offer`() {
        val found = WidgetPageFill.firstGap(listOf(slot(251)), 422, 8, 140)
        assertEquals(163, found?.widthDp)
        assertEquals(163 + 251 + 8, 422)
    }

    /**
     * The same thing stated from the other side: whatever this returns must actually fit, because
     * the row will be asked to lay it out.
     */
    @Test
    fun `an offered width always fits beside the entries before it`() {
        val entries = listOf(slot(251), slot(180), slot(300))
        val found = WidgetPageFill.firstGap(entries, 422, 8, 140) ?: return
        // The width of the line the gap belongs to, plus the gap itself, plus the offered width.
        assertEquals(false, found.widthDp + 251 + 8 > 422)
    }
}