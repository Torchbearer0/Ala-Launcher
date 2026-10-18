// SPDX-License-Identifier: GPL-3.0-or-later
package dev.alalauncher.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The corner button's drag decision, away from a phone.
 *
 * The regression these were written for was found on a device and not by reading the code: a
 * swipe left the "a drag happened" flag set, nothing cleared it, and the next real tap did
 * nothing. The first test here is that failure, kept because it is the easiest one to
 * reintroduce — the code still has to be right about when a flag is cleared.
 */
class CornerButtonDragTest {

    private val slop = 40f
    private fun drag() = CornerButtonDrag(slop)

    @Test
    fun `a press after a swipe is not still a swipe`() {
        // The bug: swipe up, release, then tap. Without the reset in onDown the second press
        // inherits the first one's travel and reads as a swipe, so the tap never fires and the
        // button looks dead.
        val d = drag()
        d.onDown()
        d.onMove(-200f)
        assertTrue("the swipe should have been recognised", d.isSwipe())

        d.onDown()
        assertFalse("a fresh press must not inherit the last one's travel", d.isSwipe())
        assertFalse("and must not read as having moved", d.moved())
    }

    @Test
    fun `a downward drag does not block the next tap either`() {
        // Same leak in the other direction: downward travel is not the swipe, but it is still
        // travel, and a flag set by it has to die with the gesture that set it.
        val d = drag()
        d.onDown()
        d.onMove(200f)
        assertFalse(d.isSwipe())
        assertTrue(d.moved())

        d.onDown()
        assertFalse(d.moved())
    }

    @Test
    fun `upward travel past the slop is the swipe`() {
        val d = drag()
        d.onDown()
        assertFalse(d.onMove(-(slop / 2f)))
        assertFalse("short of the slop is still a tap", d.isSwipe())
        assertTrue("past the slop is the swipe", d.onMove(-(slop + 1f)))
    }

    @Test
    fun `exactly the slop is not yet a swipe`() {
        // The platform has not accepted the drag at exactly the slop, so firing the swipe here
        // would answer a gesture a frame before it is distinguishable from a tap.
        val d = drag()
        d.onDown()
        assertFalse(d.onMove(-slop))
        assertFalse(d.isSwipe())
    }

    @Test
    fun `up and back down is not a swipe`() {
        // Travel is net, not distance. A finger that goes up past the slop and comes back has
        // not net-travelled upward, and answering that with the swipe would be surprising.
        val d = drag()
        d.onDown()
        d.onMove(-60f)
        d.onMove(60f)
        assertFalse(d.isSwipe())
    }

    @Test
    fun `downward travel is never consumed`() {
        // The pull that opens the app list starts in the same corner. Downward has to reach it.
        val d = drag()
        d.onDown()
        assertFalse(d.onMove(60f))
        assertFalse(d.onMove(60f))
    }

    @Test
    fun `a long slow upward drag still reaches the swipe`() {
        // Many small frames past the slop in total is the same gesture as one big one; only the
        // sum matters, so no frame size is special.
        val d = drag()
        d.onDown()
        var consumed = false
        repeat(20) { consumed = d.onMove(-5f) || consumed }
        assertTrue(consumed)
        assertTrue(d.isSwipe())
    }
}