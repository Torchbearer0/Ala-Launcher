// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * What the page to the left of the home screen shows.
 *
 * Off is the default and not merely a placeholder: a page that appears on its own would take a
 * swipe away from the home screen for anyone who did not ask for one.
 */
enum class MinusOneMode {
    OFF,

    /** One line of widgets and app shortcuts, each with a size of its own. */
    WIDGETS,
}

/**
 * One thing on the widget page, and how big it is.
 *
 * **The whole placement model is this and the order of the list.** That is not a simplification
 * or a first attempt - it is the shape Flow Launcher itself uses, read out of
 * `com.app.flowlauncher.widgets.WidgetModel` in Flow 40.1.6 (`com.app.flowlauncher`):
 *
 *     data class WidgetModel(
 *         val widgetId: Int,
 *         val dimensionsInLine: Int?,   // width in cells; null = default
 *         val habitId: Long = -1,
 *     )
 *
 * Searching that package for row, column, page, x, y, left, top or cellIndex returns nothing.
 * There are no coordinates anywhere. A widget's place is its **index in the list**, and the only
 * thing a user can change about it is how wide it is.
 *
 * This was arrived at backwards. The first version of this page stored an absolute position per
 * entry and resolved drops against it, which is a layout engine with a collision problem, and it
 * was buggy in six separate ways before it was removed. Flow has none of those because none of
 * them can be expressed. Overlap is not a state this model can enter, so there is no overlap
 * bug; packing is automatic, so there is nothing to place.
 *
 * Heights are per entry rather than one value for the page, so resizing a clock does not resize
 * the list under it. Width 0 means "whatever is left of the row".
 */
sealed interface MinusOneEntry {
    val heightDp: Int

    /**
     * How wide this entry is, or 0 to mean "whatever is left of the row".
     *
     * A dp figure rather than a fraction because a widget sizes itself against the width it is
     * told in dp and the two have to agree; a fraction would have to be converted back at every
     * layout, and a widget laid out for one width and measured at another clips its text. 0 is
     * the fill case and is what an entry written before width was resizable gets, so nothing
     * existing changes shape on upgrade.
     */
    val widthDp: Int

    /** An app widget, held by id in the same host the home screen's widget uses. */
    data class Widget(
        val widgetId: Int,
        override val heightDp: Int,
        override val widthDp: Int = 0,
    ) : MinusOneEntry

    /** A launch shortcut for an app, by the same key the app list and favorites use. */
    data class Shortcut(
        val appKey: String,
        override val heightDp: Int,
        override val widthDp: Int = 0,
    ) : MinusOneEntry
}

/**
 * The page to the left of home, as one value.
 *
 * Held together so the mode and what it holds cannot be read out of step with each other: two
 * separate reads mean a frame where the page is already in widget mode but the stack it should
 * draw has not arrived yet.
 */
data class MinusOnePage(
    val mode: MinusOneMode = MinusOneMode.OFF,
    val entries: List<MinusOneEntry> = emptyList(),
) {
    /** Entries with a widget that is no longer allocated, which a removed widget leaves behind. */
    fun withoutDeadWidgets(live: Set<Int>): MinusOnePage =
        copy(entries = entries.filterNot { it is MinusOneEntry.Widget && it.widgetId !in live })
}

/** Tall enough for a clock face, short enough that two of them still leave room for a row. */
const val MINUS_ONE_MIN_HEIGHT_DP = 56

/** Past this a single entry stops being a page and starts being the whole screen. */
const val MINUS_ONE_MAX_HEIGHT_DP = 640

/** Wide enough to be the whole page on any phone; past it there is nothing beside it to fit. */
const val MINUS_ONE_MAX_WIDTH_DP = 900

/**
 * What a freshly added widget gets.
 *
 * The home screen's own default, so a widget looks the same size as it did there, and large
 * enough that a clock reads at a glance. Anyone who wants it different drags the divider.
 */
const val MINUS_ONE_NEW_WIDGET_HEIGHT_DP = 180

fun MinusOneEntry.withHeight(dp: Int): MinusOneEntry = when (this) {
    is MinusOneEntry.Widget -> copy(heightDp = dp.coerceIn(MINUS_ONE_MIN_HEIGHT_DP, MINUS_ONE_MAX_HEIGHT_DP))
    is MinusOneEntry.Shortcut -> copy(heightDp = dp.coerceIn(MINUS_ONE_MIN_HEIGHT_DP, MINUS_ONE_MAX_HEIGHT_DP))
}

/**
 * A width of 0 fills the row, so a floor below it would make "fill" unreachable and leave the
 * entry with a width of 1dp.
 */
fun MinusOneEntry.withWidth(dp: Int): MinusOneEntry = when (this) {
    is MinusOneEntry.Widget -> copy(widthDp = dp.coerceIn(0, MINUS_ONE_MAX_WIDTH_DP))
    is MinusOneEntry.Shortcut -> copy(widthDp = dp.coerceIn(0, MINUS_ONE_MAX_WIDTH_DP))
}

fun MinusOneEntry.withSize(widthDp: Int, heightDp: Int): MinusOneEntry = withWidth(widthDp).withHeight(heightDp)

internal fun minusOneEntriesToJson(entries: List<MinusOneEntry>): String {
    val array = JSONArray()
    entries.forEach { entry ->
        array.put(
            JSONObject().apply {
                put("h", entry.heightDp)
                // Only written when it is a real width: a full-width entry is the default, and
                // storing a zero would mean every future read has to know that means "fill".
                if (entry.widthDp > 0) put("w", entry.widthDp)
                when (entry) {
                    is MinusOneEntry.Widget -> {
                        put("kind", "widget")
                        put("id", entry.widgetId)
                    }
                    is MinusOneEntry.Shortcut -> {
                        put("kind", "app")
                        put("key", entry.appKey)
                    }
                }
            }
        )
    }
    return array.toString()
}

internal fun minusOneEntriesFromJson(json: String?): List<MinusOneEntry> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val height = obj.optInt("h", 180).coerceIn(MINUS_ONE_MIN_HEIGHT_DP, MINUS_ONE_MAX_HEIGHT_DP)
            // Absent means full width, which is what every entry stored before this was
            // resizable sideways should keep rather than come back a column narrow.
            val width = obj.optInt("w", 0).coerceIn(0, MINUS_ONE_MAX_WIDTH_DP)
            when (obj.optString("kind")) {
                "widget" -> MinusOneEntry.Widget(widgetId = obj.optInt("id", -1), heightDp = height, widthDp = width)
                    // A widget with no id cannot be bound, so it is dropped rather than drawn as
                    // an empty box that never fills in.
                    .takeIf { it.widgetId > 0 }
                "app" -> obj.optString("key").takeIf { it.isNotBlank() }
                    ?.let { MinusOneEntry.Shortcut(appKey = it, heightDp = height, widthDp = width) }
                else -> null
            }
        }
    }.getOrDefault(emptyList())
}