// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a held widget lands when the finger is somewhere on the page.
 *
 * The arithmetic itself lives in [WidgetReorder] in the main source set, because the page uses
 * it; these are the cases that pin it down.
 *
 * So there is nothing here about collision, snapping or empty space, because this model cannot
 * be in a state where those mean anything.
 */
class WidgetReorderTest {

    /** Four entries stacked evenly, 200px apart, each 100px tall. Centres at 50, 250, 450, 650. */
    private fun stacked(count: Int, top: Float = 0f, pitch: Float = 200f): (Int) -> Float? =
        { index -> if (index in 0 until count) top + index * pitch + 50f else null }

    @Test
    fun `a single entry cannot be moved`() {
        assertEquals(0, WidgetReorder.targetIndex(0, stacked(1), fingerY = 400f, lastIndex = 0))
    }

    @Test
    fun `dragging the top entry down past its neighbour swaps them`() {
        // Finger at 300 is below entry 1's centre of 250, above entry 2's of 450.
        assertEquals(1, WidgetReorder.targetIndex(0, stacked(4), fingerY = 300f, lastIndex = 3))
    }

    @Test
    fun `dragging the top entry to the very bottom`() {
        assertEquals(3, WidgetReorder.targetIndex(0, stacked(4), fingerY = 900f, lastIndex = 3))
    }

    @Test
    fun `dragging the bottom entry up to the very top`() {
        assertEquals(0, WidgetReorder.targetIndex(3, stacked(4), fingerY = 10f, lastIndex = 3))
    }

    @Test
    fun `dragging the bottom entry up past its neighbour swaps them`() {
        // Entries 0 (50), 1 (250) and 2 (450) are above a finger at 500.
        assertEquals(3, WidgetReorder.targetIndex(3, stacked(4), fingerY = 500f, lastIndex = 3))
    }

    @Test
    fun `the held entry is never counted as one of the ones it is passing`() {
        // Held at 1. A finger at 300 has entry 0 above it and entry 2 below. If the held entry
        // counted itself the answer would be 2 and every drag would skip a place.
        assertEquals(1, WidgetReorder.targetIndex(1, stacked(4), fingerY = 300f, lastIndex = 3))
    }

    @Test
    fun `the target moves continuously as the finger moves`() {
        // Centres are 250, 450 and 650 for the three the held entry is carried past.
        val expected = listOf(0, 0, 0, 1, 1, 2, 2, 3, 3)
        val finger = listOf(20f, 180f, 220f, 380f, 420f, 580f, 620f, 700f, 900f)
        val actual = finger.map { WidgetReorder.targetIndex(0, stacked(4), it, lastIndex = 3) }
        assertEquals(expected, actual)
    }

    @Test
    fun `the target never falls outside the list however far the finger goes`() {
        val centres = stacked(4)
        assertEquals(3, WidgetReorder.targetIndex(0, centres, 100_000f, lastIndex = 3))
        assertEquals(0, WidgetReorder.targetIndex(3, centres, -100_000f, lastIndex = 3))
    }

    @Test
    fun `an entry whose position is not known yet sorts to the end, not the top`() {
        val centres: (Int) -> Float? = { index -> if (index == 0) 50f else null }
        assertEquals(0, WidgetReorder.targetIndex(0, centres, fingerY = 10f, lastIndex = 2))
    }

    @Test
    fun `moving down takes the neighbour's place and pushes it along`() {
        // "the bottom one comes down" is this: the list is reordered, not drawn over.
        val list = listOf("a", "b", "c")
        assertEquals(listOf("a", "c", "b"), WidgetReorder.move(list, 1, 2))
    }

    @Test
    fun `moving up puts it in front and the other one goes down`() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), WidgetReorder.move(list, 1, 0))
    }

    @Test
    fun `a drag that crosses back and forth ends where the finger ended`() {
        // Every intermediate sample is applied, so the entry tracks the finger instead of
        // being left where a swap happened to put it.
        //
        // Centres are fixed at 50/250/450/650 for all five samples, which is the worst case for
        // the function: in a real list the entries move as the held one does. Traced by hand:
        //   y=300 from 0 -> 1   [b,a,c,d]
        //   y=100 from 1 -> 1   no change, already past entry 0's centre
        //   y=700 from 1 -> 3   [b,c,d,a]
        //   y=450 from 3 -> 2   [b,c,a,d]
        //   y=300 from 2 -> 2   no change
        var list = listOf("a", "b", "c", "d")
        var held = 0
        listOf(300f, 100f, 700f, 450f, 300f).forEach { y ->
            val to = WidgetReorder.targetIndex(held, { i -> 50f + i * 200f }, y, lastIndex = 3)
            list = WidgetReorder.move(list, held, to)
            held = to
        }
        assertEquals(listOf("b", "c", "a", "d"), list)
        assertEquals(2, held)
    }

    @Test
    fun `moving to the same place changes nothing`() {
        val list = listOf("a", "b", "c")
        assertEquals(list, WidgetReorder.move(list, 1, 1))
    }

    @Test
    fun `an out of range move is refused rather than throwing`() {
        val list = listOf("a", "b")
        assertEquals(list, WidgetReorder.move(list, 5, 0))
        assertEquals(list, WidgetReorder.move(list, 0, 9))
    }

    @Test
    fun `no matter how it is moved the list keeps every entry exactly once`() {
        // Three moves that happen to compose back to the order they started in, which makes
        // this the strongest of the move tests: nothing is dropped, duplicated or corrupted
        // even when the entry is carried to both ends and back.
        //   0 -> 4   [b,c,d,e,a]
        //   4 -> 2   [b,c,a,d,e]
        //   2 -> 0   [a,b,c,d,e]
        var list = listOf("a", "b", "c", "d", "e")
        list = WidgetReorder.move(list, 0, 4)
        assertEquals("after 0 -> 4", listOf("b", "c", "d", "e", "a"), list)
        list = WidgetReorder.move(list, 4, 2)
        assertEquals("after 4 -> 2", listOf("b", "c", "a", "d", "e"), list)
        list = WidgetReorder.move(list, 2, 0)
        assertEquals("after 2 -> 0", listOf("a", "b", "c", "d", "e"), list)
        assertEquals(listOf("a", "b", "c", "d", "e").sorted(), list.sorted())
        assertTrue(list.size == 5)
    }
}
