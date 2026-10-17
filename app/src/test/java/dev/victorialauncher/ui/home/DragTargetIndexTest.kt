// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where a held widget on the page lands when the finger is somewhere.
 *
 * The page rearranges against the finger's real position rather than by a fixed step, so that
 * the other entries move out of the way as it passes them. What makes that feel right is
 * entirely in this function, and none of it is visible to a widget host or a layout pass -
 * which is the reason it is here and pulled out as pure.
 */
class DragTargetIndexTest {

    /** Four entries stacked evenly, 200px apart, each 100px tall. */
    private fun stacked(count: Int, top: Float = 0f, pitch: Float = 200f): (Int) -> Float? =
        { index -> if (index in 0 until count) top + index * pitch + 50f else null }

    @Test
    fun `a single entry stays where it is`() {
        assertEquals(0, dragTargetIndex(0, stacked(1), rootY = 400f, lastIndex = 0))
    }

    @Test
    fun `holding the top entry and staying above the rest keeps it at the top`() {
        // Finger at 100: entry 1's centre is 250, entry 0 is held, so nothing is above.
        assertEquals(0, dragTargetIndex(0, stacked(3), rootY = 100f, lastIndex = 2))
    }

    @Test
    fun `dragging the top entry past its neighbour moves it down one place`() {
        // Finger at 300: entry 1 (centre 250) is above it, entry 2 (centre 450) is not.
        assertEquals(1, dragTargetIndex(0, stacked(3), rootY = 300f, lastIndex = 2))
    }

    @Test
    fun `dragging the top entry to the very bottom of the stack`() {
        assertEquals(2, dragTargetIndex(0, stacked(3), rootY = 900f, lastIndex = 2))
    }

    @Test
    fun `dragging the bottom entry up to the top`() {
        // Finger at 10: nothing is above it.
        assertEquals(0, dragTargetIndex(2, stacked(3), rootY = 10f, lastIndex = 2))
    }

    @Test
    fun `dragging the bottom entry above its neighbour moves it up one place`() {
        // Finger at 300: entries 0 (50) and 1 (250) are above it, entry 2 is held.
        assertEquals(2, dragTargetIndex(2, stacked(3), rootY = 300f, lastIndex = 2))
    }

    @Test
    fun `the held entry is never counted as one of the ones it is passing`() {
        // Held index 1 sitting at 250. A finger at 300 is below it, so entry 0 (50) only is
        // above. If the held entry counted itself the answer would be 2 and every drag would
        // skip a place.
        assertEquals(1, dragTargetIndex(1, stacked(3), rootY = 300f, lastIndex = 2))
    }

    @Test
    fun `the target is continuous, so the stack follows the finger back the other way`() {
        // Centres are 250, 450 and 650 for the three entries the held one is being carried
        // past, so the held entry changes place exactly when the finger passes each of those.
        val centres = stacked(4)
        val expected = listOf(0, 0, 0, 1, 1, 2, 2, 3, 3)
        val finger = listOf(20f, 180f, 220f, 380f, 420f, 580f, 620f, 700f, 900f)
        val actual = finger.map { dragTargetIndex(0, centres, rootY = it, lastIndex = 3) }
        assertEquals(expected, actual)
    }

    @Test
    fun `an entry whose position is not known yet sorts to the end, not the top`() {
        // Entry 1 has not been laid out. Holding entry 0 and dropping at the top must not
        // throw entry 0 above it and then above everything else.
        val centres: (Int) -> Float? = { index -> if (index == 0) 50f else null }
        assertEquals(0, dragTargetIndex(0, centres, rootY = 10f, lastIndex = 2))
    }

    @Test
    fun `an empty list cannot reorder anything`() {
        val centres: (Int) -> Float? = { null }
        assertEquals(0, dragTargetIndex(0, centres, rootY = 100f, lastIndex = 0))
    }

    @Test
    fun `the target never falls outside the list however far the finger goes`() {
        val centres = stacked(3)
        assertEquals(2, dragTargetIndex(0, centres, rootY = 100_000f, lastIndex = 2))
        assertEquals(0, dragTargetIndex(2, centres, rootY = -100_000f, lastIndex = 2))
    }
}
