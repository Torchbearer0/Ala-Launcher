// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import dev.victorialauncher.widget.ClockWidgetWeather.WeatherGlyph
import dev.victorialauncher.widget.ClockWidgetWeather.glyphFor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The condition text comes from whichever weather app chose to send it, so the matcher is the
 * only thing standing between an odd wording and a confidently wrong sky drawn on the home
 * screen. These are the wordings that decide which.
 */
class WeatherGlyphTest {

    private fun glyph(condition: String) = glyphFor(condition)

    @Test
    fun `clear skies are clear`() {
        assertEquals(WeatherGlyph.CLEAR, glyph("Clear"))
        assertEquals(WeatherGlyph.CLEAR, glyph("Sunny"))
        assertEquals(WeatherGlyph.CLEAR, glyph("Fair"))
    }

    @Test
    fun `a cloud word alone is overcast`() {
        assertEquals(WeatherGlyph.CLOUDY, glyph("Cloudy"))
        assertEquals(WeatherGlyph.CLOUDY, glyph("Overcast"))
    }

    @Test
    fun `hedged cloud is partly cloudy, not cloudy`() {
        assertEquals(WeatherGlyph.PARTLY_CLOUDY, glyph("Partly cloudy"))
        assertEquals(WeatherGlyph.PARTLY_CLOUDY, glyph("Mostly cloudy"))
        assertEquals(WeatherGlyph.PARTLY_CLOUDY, glyph("Scattered clouds"))
        assertEquals(WeatherGlyph.PARTLY_CLOUDY, glyph("Broken clouds"))
    }

    @Test
    fun `haze and mist are fog, not cloud`() {
        assertEquals(WeatherGlyph.FOG, glyph("Fog"))
        assertEquals(WeatherGlyph.FOG, glyph("Haze"))
        assertEquals(WeatherGlyph.FOG, glyph("Mist"))
    }

    @Test
    fun `rain and drizzle are told apart`() {
        assertEquals(WeatherGlyph.RAIN, glyph("Light rain"))
        assertEquals(WeatherGlyph.RAIN, glyph("Rain showers"))
        assertEquals(WeatherGlyph.DRIZZLE, glyph("Drizzle"))
    }

    @Test
    fun `snow beats rain, because sleet is both`() {
        assertEquals(WeatherGlyph.SNOW, glyph("Light snow"))
        assertEquals(WeatherGlyph.SNOW, glyph("Sleet"))
        assertEquals(WeatherGlyph.SNOW, glyph("Freezing rain"))
        assertEquals(WeatherGlyph.SNOW, glyph("Blizzard"))
    }

    @Test
    fun `a storm beats the rain inside it`() {
        assertEquals(WeatherGlyph.THUNDERSTORM, glyph("Thunderstorm"))
        assertEquals(WeatherGlyph.THUNDERSTORM, glyph("Thundery showers possible"))
    }

    /**
     * The case that decided the order of the whole `when`: "Thunderstorm" contains "storm",
     * "rain" and "shower" all at once, so whichever is asked first is the one that wins.
     */
    @Test
    fun `the most specific word wins over the general ones`() {
        assertEquals(WeatherGlyph.THUNDERSTORM, glyph("Thunderstorm with light rain"))
        assertEquals(WeatherGlyph.SNOW, glyph("Rain and snow"))
    }

    /**
     * A sender naming two conditions at once gets the first word, not the first clause.
     * Recorded so the shortcut is a decision on record rather than a surprise later.
     */
    @Test
    fun `a compound condition is drawn as whichever word is found first`() {
        assertEquals(WeatherGlyph.RAIN, glyph("Partly cloudy, light rain later"))
    }

    @Test
    fun `wording is matched whatever its case`() {
        assertEquals(WeatherGlyph.THUNDERSTORM, glyph("THUNDERSTORM"))
        assertEquals(WeatherGlyph.SNOW, glyph("Light Snow"))
        assertEquals(WeatherGlyph.CLEAR, glyph("cLeAr"))
    }

    /** Anything unrecognised draws nothing, because a wrong sky is worse than no sky. */
    @Test
    fun `unknown wording is unknown rather than a guess`() {
        assertEquals(WeatherGlyph.UNKNOWN, glyph("Aurora expected tonight"))
        assertEquals(WeatherGlyph.UNKNOWN, glyph(""))
        assertEquals(WeatherGlyph.UNKNOWN, glyph("   "))
    }

    @Test
    fun `degrees are rounded and labelled`() {
        val warm = ClockWidgetWeather.Reading(tempK = 294, condition = "Clear") // ~21C
        assertEquals("21°C", ClockWidgetWeather.degrees(warm, fahrenheit = false))
    }

    @Test
    fun `fahrenheit converts rather than relabels`() {
        val freezing = ClockWidgetWeather.Reading(tempK = 273, condition = "") // 0C
        assertEquals("32°F", ClockWidgetWeather.degrees(freezing, fahrenheit = true))
    }

    /** Words still come back for anywhere with room for them, even though the widget uses a glyph. */
    @Test
    fun `formatted keeps the condition alongside the degrees`() {
        val reading = ClockWidgetWeather.Reading(tempK = 294, condition = "Partly cloudy")
        assertEquals("21°C  Partly cloudy", ClockWidgetWeather.formatted(reading, fahrenheit = false))
    }

    @Test
    fun `formatted with no condition is just the degrees`() {
        val reading = ClockWidgetWeather.Reading(tempK = 294, condition = "")
        assertEquals("21°C", ClockWidgetWeather.formatted(reading, fahrenheit = false))
    }
}