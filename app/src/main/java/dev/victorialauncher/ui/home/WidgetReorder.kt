// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

/**
 * Where a held widget lands when the finger is somewhere, and what the list is afterwards.
 *
 * This is the whole of arranging. It is deliberately this small, because the placement model it
 * operates on is deliberately small.
 *
 * Flow Launcher's widget model (`com.app.flowlauncher.widgets.WidgetModel`, read out of Flow
 * 40.1.6) is an ordered list plus one size per entry:
 *
 *     data class WidgetModel(widgetId: Int, dimensionsInLine: Int?, habitId: Long = -1)
 *
 * Searching that package for row, column, page, x, y, left, top or cellIndex returns nothing.
 * There are no coordinates, no page and no cell. A widget's place is its index in the list, so a
 * widget that moves up and its neighbour that comes down are not two pieces of behaviour - they
 * are one list reordered, and the neighbour is not told to do anything. It simply is no longer
 * in that slot.
 *
 * That is why there is nothing here about collision, snapping or empty space: this model cannot
 * be in a state where any of those mean anything. The previous version of the page stored an
 * absolute position per entry and resolved drops against it, and every one of its faults lived
 * in that machinery rather than in this arithmetic.
 */
object WidgetReorder {

    /**
     * The index a held entry should take, given where the finger is.
     *
     * Counted from the neighbours' centres rather than from a fixed distance, because a drag is
     * a continuous thing and the entries are different heights: a fixed step is twitchy on a
     * short entry and sluggish on a tall one. The finger is simply over whatever it is over.
     *
     * The held entry is lifted out of the count first. Counting itself would mean every drag
     * moved two places rather than one - which is exactly the bug the earlier version had.
     *
     * An entry whose position is not known yet sorts to the end, so a drag that begins before
     * the list has finished laying out cannot throw the entry to the top of the page.
     */
    fun targetIndex(held: Int, centreY: (Int) -> Float?, fingerY: Float, lastIndex: Int): Int {
        if (lastIndex < 1) return held
        val above = (0..lastIndex).count { it != held && (centreY(it) ?: Float.MAX_VALUE) < fingerY }
        return above.coerceIn(0, lastIndex)
    }

    /**
     * The list with one entry moved.
     *
     * A move rather than a swap. For a single entry they look identical, but a swap undoes
     * itself the moment the finger comes back over the same neighbour - so a drag that
     * oscillates would leave the widget wherever the last sample happened to land rather than
     * where it was released.
     */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }
}
