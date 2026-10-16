// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The age beside an app name. Boundaries are the whole of it — each of these is a place where
 * a plausible-looking implementation rounds the wrong way and prints something absurd.
 */
class RelativeTimeTest {

    private val now = 1_700_000_000_000L
    private fun agoAgo(millis: Long) = RelativeTime.ago(now - millis, now)

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    @Test
    fun `anything under a minute reads as now`() {
        assertEquals("now", agoAgo(0))
        assertEquals("now", agoAgo(999))
        assertEquals("now", agoAgo(59_999))
    }

    @Test
    fun `minutes`() {
        assertEquals("1m", agoAgo(minute))
        assertEquals("20m", agoAgo(20 * minute))
        assertEquals("59m", agoAgo(59 * minute + 59_999))
    }

    @Test
    fun `hours`() {
        assertEquals("1h", agoAgo(hour))
        assertEquals("2h", agoAgo(2 * hour))
        assertEquals("23h", agoAgo(23 * hour + hour - 1))
    }

    @Test
    fun `days`() {
        assertEquals("1d", agoAgo(day))
        assertEquals("6d", agoAgo(6 * day))
    }

    /** The boundary that decides which unit is used, checked from both sides. */
    @Test
    fun `an hour is an hour and not sixty minutes`() {
        assertEquals("59m", agoAgo(hour - 1))
        assertEquals("1h", agoAgo(hour))
    }

    @Test
    fun `a day is a day and not twenty four hours`() {
        assertEquals("23h", agoAgo(day - 1))
        assertEquals("1d", agoAgo(day))
    }

    /**
     * A notification posted by an app whose clock is ahead, or one that arrived across a clock
     * correction, is stamped in the future. A negative age would be pointing at a bug that is
     * not there.
     */
    @Test
    fun `a time from the future reads as now`() {
        assertEquals("now", RelativeTime.ago(now + minute, now))
        assertEquals("now", RelativeTime.ago(now + day, now))
    }

    @Test
    fun `an old notification stops counting days`() {
        assertEquals("a while", agoAgo(30 * day))
        assertEquals("a while", agoAgo(400 * day))
    }
}