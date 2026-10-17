// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a widget dropped on the page is allowed to land.
 *
 * A position is absolute, so without a rule about what it may land on, a widget can be dropped
 * straight through another and leave two of them drawn over each other. These pin the rule:
 * the dropped position is a wish, and the answer is the nearest free place to it.
 */
class ResolveDropYTest {

    private val pageHeight = 1000
    private val gap = 8

    /** Nothing else on the page: the wish is honoured exactly. */
    @Test
    fun `an empty page takes the drop exactly where it was aimed`() {
        assertEquals(400, resolveDropY(400, 200, emptyList(), pageHeight, gap))
    }

    @Test
    fun `a drop onto an occupied band moves to just past it`() {
        // Aiming at 400 with something sitting 100..499: the entry would span 400..599 and run
        // straight into it, so it goes under, to 499 + 1 + the 8dp gap.
        val y = resolveDropY(400, 200, listOf(100 until 500), pageHeight, gap)
        assertEquals(508, y)
    }

    @Test
    fun `two occupied bands leave the drop below both of them`() {
        // Aiming into the first of two neighbours. Just past the first is still inside the
        // second, so it has to clear that too.
        val y = resolveDropY(250, 180, listOf(200 until 350, 400 until 550), pageHeight, gap)
        assertEquals(558, y)
    }

    @Test
    fun `prefers the gap below over the gap above when both are free`() {
        // Aiming just above a neighbour at 400..599. Going up to 192 is nearer to the wish,
        // but the finger was travelling down and "make room" means landing underneath - so the
        // nearest rule is deliberately not the rule here.
        val y = resolveDropY(380, 200, listOf(400 until 600), pageHeight, gap)
        assertEquals(608, y)
    }

    @Test
    fun `a drop that just misses an entry is left alone`() {
        // Ends at 300, the entry starts at 308: the gap of 8 is exactly enough.
        val y = resolveDropY(100, 200, listOf(308 until 500), pageHeight, gap)
        assertEquals(100, y)
    }

    @Test
    fun `a drop that overlaps by a single dp is refused`() {
        // Ends at 300, the entry starts at 301: one dp short of the gap, so it moves.
        val y = resolveDropY(100, 200, listOf(301 until 500), pageHeight, gap)
        assertEquals(508, y)
    }

    @Test
    fun `an entry can never land off the bottom of the page`() {
        val y = resolveDropY(5000, 200, emptyList(), pageHeight, gap)
        assertEquals(800, y)
    }

    @Test
    fun `an entry taller than the page is pinned to the top rather than clamped negative`() {
        assertEquals(0, resolveDropY(50, 1400, emptyList(), pageHeight, gap))
    }

    @Test
    fun `a drop never ends up overlapping anything when there is room`() {
        val occupied = listOf(100 until 300, 320 until 520)
        val height = 180
        val y = resolveDropY(340, height, occupied, pageHeight, gap)
        occupied.forEach { band ->
            assertTrue(
                "landed at $y overlapping $band",
                !(y < band.last + gap && band.first < y + height + gap),
            )
        }
    }

    @Test
    fun `always lands somewhere on the page, even with nowhere free`() {
        // The page packed solid: there is genuinely no free band left, so the resolver cannot
        // honour the rule, and the documented answer is the wish clamped to the page. It has to
        // be a position rather than a crash or a null - something has to give, and hiding the
        // widget off-screen would be worse than showing it on top of another.
        val occupied = listOf(0 until 800, 808 until 1000)
        val y = resolveDropY(400, 200, occupied, pageHeight, gap)
        assertTrue(y in 0..800)
    }
}
