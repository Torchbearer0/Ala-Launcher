// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import kotlin.math.max

/**
 * Where a page with entries on it still has room for another one.
 *
 * The page is an ordered list packed left to right and wrapped, which is the same thing a
 * [androidx.compose.foundation.layout.FlowRow] does. That leaves a gap whenever a line of entries
 * does not come to the full width - and a gap is not a decoration, it is the only reason free
 * resizing is worth having. Resizing a widget down is pointless if the space it gives up cannot be
 * given to anything else, and the page had exactly that problem: the gap was visible and
 * unreachable, because the only entry that could have gone in it was the next one in the list, at
 * whatever width it was already.
 *
 * So the add button fills the first gap it finds, sizing the new entry to fit it exactly.
 *
 * ## Why this is arithmetic and not layout
 *
 * This mirrors FlowRow's own line breaking rather than asking the row where anything landed. The
 * sizes are already known - each entry stores its own width and height - and the row's width is
 * measured once. Recomputing the same decision is what keeps the page free of the machinery that
 * used to break it: there is no coordinate to store, no collision to resolve, and no view to keep
 * in sync, because the list and this function are the only two things that decide where anything
 * goes.
 *
 * A full-width entry stores its width as 0 to mean "fill", which is a word rather than a number.
 * Here it is resolved to the whole line, which is also why a full-width entry can never leave a
 * gap after it: it takes everything that was left.
 */
object WidgetPageFill {

    /** One entry's size as the row sees it: the width it takes, and the height of its line. */
    data class Slot(val widthDp: Int, val heightDp: Int)

    /**
     * The first place on the page wide enough to be worth putting another entry into.
     *
     * [insertIndex] is where the new entry goes in the list, which puts it in that gap without any
     * of the entries after it moving. [widthDp] and [heightDp] are the size it should be created
     * at, so it fills the space rather than merely fitting inside it.
     *
     * Only gaps at least [minWidthDp] wide are returned. A widget narrower than that is not a
     * widget; it is a strip that renders its own contents unreadable. Skipping a gap that is too
     * narrow and taking the next one is what makes the button always do something sensible.
     *
     * Null means the entries exactly fill their lines, and the next widget goes on as a new line.
     */
    fun firstGap(entries: List<Slot>, containerDp: Int, gapDp: Int, minWidthDp: Int): Gap? {
        if (entries.isEmpty() || containerDp <= 0) return null

        var lineWidth = 0
        var lineHeight = 0

        for ((index, entry) in entries.withIndex()) {
            // 0 means fill, and filling takes the whole line whatever is left on it.
            val width = if (entry.widthDp > 0) entry.widthDp else containerDp

            // The same test FlowRow makes: does this entry still fit on the line, counting the
            // gap that goes between two items but not before the first.
            if (lineWidth > 0 && lineWidth + gapDp + width > containerDp) {
                val gap = gapOn(lineWidth, lineHeight, index, containerDp, gapDp, minWidthDp)
                if (gap != null) return gap
                // Too narrow to be useful. Close the line and start the next one.
                lineWidth = 0
                lineHeight = 0
            }

            lineWidth += (if (lineWidth > 0) gapDp else 0) + width
            lineHeight = max(lineHeight, entry.heightDp)
        }

        // The last line, which the loop above never gets to close because nothing follows it.
        //
        // This is the one that matters most and it is the easy one to leave out: a single widget
        // narrowed to half the page has nothing after it, so the only place its gap can be noticed
        // is here. Missing it meant the page could not be filled in the single most obvious case -
        // the one a user reaches for after resizing.
        return gapOn(lineWidth, lineHeight, entries.size, containerDp, gapDp, minWidthDp)
    }

    /**
     * The gap left by a line, or null when the line is full or what is left is too narrow.
     *
     * The inter-item gap comes off before the answer is given, and that is the whole of it. A
     * widget that fills the space still has to be *placed* there, and being placed costs the same
     * spacing between two things as anything else - so the usable width is what is left of the
     * line **less one gap**, not what is left of it.
     *
     * Getting that wrong is invisible until a device measures it: the page reported 171dp free in
     * a 422dp row holding a 251dp widget, offered 171dp, and then could not lay it out - because
     * 251 + 8 + 171 is 430. The caption and the arithmetic agreed with each other and both were
     * 8dp wrong, so nothing that compared them caught it.
     */
    private fun gapOn(
        lineWidth: Int,
        lineHeight: Int,
        insertIndex: Int,
        containerDp: Int,
        gapDp: Int,
        minWidthDp: Int,
    ): Gap? {
        val leftover = containerDp - lineWidth - gapDp
        if (leftover < minWidthDp) return null
        return Gap(insertIndex = insertIndex, widthDp = leftover, heightDp = lineHeight)
    }

    /**
     * A place on the page wide enough to be worth putting another entry into.
     *
     * [insertIndex] is where in the list it goes, and the two sizes are what it should be created
     * at so that it fills the gap rather than leaving more of it.
     */
    data class Gap(val insertIndex: Int, val widthDp: Int, val heightDp: Int)
}

/** Narrower than this and it is not a widget, it is an unreadable strip. */
const val MIN_FILLABLE_WIDTH_DP = 140

/** The gap between two entries on the same line. Mirrors `ENTRY_GAP_DP` in MinusOnePage. */
const val ENTRY_LINE_GAP_DP = 8