// SPDX-License-Identifier: GPL-3.0-or-later
package dev.alalauncher.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The forecast comes from another app's JSON, so this is where it has to be trusted.
 *
 * Every case here is a sender doing something reasonable-but-unexpected rather than an
 * attacker: a missing temperature, a pair the wrong way round, more days than there are
 * slots to draw them in. None of those may crash the receiver, and none may produce a row
 * saying something confident and wrong.
 */
class WeatherForecastTest {

    @Test
    fun `reads the days a sender sent`() {
        val days = ClockWidgetWeather.parseForecast(
            """
            [
              { "dayCode": 1, "minTemp": 278, "maxTemp": 292 },
              { "dayCode": 2, "minTemp": 275, "maxTemp": 285 }
            ]
            """.trimIndent(),
        )
        assertEquals(2, days.size)
        assertEquals(278, days[0].minTempK)
        assertEquals(292, days[0].maxTempK)
        assertEquals("1", days[0].condition)
    }

    @Test
    fun `a day without both temperatures is dropped rather than drawn`() {
        val days = ClockWidgetWeather.parseForecast(
            """
            [
              { "dayCode": 1, "minTemp": 278 },
              { "dayCode": 2, "minTemp": 275, "maxTemp": 285 }
            ]
            """.trimIndent(),
        )
        // The good day survives, and the days after it keep their place rather than closing up.
        assertEquals(1, days.size)
        assertEquals("2", days[0].condition)
    }

    @Test
    fun `a temperature nothing could survive is rejected`() {
        // 0K is what a sender that left the field out looks like once defaulted.
        val days = ClockWidgetWeather.parseForecast(
            """[{ "dayCode": 1, "minTemp": 0, "maxTemp": 0 }]""",
        )
        assertTrue(days.isEmpty())
    }

    @Test
    fun `a pair the wrong way round is put the right way`() {
        val days = ClockWidgetWidgetSwap().parseSwapped()
        assertEquals(275, days.minTempK)
        assertEquals(292, days.maxTempK)
    }

    @Test
    fun `more days than there are slots keeps the first of them`() {
        val json = (1..8).joinToString(",") { """{ "dayCode": $it, "minTemp": 275, "maxTemp": 285 }""" }
        val days = ClockWidgetWeather.parseForecast("[$json]")
        assertEquals(ClockWidgetWeather.MAX_FORECAST_DAYS, days.size)
        assertEquals("1", days.first().condition)
    }

    @Test
    fun `nothing readable reads as no forecast`() {
        assertTrue(ClockWidgetWeather.parseForecast(null).isEmpty())
        assertTrue(ClockWidgetWeather.parseForecast("").isEmpty())
        assertTrue(ClockWidgetWeather.parseForecast("not json at all").isEmpty())
        assertTrue(ClockWidgetWeather.parseForecast("{}").isEmpty())
    }

    @Test
    fun `the day's condition is found under any of the names a sender uses`() {
        // Gadgetbridge's own name, Breezy's, and two more seen in the wild. Reading only the
        // documented one produced five days of numbers and no sky on any of them, which is a
        // widget that looks like it is working while saying nothing.
        for (key in listOf("dayCode", "conditionCode", "condition", "day_condition")) {
            val days = ClockWidgetWeather.parseForecast(
                """[{ "$key": 4, "minTemp": 278, "maxTemp": 292 }]""",
            )
            assertEquals("no day read from key \"$key\"", 1, days.size)
            assertEquals("wrong code from key \"$key\"", "4", days[0].condition)
        }
    }

    @Test
    fun `a day whose condition is missing entirely still counts as a day`() {
        // The temperatures are real, so the row is worth having; what is missing is the sky.
        val days = ClockWidgetWeather.parseForecast(
            """[{ "minTemp": 278, "maxTemp": 292 }]""",
        )
        assertEquals(1, days.size)
        assertEquals("", days[0].condition)
    }

    @Test
    fun `a day code becomes words, which is what the row is drawn from`() {
        // The forecast carries a number where today's condition carries text, so the day
        // cannot go through the same substring matching as today does.
        assertEquals("Clear", ClockWidgetWeather.conditionWordsForDayCode("0"))
        assertEquals("Partly cloudy", ClockWidgetWeather.conditionWordsForDayCode("2"))
        assertEquals("Rainy", ClockWidgetWeather.conditionWordsForDayCode("4"))
        assertEquals("Heavy snow", ClockWidgetWeather.conditionWordsForDayCode("10"))
        assertEquals("Breezy", ClockWidgetWeather.conditionWordsForDayCode("29"))
        assertEquals("Fog", ClockWidgetWeather.conditionWordsForDayCode("47"))
    }

    @Test
    fun `a day code we do not know reads as no condition rather than a wrong one`() {
        assertEquals("", ClockWidgetWeather.conditionWordsForDayCode("999"))
        assertEquals("", ClockWidgetWeather.conditionWordsForDayCode(""))
        assertEquals("", ClockWidgetWeather.conditionWordsForDayCode("sunny"))
    }

    @Test
    fun `every named day code is a sky this can draw`() {
        // A word that resolves to no glyph would leave the row with a temperature and
        // nothing else, which is the failure this whole mapping exists to prevent.
        for (code in 0..52) {
            val words = ClockWidgetWeather.conditionWordsForDayCode(code.toString())
            if (words.isBlank()) continue
            val glyph = ClockWidgetWeather.glyphFor(words)
            assertTrue(
                "\"$words\" (code $code) has no glyph",
                glyph != ClockWidgetWeather.WeatherGlyph.UNKNOWN,
            )
        }
    }

    @Test
    fun `both units come out of the same conversion`() {
        val celsius = ClockWidgetWeather.degreesFromKelvin(273, fahrenheit = false)
        val fahrenheit = ClockWidgetWeather.degreesFromKelvin(273, fahrenheit = true)
        assertTrue("expected a Celsius reading, was $celsius", celsius.endsWith("C"))
        assertTrue("expected a Fahrenheit reading, was $fahrenheit", fahrenheit.endsWith("F"))
    }

    /** Small shim so the swap case reads as a sender's bug rather than as our arithmetic. */
    private class ClockWidgetWidgetSwap {
        fun parseSwapped(): ClockWidgetWeather.ForecastDay {
            val day = ClockWidgetWeather.parseForecast(
                """[{ "dayCode": 3, "minTemp": 292, "maxTemp": 275 }]""",
            ).single()
            return day
        }
    }
}
