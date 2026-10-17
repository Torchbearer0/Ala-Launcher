// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

enum class PaddingSlot {
    WIDGET_TOP,
    WIDGET_BOTTOM,
    FAVORITES_TOP,
    FAVORITES_BOTTOM,
}

/**
 * Per-block top/bottom spacing on the home screen, in dp.
 *
 * The two Now Playing slots are gone with the block that used them: a slot nothing can
 * select is a setting nobody can find, and a default that is never read is worse than no
 * default at all.
 */
data class HomePaddings(
    val widgetTop: Int,
    val widgetBottom: Int,
    val favoritesTop: Int,
    val favoritesBottom: Int,
) {
    operator fun get(slot: PaddingSlot): Int = when (slot) {
        PaddingSlot.WIDGET_TOP -> widgetTop
        PaddingSlot.WIDGET_BOTTOM -> widgetBottom
        PaddingSlot.FAVORITES_TOP -> favoritesTop
        PaddingSlot.FAVORITES_BOTTOM -> favoritesBottom
    }

    companion object {
        val Default = HomePaddings(8, 8, 8, 24)
    }
}