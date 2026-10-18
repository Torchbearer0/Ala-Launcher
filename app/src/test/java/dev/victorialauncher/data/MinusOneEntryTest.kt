// SPDX-License-Identifier: GPL-3.0-or-later
package dev.alalauncher.data

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
    fun widthIsClampedButZeroStillMeansFullWidth() {
        val widget = MinusOneEntry.Widget(widgetId = 3, heightDp = 180)
        // 0 is the fill case, so a negative width clamps to 0 rather than to a 1dp sliver.
        assertEquals(0, widget.withWidth(-40).widthDp)
        assertEquals(MINUS_ONE_MAX_WIDTH_DP, widget.withWidth(99_999).widthDp)
        assertEquals(180, widget.withWidth(180).widthDp)
    }

    @Test
    fun resizingBothAxesAtOnceLeavesTheOtherAlone() {
        val widget = MinusOneEntry.Widget(widgetId = 3, heightDp = 180, widthDp = 200)
        val resized = widget.withSize(widthDp = 300, heightDp = 400) as MinusOneEntry.Widget
        assertEquals(300, resized.widthDp)
        assertEquals(400, resized.heightDp)
        assertEquals(3, resized.widgetId)
    }

    @Test
    fun aStoredEntryWithNoWidthComesBackFullWidth() {
        // Written before entries were resizable sideways. It has no width at all, and the only
        // correct reading of that is "as wide as the page", not "as wide as it can be".
        val entry = minusOneEntriesFromJson("""[{"kind":"widget","id":7,"h":180}]""").single()
        assertEquals(0, entry.widthDp)
    }

    @Test
    fun sideBySideEntriesSurviveARoundTrip() {
        val entries = listOf(
            MinusOneEntry.Widget(1, heightDp = 180, widthDp = 180),
            MinusOneEntry.Widget(2, heightDp = 180, widthDp = 180),
            MinusOneEntry.Widget(3, heightDp = 300),
        )
        assertEquals(entries, minusOneEntriesFromJson(minusOneEntriesToJson(entries)))
    }

    @Test
    fun aFullWidthEntryIsNotWrittenOut() {
        // 0 is the default, so storing it would mean every future read has to know that zero
        // means "fill" rather than "nothing".
        val json = minusOneEntriesToJson(listOf(MinusOneEntry.Widget(1, heightDp = 180)))
        assertTrue(!json.contains("\"w\""))
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