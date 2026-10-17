// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page to the left of home is stored as JSON in a preference, which means it is read back
 * long after it was written and on a different version of the code that wrote it.
 */
class MinusOneEntryTest {

    @Test
    fun emptyAndUnreadableInputGiveAnEmptyPage() {
        assertEquals(emptyList<MinusOneEntry>(), minusOneEntriesFromJson(null))
        assertEquals(emptyList<MinusOneEntry>(), minusOneEntriesFromJson(""))
        assertEquals(emptyList<MinusOneEntry>(), minusOneEntriesFromJson("   "))
        // A truncated write, or a value from a future version that this one cannot read.
        assertEquals(emptyList<MinusOneEntry>(), minusOneEntriesFromJson("[{\"kind\":\"widget\""))
        assertEquals(emptyList<MinusOneEntry>(), minusOneEntriesFromJson("not json at all"))
    }

    @Test
    fun widgetsAndShortcutsSurviveARoundTrip() {
        val entries = listOf(
            MinusOneEntry.Widget(widgetId = 41, heightDp = 180),
            MinusOneEntry.Shortcut(appKey = "com.example/com.example.Main", heightDp = 72),
            MinusOneEntry.Widget(widgetId = 42, heightDp = 240),
        )
        assertEquals(entries, minusOneEntriesFromJson(minusOneEntriesToJson(entries)))
    }

    @Test
    fun anEmptyStackRoundTripsToAnEmptyStack() {
        assertEquals(emptyList<MinusOneEntry>(), minusOneEntriesFromJson(minusOneEntriesToJson(emptyList())))
    }

    @Test
    fun heightsAreClampedRatherThanRejected() {
        // A widget taller than the screen is a setting that was dragged too far, not a corrupt
        // value. Dropping the entry would take the widget off the page entirely, so the height
        // is pulled back into range and the entry is kept.
        val json = """[{"kind":"widget","id":7,"h":100000}]"""
        val entry = minusOneEntriesFromJson(json).single()
        assertEquals(MINUS_ONE_MAX_HEIGHT_DP, entry.heightDp)
    }

    @Test
    fun aMissingHeightFallsBackToTheDefaultRatherThanZero() {
        val entry = minusOneEntriesFromJson("""[{"kind":"app","key":"com.example/.Main"}]""").single()
        assertEquals(180, entry.heightDp)
    }

    @Test
    fun anEntryThatCannotBeDrawnIsDropped() {
        // A widget with no id cannot be bound to anything, and a shortcut with no key names no
        // app. Either would sit on the page as a permanent blank.
        val json = """[{"kind":"widget","id":-1},{"kind":"app"},{"kind":"nonsense"}]"""
        assertEquals(emptyList<MinusOneEntry>(), minusOneEntriesFromJson(json))
    }

    @Test
    fun orderIsPreservedBecauseItIsTheOrderOnScreen() {
        val entries = (1..5).map { MinusOneEntry.Widget(it, 100 + it) }
        assertEquals(entries, minusOneEntriesFromJson(minusOneEntriesToJson(entries)))
    }

    @Test
    fun resizingIsClampedToWhatIsDrawable() {
        val widget = MinusOneEntry.Widget(widgetId = 3, heightDp = 180)
        assertEquals(MINUS_ONE_MIN_HEIGHT_DP, widget.withHeight(0).heightDp)
        assertEquals(MINUS_ONE_MAX_HEIGHT_DP, widget.withHeight(99_999).heightDp)
        assertEquals(200, widget.withHeight(200).heightDp)

        val shortcut = MinusOneEntry.Shortcut(appKey = "a/b", heightDp = 180)
        assertEquals(MINUS_ONE_MIN_HEIGHT_DP, shortcut.withHeight(-40).heightDp)
    }

    @Test
    fun aWidgetWhoseIdIsGoneIsPrunedWithoutDisturbingTheRest() {
        val page = MinusOnePage(
            mode = MinusOneMode.WIDGETS,
            entries = listOf(
                MinusOneEntry.Widget(1, 180),
                MinusOneEntry.Shortcut("a/b", 72),
                MinusOneEntry.Widget(9, 180),
            ),
        )
        val pruned = page.withoutDeadWidgets(live = setOf(1, 2))
        assertEquals(listOf(MinusOneEntry.Widget(1, 180), MinusOneEntry.Shortcut("a/b", 72)), pruned.entries)
        assertEquals(MinusOneMode.WIDGETS, pruned.mode)
    }

    @Test
    fun anOffPageIsTheDefaultAndHoldsNothing() {
        assertEquals(MinusOneMode.OFF, MinusOnePage().mode)
        assertTrue(MinusOnePage().entries.isEmpty())
    }
}